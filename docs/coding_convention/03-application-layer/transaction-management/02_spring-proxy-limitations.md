# Spring Proxy Limitations

**Issue**: [#27](https://github.com/ryu-qqq/claude-spring-standards/issues/27)
**Priority**: 🔴 CRITICAL
**Validation**: `hooks/validators/transaction-proxy-validator.sh`

---

## 📋 핵심 원칙

Spring은 **AOP 프록시**를 통해 `@Transactional`을 구현합니다.
프록시가 작동하지 않는 상황을 이해하고 회피해야 합니다.

---

## 🏗️ Spring Proxy 종류

### 1. JDK Dynamic Proxy
- **대상**: 인터페이스를 구현한 클래스
- **방식**: 인터페이스 기반 프록시 생성
- **제약**: 인터페이스에 정의된 메서드만 프록시 가능

### 2. CGLIB Proxy (기본값)
- **대상**: 구체 클래스
- **방식**: 서브클래스 생성 (상속 기반)
- **제약**: Final 클래스/메서드 프록시 불가

---

## 🚨 프록시가 작동하지 않는 4가지 경우

### 1. Private 메서드 ❌
CGLIB는 서브클래스를 생성하므로 Private 메서드 접근 불가

### 2. Final 메서드 ❌
서브클래스에서 오버라이드 불가

### 3. Final 클래스 ❌
상속 자체가 불가능

### 4. 내부 메서드 호출 (`this.method()`) ❌
프록시를 우회하여 직접 호출됨

---

## ❌ 시나리오 1: Private 메서드에 @Transactional

```java
@Service
public class OrderService {

    public void processOrder(OrderCommand cmd) {
        // ❌ 이 호출은 프록시를 거치지 않음
        this.saveOrder(cmd);  // @Transactional 무시됨!
    }

    @Transactional  // ❌ Private 메서드는 프록시 불가
    private void saveOrder(OrderCommand cmd) {
        // 트랜잭션이 적용되지 않음!
        // 실제로는 Auto-commit 모드로 실행됨
        orderRepository.save(Order.create(cmd));
    }
}
```

### 문제점
- Private 메서드는 서브클래스에서 접근 불가
- CGLIB가 메서드를 오버라이드할 수 없음
- `@Transactional` 어노테이션이 완전히 무시됨

### 결과
- 트랜잭션 없이 실행됨 (Auto-commit 모드)
- 예외 발생 시 롤백 안 됨
- 데이터 일관성 문제 발생 가능

---

## ❌ 시나리오 2: 내부 메서드 호출 (가장 흔한 실수)

```java
@Service
public class OrderService {

    @Transactional
    public void processOrder(OrderCommand cmd) {
        try {
            Order order = Order.create(cmd);
            orderRepository.save(order);
        } catch (Exception e) {
            // ❌ 내부 호출 - 프록시 우회!
            this.handleFailure(cmd.getId(), e.getMessage());
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void handleFailure(Long orderId, String reason) {
        // ❌ REQUIRES_NEW가 작동하지 않음
        // 새 트랜잭션이 생성되지 않고 상위 트랜잭션 사용
        FailureLog log = FailureLog.create(orderId, reason);
        failureLogRepository.save(log);
    }
}
```

### 문제점
- `this.handleFailure()` 호출은 프록시를 거치지 않음
- Spring AOP Interceptor가 실행되지 않음
- `REQUIRES_NEW` 전파 속성이 무시됨

### 결과
- 새 트랜잭션 생성 안 됨
- 상위 트랜잭션 롤백 시 실패 로그도 함께 롤백됨
- 실패 로그가 저장되지 않음 (의도와 다름!)

---

## ❌ 시나리오 3: Final 클래스/메서드

```java
// ❌ Final 클래스 - CGLIB 프록시 불가
@Service
public final class OrderService {  // ❌ final 제거 필요

    @Transactional
    public void processOrder(OrderCommand cmd) {
        // 트랜잭션이 작동하지 않음!
        orderRepository.save(Order.create(cmd));
    }
}
```

```java
// ❌ Final 메서드 - 오버라이드 불가
@Service
public class OrderService {

    @Transactional
    public final void processOrder(OrderCommand cmd) {  // ❌ final 제거 필요
        // 트랜잭션이 작동하지 않음!
        orderRepository.save(Order.create(cmd));
    }
}
```

### 문제점
- Final 클래스는 상속 불가
- Final 메서드는 오버라이드 불가
- CGLIB가 프록시를 생성할 수 없음

### 결과
- `@Transactional` 완전히 무시됨
- 런타임 경고도 없음 (Silent Failure)
- 디버깅 매우 어려움

---

## ✅ 해결책: 별도 빈으로 분리

### 올바른 패턴

```java
/**
 * 조정자 역할 (Coordinator)
 * - 트랜잭션 없음
 * - 외부 API 호출 가능
 * - 여러 트랜잭션 조율
 */
@Service
public class OrderService {
    private final OrderPersistenceService persistenceService;
    private final OrderFailureService failureService;

    public OrderService(
        OrderPersistenceService persistenceService,
        OrderFailureService failureService
    ) {
        this.persistenceService = persistenceService;
        this.failureService = failureService;
    }

    // ✅ @Transactional 없음 - 외부 API 호출 가능
    public OrderResult processOrder(OrderCommand cmd) {
        try {
            // ✅ 별도 빈 호출 - 프록시 정상 작동
            Order order = persistenceService.saveOrder(cmd);
            return OrderResult.from(order);
        } catch (Exception e) {
            // ✅ 별도 빈 호출 - REQUIRES_NEW 정상 작동
            //    - 상위 트랜잭션 롤백되어도 실패 로그는 커밋됨
            failureService.logFailure(cmd.getId(), e.getMessage());
            throw new OrderProcessingException("Order processing failed", e);
        }
    }
}

/**
 * DB 작업 전담 Service
 */
@Service
public class OrderPersistenceService {

    private final SaveOrderPort saveOrderPort;

    // ✅ Public 메서드 + 별도 빈 = 프록시 작동
    @Transactional
    public Order saveOrder(OrderCommand cmd) {
        Order order = Order.create(cmd.userId(), cmd.items());
        return saveOrderPort.save(order);
    }
}

/**
 * 실패 로그 전담 Service
 */
@Service
public class OrderFailureService {

    private final SaveFailureLogPort saveFailureLogPort;

    // ✅ REQUIRES_NEW가 정상 작동
    //    - 새 트랜잭션 생성
    //    - 상위 트랜잭션과 독립적
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(Long orderId, String reason) {
        FailureLog log = FailureLog.create(orderId, reason, LocalDateTime.now());
        saveFailureLogPort.save(log);
    }
}
```

### 설계 원칙
1. **조정자 Service**: `@Transactional` 없음, 여러 트랜잭션 조율
2. **작업 Service**: `@Transactional` 있음, 단일 책임
3. **별도 빈**: 각 Service는 독립적인 Spring Bean

---

## 🔍 프록시 작동 원리 이해

### ✅ 외부 호출 (프록시 통과)

```
Client
  ↓
orderService.processOrder(cmd)  // Spring Bean 주입 받은 프록시
  ↓
[Spring Proxy Interceptor]
  ↓
@Transactional Begin
  ↓
OrderService.processOrder()  // 실제 메서드 실행
  ↓
@Transactional Commit/Rollback
  ↓
Result
```

**동작**:
- Spring이 주입한 프록시 객체 호출
- AOP Interceptor가 `@Transactional` 처리
- 트랜잭션 시작/커밋/롤백 자동 실행

---

### ❌ 내부 호출 (프록시 우회)

```
OrderService.processOrder()
  ↓
this.saveOrder()  // 직접 호출 (프록시 없음)
  ↓
OrderService.saveOrder()  // @Transactional 무시됨!
  ↓
orderRepository.save()
```

**문제**:
- `this`는 프록시가 아닌 실제 객체
- AOP Interceptor 실행 안 됨
- `@Transactional` 완전히 무시됨

---

## 📊 REQUIRES_NEW 전파 속성 비교

### ❌ Bad - 내부 호출 (작동 안 함)

```java
@Service
public class OrderService {

    @Transactional
    public void processOrder(OrderCommand cmd) {
        orderRepository.save(order);  // 트랜잭션 T1

        // ❌ 내부 호출 - REQUIRES_NEW 무시
        this.logAudit(order.getId());  // 여전히 트랜잭션 T1 사용
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void logAudit(Long orderId) {
        auditRepository.save(audit);  // T1 사용 (새 트랜잭션 생성 안 됨)
    }
}
```

**결과**:
- 주문 저장 실패 → 롤백
- 감사 로그도 함께 롤백됨 (의도와 다름!)

---

### ✅ Good - 별도 빈 호출 (정상 작동)

```java
@Service
public class OrderService {
    private final OrderPersistenceService persistenceService;
    private final AuditService auditService;

    public void processOrder(OrderCommand cmd) {
        try {
            // 트랜잭션 T1
            Order order = persistenceService.saveOrder(cmd);

            // ✅ 별도 빈 호출 - 새 트랜잭션 T2 생성
            auditService.logAudit(order.getId());
        } catch (Exception e) {
            // 주문 저장 실패 → T1 롤백
            // 감사 로그는 T2에서 이미 커밋됨 (유지!)
            throw e;
        }
    }
}

@Service
public class OrderPersistenceService {
    @Transactional  // T1
    public Order saveOrder(OrderCommand cmd) {
        return orderRepository.save(Order.create(cmd));
    }
}

@Service
public class AuditService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)  // T2
    public void logAudit(Long orderId) {
        auditRepository.save(Audit.create(orderId));
    }
}
```

**결과**:
- 주문 저장 실패 → T1 롤백
- 감사 로그는 T2에서 이미 커밋됨 (유지됨!)

---

## ✅ 체크리스트

코드 작성 전:
- [ ] `@Transactional`은 **public** 메서드에만 사용
- [ ] **Private** 메서드에 `@Transactional` 사용 금지
- [ ] **Final** 클래스/메서드에 `@Transactional` 사용 금지
- [ ] 같은 클래스 내부에서 `@Transactional` 메서드 호출 금지
- [ ] 다른 트랜잭션 전파 속성 필요 시 **별도 빈**으로 분리
- [ ] 보상 트랜잭션은 **별도 Service 클래스**로 구현

커밋 전:
- [ ] Pre-commit Hook 통과 (`transaction-proxy-validator.sh`)
- [ ] ArchUnit 테스트 통과 (`TransactionArchitectureTest.java`)

---

## 🔧 검증 방법

### Git Pre-commit Hook
```bash
./hooks/validators/transaction-proxy-validator.sh
```

**검증 항목**:
- Private 메서드에 `@Transactional` 사용 감지
- Final 클래스/메서드에 `@Transactional` 사용 감지
- 내부 메서드 호출 패턴 감지 (휴리스틱)

### ArchUnit 테스트
```java
// application/src/test/java/architecture/TransactionArchitectureTest.java
@ArchTest
static final ArchRule transactional_methods_should_be_public =
    methods()
        .that().areAnnotatedWith(Transactional.class)
        .should().bePublic()
        .because("@Transactional only works on public methods due to CGLIB proxy limitations");

@ArchTest
static final ArchRule transactional_classes_should_not_be_final =
    classes()
        .that().haveMethodAnnotatedWith(Transactional.class)
        .should().notBeFinal()
        .because("@Transactional requires CGLIB proxy which cannot extend final classes");
```

---

## 🎓 실전 가이드라인

### 패턴 1: 조정자 + 작업자 분리
```java
// 조정자: @Transactional 없음
@Service
public class OrderCoordinator {
    public void process(OrderCommand cmd) {
        // 여러 트랜잭션 조율
    }
}

// 작업자: @Transactional 있음
@Service
public class OrderPersistenceService {
    @Transactional
    public Order save(OrderCommand cmd) {
        // 단일 트랜잭션 작업
    }
}
```

### 패턴 2: Service 계층 분리
```java
// Web → Service (조정) → Service (트랜잭션)
@RestController
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/orders")
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.processOrder(toCommand(request));
    }
}

@Service
public class OrderService {
    private final OrderPersistenceService persistenceService;

    public OrderResponse processOrder(OrderCommand cmd) {
        // 조정 로직
        Order order = persistenceService.saveOrder(cmd);
        return OrderResponse.from(order);
    }
}

@Service
public class OrderPersistenceService {
    @Transactional
    public Order saveOrder(OrderCommand cmd) {
        // 트랜잭션 작업
    }
}
```

---

## 📚 관련 가이드

**전제 조건**:
- [Transaction Boundaries](./01_transaction-boundaries.md) - 트랜잭션 경계 이해

**연관 패턴**:
- [Transaction Best Practices](./03_transaction-best-practices.md) - 트랜잭션 베스트 프랙티스
- [Port Responsibility](../port-responsibility/) - Port 설계 원칙

**심화 학습**:
- [Spring AOP 공식 문서](https://docs.spring.io/spring-framework/reference/core/aop.html)
- [CGLIB Proxy 동작 원리](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

---

**Issue**: [#27](https://github.com/ryu-qqq/claude-spring-standards/issues/27)
**작성일**: 2025-10-16
**검증 도구**: `hooks/validators/transaction-proxy-validator.sh`, `TransactionArchitectureTest.java`
