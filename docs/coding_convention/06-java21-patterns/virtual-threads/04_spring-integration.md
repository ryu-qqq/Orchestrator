# Spring Integration with Virtual Threads - @Transactional & @Async 통합

**목적**: Virtual Threads와 Spring Framework (@Transactional, @Async) 통합 패턴 및 주의사항

**관련 문서**:
- [Virtual Threads Basics](./01_virtual-threads-basics.md)
- [Async Processing](./02_async-processing.md)
- [Command UseCase](../../../03-application-layer/usecase-design/01_command-usecase.md)
- [Domain Events](../../../07-enterprise-patterns/event-driven/01_domain-events.md)

**필수 버전**: Spring Boot 3.2+, Java 21+

---

## 📌 핵심 원칙

### Spring + Virtual Threads 통합

1. **@Transactional**: Virtual Thread에서도 정상 작동
2. **@Async**: Virtual Thread Pool 자동 사용 (Spring Boot 3.2+)
3. **ThreadLocal**: 주의 필요 (Virtual Thread는 재사용됨)
4. **Pinning 방지**: `synchronized` 대신 `ReentrantLock`

---

## ⚙️ Spring Boot Virtual Threads 활성화

### 설정 1: application.yml

```yaml
# ✅ Virtual Threads 활성화
spring:
  threads:
    virtual:
      enabled: true  # Spring Boot 3.2+

# ✅ Async Executor 설정
  task:
    execution:
      pool:
        core-size: 10
        max-size: 50
        queue-capacity: 100
      thread-name-prefix: async-vt-
    scheduling:
      pool:
        size: 5
      thread-name-prefix: scheduling-vt-
```

### 설정 2: AsyncConfigurer

```java
package com.company.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async Configuration with Virtual Threads
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * ✅ Virtual Thread Executor
     *
     * - Spring Boot 3.2+에서는 자동으로 Virtual Thread 사용
     * - 명시적으로 설정하려면 여기서 재정의
     */
    @Override
    public Executor getAsyncExecutor() {
        // ✅ Virtual Thread Executor 생성
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

---

## ✅ @Transactional with Virtual Threads

### 패턴 1: UseCase Service with @Transactional

```java
package com.company.application.order.service.command;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.application.order.port.out.SaveOrderPort;
import com.company.application.order.assembler.OrderAssembler;
import com.company.domain.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create Order Service
 *
 * - @Transactional이 Virtual Thread에서도 정상 작동
 * - ThreadLocal 기반 트랜잭션 컨텍스트 유지
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
@Transactional  // ✅ Virtual Thread에서도 정상 작동
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderAssembler orderAssembler;
    private final SaveOrderPort saveOrderPort;

    public CreateOrderService(
        OrderAssembler orderAssembler,
        SaveOrderPort saveOrderPort
    ) {
        this.orderAssembler = orderAssembler;
        this.saveOrderPort = saveOrderPort;
    }

    /**
     * ✅ @Transactional 메서드
     *
     * - Virtual Thread에서 호출되어도 트랜잭션 정상 관리
     * - Spring AOP Proxy가 트랜잭션 시작/커밋/롤백 처리
     */
    @Override
    public Response createOrder(Command command) {
        // ✅ 1. Assembler: Command → Domain
        Order order = orderAssembler.toDomain(command);

        // ✅ 2. Port: Domain 저장 (트랜잭션 내)
        Order savedOrder = saveOrderPort.save(order);

        // ✅ 3. Assembler: Domain → Response
        return orderAssembler.toResponse(savedOrder);
    }
}
```

**핵심 포인트**:
- ✅ `@Transactional`은 Virtual Thread에서도 정상 작동
- ✅ Spring AOP Proxy가 Virtual Thread와 무관하게 트랜잭션 관리
- ✅ ThreadLocal 기반 트랜잭션 컨텍스트는 Virtual Thread에도 유지됨

---

### 패턴 2: @Transactional 내부 호출 주의 (프록시 우회)

```java
// ❌ Before - 같은 클래스 내부 호출 (프록시 우회)
@Service
public class OrderService {

    /**
     * ❌ this.saveOrder() 호출 시 @Transactional 무시됨!
     *
     * - Spring AOP Proxy는 외부 호출에만 적용
     * - 같은 클래스 내부 메서드 호출은 프록시 우회
     */
    public void processOrder(CreateOrderCommand command) {
        // ... 비즈니스 로직 ...

        this.saveOrder(order);  // ❌ @Transactional 무시됨!
    }

    @Transactional
    private void saveOrder(Order order) {
        orderRepository.save(order);
    }
}

// ✅ After - 별도 빈으로 분리
@Service
public class OrderCommandService {

    private final OrderPersistenceService persistenceService;

    /**
     * ✅ 외부 빈 호출 → 프록시 정상 작동
     */
    public void processOrder(CreateOrderCommand command) {
        // ... 비즈니스 로직 ...

        persistenceService.saveOrder(order);  // ✅ @Transactional 적용됨
    }
}

@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;

    /**
     * ✅ @Transactional 정상 적용
     */
    @Transactional
    public void saveOrder(Order order) {
        orderRepository.save(order);
    }
}
```

**핵심 포인트**:
- ✅ `@Transactional`은 **외부 빈 호출**에만 적용
- ❌ 같은 클래스 내부 메서드 호출 (`this.method()`)은 프록시 우회
- ✅ 별도 빈으로 분리하여 프록시 정상 작동

---

### 패턴 3: @Transactional with Timeout (Virtual Thread)

```java
/**
 * ✅ @Transactional with Timeout
 *
 * - Virtual Thread에서도 timeout 설정 정상 작동
 * - 긴 트랜잭션 방지
 */
@Service
@Transactional(timeout = 5)  // ✅ 5초 제한
public class OrderProcessingService {

    /**
     * ✅ 긴 작업은 트랜잭션 외부로 분리
     */
    public void processOrderWithExternalApi(CreateOrderCommand command) {
        // ✅ 1. 트랜잭션 내: Order 저장 (짧게 유지)
        Order savedOrder = orderTransactionService.saveOrder(command);

        // ✅ 2. 트랜잭션 외: 외부 API 호출 (긴 작업)
        externalApiService.notifyOrderCreated(savedOrder.getId());
    }
}

@Service
public class OrderTransactionService {

    /**
     * ✅ 짧은 트랜잭션 (1-2초)
     */
    @Transactional(timeout = 2)
    public Order saveOrder(CreateOrderCommand command) {
        Order order = Order.create(command.customerId(), command.items());
        return orderRepository.save(order);
    }
}
```

---

## ✅ @Async with Virtual Threads

### 패턴 1: 기본 @Async

```java
package com.company.application.order.service.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Async Service with Virtual Threads
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OrderNotificationService {

    private final EmailService emailService;
    private final SmsService smsService;

    /**
     * ✅ @Async - Virtual Thread Pool에서 실행
     *
     * - Spring Boot 3.2+에서 자동으로 Virtual Thread 사용
     * - CompletableFuture 반환으로 비동기 결과 처리
     */
    @Async
    public CompletableFuture<Void> sendOrderConfirmation(Long orderId, Long customerId) {
        // ✅ 이메일 발송 (외부 API - 긴 작업)
        emailService.sendOrderConfirmation(orderId, customerId);

        // ✅ SMS 발송 (외부 API - 긴 작업)
        smsService.sendOrderConfirmation(orderId, customerId);

        return CompletableFuture.completedFuture(null);
    }

    /**
     * ✅ void 반환 (@Async - Fire and Forget)
     */
    @Async
    public void logOrderCreated(Long orderId) {
        // ✅ 로그 기록 (비동기 - 결과 무시)
        System.out.println("Order created: " + orderId);
    }
}
```

---

### 패턴 2: @Async with Exception Handling

```java
/**
 * ✅ @Async Exception Handling
 */
@Service
public class AsyncOrderProcessingService {

    /**
     * ✅ CompletableFuture.exceptionally()로 예외 처리
     */
    @Async
    public CompletableFuture<Order> processOrderAsync(CreateOrderCommand command) {
        try {
            Order order = Order.create(command.customerId(), command.items());
            orderRepository.save(order);

            // ✅ 외부 API 호출 (비동기)
            externalApiService.notifyOrderCreated(order.getId());

            return CompletableFuture.completedFuture(order);
        } catch (Exception e) {
            // ✅ 예외 발생 시 CompletableFuture로 전달
            return CompletableFuture.failedFuture(e);
        }
    }
}

/**
 * ✅ AsyncUncaughtExceptionHandler 설정
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            System.err.println("Async method failed: " + method.getName());
            ex.printStackTrace();
        };
    }
}
```

---

### 패턴 3: StructuredTaskScope with @Async (Java 21+)

```java
/**
 * ✅ StructuredTaskScope + @Async
 *
 * - Structured Concurrency로 여러 비동기 작업 조율
 */
@Service
public class OrderEnrichmentService {

    private final CustomerService customerService;
    private final ProductService productService;
    private final InventoryService inventoryService;

    /**
     * ✅ 여러 API 병렬 호출 (StructuredTaskScope)
     */
    @Async
    public CompletableFuture<EnrichedOrder> enrichOrder(Order order) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // ✅ 병렬 실행: Customer, Product, Inventory 조회
            Future<Customer> customerFuture = scope.fork(() ->
                customerService.getCustomer(order.getCustomerId())
            );

            Future<List<Product>> productsFuture = scope.fork(() ->
                productService.getProducts(order.getProductIds())
            );

            Future<List<Inventory>> inventoryFuture = scope.fork(() ->
                inventoryService.checkAvailability(order.getProductIds())
            );

            // ✅ 모든 작업 완료 대기
            scope.join();
            scope.throwIfFailed();

            // ✅ 결과 조합
            EnrichedOrder enriched = new EnrichedOrder(
                order,
                customerFuture.resultNow(),
                productsFuture.resultNow(),
                inventoryFuture.resultNow()
            );

            return CompletableFuture.completedFuture(enriched);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
```

---

## ✅ @TransactionalEventListener with Virtual Threads

### 패턴 1: Domain Event Handler (AFTER_COMMIT)

```java
package com.company.application.event;

import com.company.domain.order.event.OrderCreated;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Order Event Handler
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderEventHandler {

    private final InventoryService inventoryService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    /**
     * ✅ @TransactionalEventListener(AFTER_COMMIT) + @Async
     *
     * - 트랜잭션 커밋 후에만 실행
     * - Virtual Thread에서 비동기 실행
     * - 외부 API 호출은 트랜잭션 밖
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreated event) {
        // ✅ 1. 재고 차감 (별도 트랜잭션)
        inventoryService.decreaseStock(event.items());

        // ✅ 2. 이메일 발송 (외부 API - 트랜잭션 밖)
        emailService.sendOrderConfirmation(event.orderId(), event.customerId());

        // ✅ 3. 푸시 알림 (외부 API - 트랜잭션 밖)
        notificationService.notifyOrderCreated(event.orderId());
    }

    /**
     * ✅ 여러 Handler가 같은 Event 처리 가능
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logOrderCreated(OrderCreated event) {
        System.out.println("Order created: " + event.orderId());
    }
}
```

**핵심 포인트**:
- ✅ `TransactionPhase.AFTER_COMMIT`: Order 저장 트랜잭션 성공 후에만 실행
- ✅ `@Async`: 별도 Virtual Thread에서 비동기 실행
- ✅ 외부 API 호출은 트랜잭션 밖에서 (성능 향상)

---

### 패턴 2: BEFORE_COMMIT vs AFTER_COMMIT

```java
/**
 * ✅ TransactionPhase 비교
 */
@Component
public class OrderTransactionEventHandler {

    /**
     * ✅ BEFORE_COMMIT - 트랜잭션 내에서 실행
     *
     * - 트랜잭션이 아직 커밋되지 않음
     * - 추가 검증 로직에 적합
     * - 예외 발생 시 트랜잭션 롤백 가능
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void validateBeforeCommit(OrderCreated event) {
        // ✅ 트랜잭션 내에서 추가 검증
        if (event.totalAmount() > 1_000_000) {
            throw new IllegalStateException("Order amount too high");
        }
    }

    /**
     * ✅ AFTER_COMMIT - 트랜잭션 커밋 후 실행
     *
     * - 트랜잭션 이미 커밋됨
     * - 외부 API 호출에 적합
     * - 예외 발생해도 트랜잭션 롤백 불가
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyAfterCommit(OrderCreated event) {
        // ✅ 트랜잭션 밖에서 외부 API 호출
        externalApiService.notifyOrderCreated(event.orderId());
    }

    /**
     * ✅ AFTER_ROLLBACK - 트랜잭션 롤백 시 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleRollback(OrderCreated event) {
        // ✅ 롤백 시 보상 트랜잭션
        compensationService.rollbackOrder(event.orderId());
    }

    /**
     * ✅ AFTER_COMPLETION - 커밋/롤백 상관없이 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void cleanupAfterCompletion(OrderCreated event) {
        // ✅ 정리 작업
        cacheService.clearOrderCache(event.orderId());
    }
}
```

---

## ⚠️ 주의사항 - ThreadLocal 사용

### 문제점: ThreadLocal with Virtual Threads

```java
// ❌ Before - ThreadLocal 사용 (위험)
public class UserContext {

    // ❌ Virtual Thread는 재사용되므로 ThreadLocal 위험
    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

    public static void setCurrentUser(User user) {
        CURRENT_USER.set(user);
    }

    public static User getCurrentUser() {
        return CURRENT_USER.get();
    }

    /**
     * ❌ Virtual Thread 재사용 시 이전 값 남아있을 수 있음!
     */
    public static void clear() {
        CURRENT_USER.remove();  // ❌ 호출 누락 시 문제
    }
}

// ✅ After - ScopedValue 사용 (Java 21+)
public class UserContext {

    // ✅ ScopedValue는 Virtual Thread 안전
    private static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

    public static <R> R runWithUser(User user, Supplier<R> action) {
        return ScopedValue.where(CURRENT_USER, user)
            .call(action);
    }

    public static User getCurrentUser() {
        return CURRENT_USER.orElse(null);
    }
}

/**
 * ✅ ScopedValue 사용 예시
 */
@Service
public class OrderService {

    public void processOrder(CreateOrderCommand command, User currentUser) {
        // ✅ ScopedValue로 User 전달 (Virtual Thread 안전)
        UserContext.runWithUser(currentUser, () -> {
            orderRepository.save(order);
            return null;
        });
    }
}
```

**핵심 포인트**:
- ❌ `ThreadLocal`은 Virtual Thread 재사용 시 문제 발생 가능
- ✅ `ScopedValue` (Java 21+) 사용 권장
- ✅ `@TransactionalEventListener`는 Spring이 자동 관리하므로 안전

---

## 📋 Spring Integration 체크리스트

### @Transactional
- [ ] Virtual Thread에서도 정상 작동하는가?
- [ ] 내부 메서드 호출 시 프록시 우회 주의했는가?
- [ ] 트랜잭션 timeout 설정했는가?
- [ ] 외부 API 호출은 트랜잭션 밖에서 수행하는가?

### @Async
- [ ] Spring Boot 3.2+ 활성화했는가? (`spring.threads.virtual.enabled: true`)
- [ ] `CompletableFuture` 반환으로 결과 처리하는가?
- [ ] Exception Handling 구현했는가?
- [ ] `StructuredTaskScope`와 함께 사용하는가?

### @TransactionalEventListener
- [ ] `AFTER_COMMIT`으로 트랜잭션 커밋 후 실행하는가?
- [ ] `@Async`와 함께 사용하여 비동기 처리하는가?
- [ ] 외부 API 호출은 Event Handler에서 수행하는가?
- [ ] 보상 트랜잭션 (`AFTER_ROLLBACK`) 구현했는가?

### ThreadLocal 주의
- [ ] `ThreadLocal` 대신 `ScopedValue` 사용하는가?
- [ ] Spring 관리 컨텍스트 (`@TransactionalEventListener`)는 안전한가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
