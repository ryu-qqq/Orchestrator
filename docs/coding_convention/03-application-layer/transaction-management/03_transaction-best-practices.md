# Transaction Best Practices

**Priority**: 🟡 IMPORTANT
**Validation**: ArchUnit `TransactionArchitectureTest.java`

---

## 📋 핵심 원칙

1. **Aggregate 단위 트랜잭션**: 하나의 트랜잭션에서 하나의 Aggregate만 수정
2. **트랜잭션 짧게 유지**: DB 작업만 포함, 외부 호출 제외
3. **명시적 경계 설정**: Application Layer에만 `@Transactional` 배치
4. **Read-Only 최적화**: 조회 작업은 `readOnly = true`

---

## 1️⃣ Aggregate 단위 트랜잭션

### 원칙: 1 Transaction = 1 Aggregate

DDD의 핵심 원칙 중 하나는 **트랜잭션 경계 = Aggregate 경계**입니다.

### ❌ Bad - 여러 Aggregate 동시 수정

```java
@UseCase
@Transactional
public class CreateOrderService {

    @Override
    public OrderResult execute(CreateOrderCommand command) {
        // ❌ 여러 Aggregate를 하나의 트랜잭션에서 수정

        // Order Aggregate 수정
        Order order = Order.create(command.userId(), command.items());
        orderRepository.save(order);

        // ❌ Payment Aggregate도 동시 수정
        Payment payment = Payment.create(order.getId(), order.getTotal());
        paymentRepository.save(payment);

        // ❌ User Aggregate도 동시 수정
        User user = userRepository.findById(command.userId());
        user.incrementOrderCount();
        userRepository.save(user);

        return OrderResult.from(order);
    }
}
```

### 문제점
- **강한 결합**: Order, Payment, User가 하나의 트랜잭션에 묶임
- **동시성 문제**: 여러 Aggregate Lock으로 성능 저하
- **확장성 저해**: 분산 시스템으로 전환 어려움
- **장애 전파**: 한 Aggregate 실패 시 전체 실패

---

### ✅ Good - Domain Event로 분리

```java
/**
 * Order Aggregate만 수정
 */
@UseCase
@Transactional
public class CreateOrderService {
    private final SaveOrderPort saveOrderPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public CreateOrderResult execute(CreateOrderCommand command) {
        // ✅ Order Aggregate만 수정
        Order order = Order.create(command.userId(), command.items());
        Order savedOrder = saveOrderPort.save(order);

        // ✅ Domain Event 발행 (트랜잭션 커밋 후 비동기 처리)
        eventPublisher.publishEvent(new OrderCreatedEvent(
            savedOrder.getId(),
            savedOrder.getUserId(),
            savedOrder.getTotal()
        ));

        return CreateOrderResult.from(savedOrder);
    }
}

/**
 * Event Handler: 다른 Aggregate 수정
 */
@Component
public class OrderEventHandler {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final UpdateUserStatsUseCase updateUserStatsUseCase;

    @EventListener
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        // ✅ Payment Aggregate 수정 (별도 트랜잭션)
        createPaymentUseCase.execute(new CreatePaymentCommand(
            event.getOrderId(),
            event.getTotalAmount()
        ));

        // ✅ User Aggregate 수정 (별도 트랜잭션)
        updateUserStatsUseCase.execute(new UpdateUserStatsCommand(
            event.getUserId(),
            StatType.ORDER_COUNT
        ));
    }
}
```

### 장점
- **느슨한 결합**: 각 Aggregate 독립적 수정
- **동시성 향상**: Lock 최소화
- **확장성**: 마이크로서비스로 쉽게 전환
- **장애 격리**: 한 Aggregate 실패가 다른 Aggregate에 영향 없음

---

## 2️⃣ Read-Only 트랜잭션 최적화

### 원칙: 조회 작업은 `readOnly = true`

읽기 전용 트랜잭션은 다음과 같은 최적화를 제공합니다:
- **Dirty Checking 비활성화**: 변경 감지 로직 Skip
- **Flush 모드 변경**: `FlushMode.MANUAL`로 설정
- **DB 최적화**: 읽기 전용 Connection 사용 (DB에 따라)

### ✅ Good - Read-Only 명시

```java
/**
 * 조회 전용 UseCase
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrderService implements GetOrderUseCase {
    private final LoadOrderPort loadOrderPort;
    private final LoadOrderItemsPort loadOrderItemsPort;

    @Override
    public GetOrderResult execute(GetOrderQuery query) {
        // ✅ readOnly = true
        //    - Dirty Checking 비활성화
        //    - Flush 모드 MANUAL
        //    - 성능 향상

        Order order = loadOrderPort.loadById(query.orderId())
            .orElseThrow(() -> new OrderNotFoundException(query.orderId()));

        List<OrderItem> items = loadOrderItemsPort.loadByOrderId(query.orderId());

        return GetOrderResult.of(order, items);
    }
}
```

### 성능 개선
- **Dirty Checking 비용 제거**: Entity 변경 감지 로직 Skip
- **메모리 절약**: Snapshot 저장 불필요
- **DB 최적화**: 읽기 전용 Connection Pool 사용 (MySQL 등)

---

## 3️⃣ 트랜잭션 타임아웃 설정

### 원칙: 장시간 트랜잭션 방지

기본 타임아웃은 무제한이므로, 명시적으로 설정해야 합니다.

### ✅ Good - 타임아웃 설정

```java
@UseCase
@Transactional(timeout = 5)  // ✅ 5초 타임아웃
public class CreateOrderService implements CreateOrderUseCase {

    @Override
    public CreateOrderResult execute(CreateOrderCommand command) {
        // 5초 이내 완료되지 않으면 TransactionTimedOutException 발생
        Order order = Order.create(command.userId(), command.items());
        return CreateOrderResult.from(saveOrderPort.save(order));
    }
}
```

### 권장 타임아웃
- **단순 CRUD**: 5-10초
- **복잡한 비즈니스 로직**: 10-30초
- **Batch 작업**: 별도 설정 (분 단위)

### 주의 사항
- 외부 API 호출 포함 시 타임아웃 불필요
- 외부 API는 트랜잭션 밖에서 처리

---

## 4️⃣ 전파 속성 (Propagation) 활용

### REQUIRED (기본값)
- 기존 트랜잭션 있으면 참여
- 없으면 새로 생성

```java
@Transactional(propagation = Propagation.REQUIRED)
public void execute(Command cmd) {
    // 기존 트랜잭션 참여 or 새 트랜잭션 생성
}
```

### REQUIRES_NEW
- 항상 새 트랜잭션 생성
- 기존 트랜잭션은 일시 중단

```java
@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAudit(Long orderId, String action) {
        // ✅ 새 트랜잭션 생성
        //    - 상위 트랜잭션 롤백되어도 커밋됨
        //    - 감사 로그는 항상 저장됨
        auditRepository.save(Audit.create(orderId, action));
    }
}
```

### MANDATORY
- 기존 트랜잭션 필수
- 없으면 예외 발생

```java
@Transactional(propagation = Propagation.MANDATORY)
public void execute(Command cmd) {
    // ✅ 기존 트랜잭션 필수
    //    - 없으면 IllegalTransactionStateException 발생
    //    - 트랜잭션 경계 명확화
}
```

---

## 5️⃣ 롤백 규칙 (Rollback Rules)

### 기본 동작
- **Unchecked Exception** (RuntimeException): 롤백
- **Checked Exception**: 커밋 (롤백 안 됨!)

### ✅ Good - Checked Exception도 롤백

```java
@UseCase
@Transactional(rollbackFor = Exception.class)  // ✅ 모든 예외 롤백
public class CreateOrderService implements CreateOrderUseCase {

    @Override
    public CreateOrderResult execute(CreateOrderCommand command) throws OrderValidationException {
        // ✅ Checked Exception도 롤백됨
        validateOrderItems(command.items());  // throws OrderValidationException

        Order order = Order.create(command.userId(), command.items());
        return CreateOrderResult.from(saveOrderPort.save(order));
    }
}
```

### 특정 예외만 롤백 안 함

```java
@Transactional(noRollbackFor = NotificationException.class)
public void execute(Command cmd) {
    // NotificationException 발생 시 롤백 안 함
    // 다른 예외는 롤백
}
```

---

## 6️⃣ 트랜잭션 격리 수준 (Isolation Level)

### DEFAULT (권장)
- DB 기본 격리 수준 사용
- MySQL: REPEATABLE_READ
- PostgreSQL: READ_COMMITTED

```java
@Transactional(isolation = Isolation.DEFAULT)
public void execute(Command cmd) {
    // DB 기본 격리 수준 사용
}
```

### READ_COMMITTED
- Dirty Read 방지
- Non-Repeatable Read 허용

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void execute(Command cmd) {
    // 커밋된 데이터만 읽기
    // 성능 우선 시 사용
}
```

### REPEATABLE_READ (MySQL 기본)
- Non-Repeatable Read 방지
- Phantom Read 발생 가능 (MySQL InnoDB는 방지)

### SERIALIZABLE (가장 엄격)
- 모든 동시성 문제 방지
- 성능 저하 심각
- 거의 사용 안 함

---

## 7️⃣ 트랜잭션 배치 위치

### ✅ Good - Application Layer에만

```java
// ✅ Application Layer
@UseCase
@Transactional
public class CreateOrderService {
    // 트랜잭션 시작점
}

// ✅ Domain Layer
public class Order {
    // @Transactional 없음
    public Order confirm() {
        // 순수 비즈니스 로직
    }
}

// ✅ Adapter Layer (Persistence)
@Component
public class OrderPersistenceAdapter {
    // @Transactional 없음
    // Application Layer에서 관리
}
```

### ❌ Bad - 여러 레이어에 분산

```java
// ❌ Controller에 @Transactional
@RestController
public class OrderController {
    @Transactional  // ❌ Controller에 트랜잭션 금지
    @PostMapping("/orders")
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        // ...
    }
}

// ❌ Repository Adapter에 @Transactional
@Component
public class OrderPersistenceAdapter {
    @Transactional  // ❌ Adapter에 트랜잭션 금지
    public Order save(Order order) {
        // ...
    }
}
```

---

## 8️⃣ 성능 최적화 패턴

### 패턴 1: Batch Size 설정

```java
@Configuration
public class JpaConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return properties -> {
            // ✅ Batch Insert/Update 최적화
            properties.put("hibernate.jdbc.batch_size", 50);
            properties.put("hibernate.order_inserts", true);
            properties.put("hibernate.order_updates", true);
            properties.put("hibernate.batch_versioned_data", true);
        };
    }
}
```

### 패턴 2: Flush 모드 조절

```java
@Transactional
public void bulkInsert(List<Order> orders) {
    int batchSize = 50;
    for (int i = 0; i < orders.size(); i++) {
        orderRepository.save(orders.get(i));

        if (i % batchSize == 0 && i > 0) {
            // ✅ 주기적 Flush & Clear
            entityManager.flush();
            entityManager.clear();
        }
    }
}
```

---

## ✅ 체크리스트

코드 작성 전:
- [ ] 하나의 트랜잭션에서 하나의 Aggregate만 수정
- [ ] 조회 작업은 `readOnly = true` 사용
- [ ] 트랜잭션 타임아웃 명시적 설정 (5-30초)
- [ ] Checked Exception도 롤백하도록 `rollbackFor` 설정
- [ ] 트랜잭션은 Application Layer에만 배치

커밋 전:
- [ ] ArchUnit 테스트 통과 (`TransactionArchitectureTest.java`)
- [ ] 트랜잭션 시간 측정 (100ms 이내 권장)

---

## 📚 관련 가이드

**전제 조건**:
- [Transaction Boundaries](./01_transaction-boundaries.md) - 외부 API 분리
- [Spring Proxy Limitations](./02_spring-proxy-limitations.md) - 프록시 한계 이해

**연관 패턴**:
- [Async Processing](../../08-enterprise-patterns/async-processing/) - 비동기 처리
- [Domain Events](../../02-domain-layer/domain-events/) - 이벤트 기반 아키텍처

---

**작성일**: 2025-10-16
**검증 도구**: ArchUnit `TransactionArchitectureTest.java`
