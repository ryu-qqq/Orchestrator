# Domain Events - 도메인 이벤트로 Aggregate 결합도 감소

**목적**: Domain Event를 활용한 Aggregate 간 느슨한 결합 및 비동기 처리

**관련 문서**:
- [Event Modeling with Sealed Classes](../../06-java21-patterns/sealed-classes/02_event-modeling.md)
- [Saga Pattern](./03_saga-pattern.md)

**필수 버전**: Spring Framework 5.0+, Java 21+ (Sealed Classes)

---

## 📌 핵심 원칙

### Domain Event란?

1. **과거 사실**: "OrderCreated", "PaymentProcessed" (과거형)
2. **불변 객체**: 발생 후 변경 불가 (Record 사용)
3. **Aggregate 결합도 감소**: 직접 의존 대신 이벤트 발행
4. **비동기 처리**: 트랜잭션 경계 분리

### DDD에서의 역할

- **Aggregate Root에서 발행**: 상태 변경 시 이벤트 생성
- **다른 Aggregate 반응**: 이벤트 리스닝으로 상태 동기화
- **Eventual Consistency**: 최종 일관성 보장

---

## ❌ 기존 직접 참조 문제점

### 문제 1: Aggregate 간 강한 결합

```java
// ❌ Before - 직접 의존 (강한 결합)
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;  // ❌ 직접 의존
    private final EmailService emailService;          // ❌ 직접 의존

    /**
     * ❌ 문제점:
     * - Order → Inventory → Email 순차 실행 (트랜잭션 길어짐)
     * - Email 실패 시 Order 트랜잭션 롤백 위험
     * - 3가지 책임이 하나의 메서드에 혼재
     */
    @Transactional
    public void createOrder(CreateOrderCommand command) {
        // 1. Order 생성
        Order order = Order.create(command.customerId(), command.items());
        orderRepository.save(order);

        // 2. 재고 차감 (다른 Aggregate 직접 수정!)
        inventoryService.decreaseStock(command.items());  // ❌

        // 3. 이메일 발송 (외부 API 호출)
        emailService.sendOrderConfirmation(order);  // ❌ 트랜잭션 내 외부 API
    }
}
```

**문제점**:
- ❌ Order, Inventory, Email 3가지 관심사가 하나의 트랜잭션에
- ❌ 외부 API 호출이 트랜잭션 내부 (성능 저하)
- ❌ 테스트 시 모든 의존성 Mocking 필요
- ❌ 확장 어려움 (새로운 기능 추가 시 메서드 수정)

---

## ✅ Domain Event 패턴

### 패턴 1: Event 정의 (Sealed Interface + Record)

```java
package com.company.domain.order.event;

import java.time.Instant;

/**
 * Order Event - Sealed Interface
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface OrderEvent
    permits OrderCreated, OrderCancelled {

    /**
     * 공통 메타데이터
     */
    OrderId orderId();
    Instant occurredAt();
}

/**
 * Order Created Event
 *
 * - 과거 사실: "주문이 생성되었다"
 * - 불변 객체: Record 사용
 */
public record OrderCreated(
    OrderId orderId,
    CustomerId customerId,
    List<OrderLineItem> items,
    Money totalAmount,
    Instant occurredAt
) implements OrderEvent {

    /**
     * 정적 팩토리 메서드
     */
    public static OrderCreated of(Order order) {
        return new OrderCreated(
            order.getId(),
            order.getCustomerId(),
            order.getItems(),
            order.getTotalAmount(),
            Instant.now()
        );
    }
}

/**
 * Order Cancelled Event
 */
public record OrderCancelled(
    OrderId orderId,
    String reason,
    Instant occurredAt
) implements OrderEvent {}
```

---

### 패턴 2: Aggregate에서 Event 발행

```java
package com.company.domain.order;

import org.springframework.data.domain.AbstractAggregateRoot;

/**
 * Order Aggregate - Event 발행
 *
 * @author development-team
 * @since 1.0.0
 */
public class Order extends AbstractAggregateRoot<Order> {

    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderLineItem> items;
    private OrderStatus status;

    /**
     * ✅ 정적 팩토리 메서드에서 Event 등록
     */
    public static Order create(CustomerId customerId, List<OrderLineItem> items) {
        Order order = new Order(OrderId.generate(), customerId, items, OrderStatus.PENDING);

        // ✅ Domain Event 등록 (즉시 발행 X, 트랜잭션 커밋 시 발행)
        order.registerEvent(OrderCreated.of(order));

        return order;
    }

    /**
     * ✅ 주문 취소 시 Event 발행
     */
    public void cancel(String reason) {
        if (!this.status.isCancellable()) {
            throw new OrderNotCancellableException(this.id);
        }

        this.status = OrderStatus.CANCELLED;

        // ✅ Domain Event 등록
        registerEvent(new OrderCancelled(this.id, reason, Instant.now()));
    }
}
```

**핵심 포인트**:
- ✅ `AbstractAggregateRoot` 상속 → `registerEvent()` 메서드 제공
- ✅ Event는 즉시 발행되지 않고 **트랜잭션 커밋 시 자동 발행**
- ✅ Repository `save()` 호출 시 Spring Data가 Event 발행

---

### 패턴 3: Event Handler (Spring ApplicationEventPublisher)

```java
package com.company.application.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Order Event Handler - 비동기 처리
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
     * ✅ TransactionalEventListener - 트랜잭션 커밋 후 실행
     *
     * - AFTER_COMMIT: Order 저장 트랜잭션 성공 후에만 실행
     * - @Async: 별도 스레드에서 비동기 실행 (Virtual Thread)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreated event) {
        // 1. 재고 차감 (별도 트랜잭션)
        inventoryService.decreaseStock(event.items());

        // 2. 이메일 발송 (외부 API - 트랜잭션 밖)
        emailService.sendOrderConfirmation(event.orderId(), event.customerId());

        // 3. 푸시 알림 (외부 API - 트랜잭션 밖)
        notificationService.notifyOrderCreated(event.orderId());
    }

    /**
     * ✅ 여러 Handler가 같은 Event 처리 가능
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logOrderCreated(OrderCreated event) {
        log.info("Order created: {}", event.orderId());
    }
}
```

**Before (직접 의존) vs After (Event 발행)**:

| 항목 | Before (직접 의존) | After (Event 발행) |
|------|-------------------|-------------------|
| 결합도 | 강함 (3개 서비스 의존) | 느슨함 (Event만 발행) |
| 트랜잭션 | 하나의 긴 트랜잭션 | 분리된 여러 트랜잭션 |
| 외부 API | 트랜잭션 내부 (위험) | 트랜잭션 외부 (안전) |
| 확장성 | 메서드 수정 필요 | Handler 추가만 |
| 테스트 | 모든 의존성 Mocking | Event 발행만 검증 |

---

## 🎯 실전 예제: 결제 연동

### ✅ Example: Payment Event

```java
/**
 * Payment Event - Sealed Hierarchy
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface PaymentEvent
    permits PaymentCompleted, PaymentFailed {}

public record PaymentCompleted(
    PaymentId paymentId,
    OrderId orderId,
    Money amount,
    String transactionId,
    Instant occurredAt
) implements PaymentEvent {}

public record PaymentFailed(
    PaymentId paymentId,
    OrderId orderId,
    String reason,
    Instant occurredAt
) implements PaymentEvent {}

/**
 * Payment Aggregate
 */
public class Payment extends AbstractAggregateRoot<Payment> {

    public void complete(String transactionId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new PaymentNotPendingException(this.id);
        }

        this.status = PaymentStatus.COMPLETED;
        this.transactionId = transactionId;

        // ✅ Event 발행
        registerEvent(new PaymentCompleted(
            this.id, this.orderId, this.amount, transactionId, Instant.now()
        ));
    }
}

/**
 * Order가 Payment Event에 반응
 */
@Component
public class OrderPaymentEventHandler {

    private final OrderRepository orderRepository;

    /**
     * ✅ 결제 완료 시 Order 상태 업데이트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompleted event) {
        Order order = orderRepository.findById(event.orderId()).orElseThrow();

        // Order Aggregate의 비즈니스 로직 호출
        order.markAsPaid();

        orderRepository.save(order); // Event 재발행 가능
    }

    /**
     * ✅ 결제 실패 시 Order 취소
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailed event) {
        Order order = orderRepository.findById(event.orderId()).orElseThrow();

        order.cancel("Payment failed: " + event.reason());

        orderRepository.save(order);
    }
}
```

---

## 🔧 고급 패턴

### 패턴 1: Event Store (선택적)

```java
/**
 * Event Store - 모든 Event 저장
 *
 * - Event Sourcing의 기초
 * - 감사 로그 (Audit Log)
 * - 이벤트 재생 (Event Replay)
 *
 * @author development-team
 * @since 1.0.0
 */
@Entity
@Table(name = "domain_events")
public class DomainEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    /**
     * Event → JSON 직렬화 후 저장
     */
    public static DomainEventEntity from(Object event) {
        return new DomainEventEntity(
            event.getClass().getSimpleName(),
            serializeToJson(event),
            Instant.now()
        );
    }
}

/**
 * Event Publishing Interceptor
 */
@Component
public class EventStoreInterceptor {

    private final DomainEventRepository eventRepository;

    @EventListener
    public void storeEvent(OrderEvent event) {
        eventRepository.save(DomainEventEntity.from(event));
    }
}
```

---

### 패턴 2: Event Versioning

```java
/**
 * Event Versioning - 하위 호환성 보장
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderCreatedV1(
    OrderId orderId,
    CustomerId customerId,
    Money totalAmount,
    Instant occurredAt
) implements OrderEvent {}

/**
 * V2: items 필드 추가
 */
public record OrderCreatedV2(
    OrderId orderId,
    CustomerId customerId,
    List<OrderLineItem> items,  // ✅ 추가 필드
    Money totalAmount,
    Instant occurredAt
) implements OrderEvent {}

/**
 * Handler는 모든 버전 처리
 */
@Component
public class OrderEventHandler {

    @EventListener
    public void handleOrderCreated(OrderEvent event) {
        switch (event) {
            case OrderCreatedV1(var id, var customerId, var amount, var time) -> {
                // V1 처리 로직
            }
            case OrderCreatedV2(var id, var customerId, var items, var amount, var time) -> {
                // V2 처리 로직
            }
        }
    }
}
```

---

## 🔧 Long FK 전략과의 통합

### Pattern: Event에서 Long FK 활용

```java
/**
 * ✅ Good - Long FK 기반 Event
 *
 * - Entity 직접 참조 대신 Long ID 사용
 * - Law of Demeter 준수
 * - 직렬화 안전
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderCreated(
    OrderId orderId,
    Long customerId,           // ✅ Long FK (Customer Entity ID)
    Long shippingAddressId,    // ✅ Long FK (Address Entity ID)
    List<OrderLineItem> items,
    Money totalAmount,
    Instant occurredAt
) implements OrderEvent {

    /**
     * ✅ Aggregate에서 Long FK 추출
     */
    public static OrderCreated from(Order order) {
        return new OrderCreated(
            order.getId(),
            order.getCustomerId(),        // Long FK 반환
            order.getShippingAddressId(), // Long FK 반환
            order.getItems(),
            order.getTotalAmount(),
            Instant.now()
        );
    }
}
```

### ❌ Bad - Entity 직접 참조 (Law of Demeter 위반)

```java
/**
 * ❌ Bad - Entity 직접 포함
 */
public record OrderCreated(
    OrderId orderId,
    Customer customer,         // ❌ Entity 직접 참조
    Address shippingAddress,   // ❌ Entity 직접 참조
    List<OrderLineItem> items,
    Instant occurredAt
) implements OrderEvent {}

// 문제점:
// 1. Law of Demeter 위반: event.customer().getEmail()
// 2. 직렬화 문제: Entity 그래프 전체 직렬화
// 3. LazyInitializationException 위험
// 4. 영속성 컨텍스트 의존
```

### ✅ Event Handler에서 명시적 조회

```java
/**
 * Event Handler에서 Long FK로 Entity 조회
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderEventHandler {

    private final LoadCustomerPort loadCustomerPort;
    private final LoadAddressPort loadAddressPort;
    private final EmailService emailService;

    /**
     * ✅ Long FK로 필요한 Entity 명시적 조회
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreated event) {
        // ✅ Event의 Long FK로 Customer 조회
        Customer customer = loadCustomerPort.loadById(event.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(event.customerId()));

        // ✅ Event의 Long FK로 Address 조회
        Address address = loadAddressPort.loadById(event.shippingAddressId())
            .orElseThrow(() -> new AddressNotFoundException(event.shippingAddressId()));

        // ✅ 명시적으로 조회한 Entity 사용
        emailService.sendOrderConfirmation(
            customer.getEmail(),
            address.getFullAddress(),
            event.orderId()
        );
    }
}
```

**Long FK 전략 장점:**
- ✅ Law of Demeter 준수 (Getter 체이닝 원천 차단)
- ✅ 직렬화 안전 (Primitive 타입만 포함)
- ✅ 영속성 컨텍스트 독립적
- ✅ Event Handler에서 필요한 데이터만 조회

---

## 📋 Domain Event 체크리스트

### 설계
- [ ] Event는 과거형 네이밍 (`OrderCreated`, `PaymentCompleted`)
- [ ] Sealed Interface + Record 사용
- [ ] 불변 객체 (발행 후 변경 불가)
- [ ] 공통 메타데이터 포함 (id, occurredAt)
- [ ] Entity 직접 참조 대신 Long FK 사용

### 구현
- [ ] Aggregate Root에서 `registerEvent()` 사용
- [ ] `@TransactionalEventListener(AFTER_COMMIT)` 사용
- [ ] 외부 API 호출은 Event Handler에서 (`@Async`)
- [ ] Event Handler는 별도 트랜잭션
- [ ] Event Handler에서 Long FK로 명시적 조회

### 테스트
- [ ] Event 발행 검증 (`@DomainEvents` 테스트)
- [ ] Event Handler 격리 테스트
- [ ] 트랜잭션 경계 검증

---

## 📚 관련 가이드

**전제 조건**:
- [Long FK Strategy](../../04-persistence-layer/jpa-entity-design/01_long-fk-strategy.md) - Long FK 전략
- [Law of Demeter](../../02-domain-layer/law-of-demeter/01_getter-chaining-prohibition.md) - Getter 체이닝 금지

**연관 패턴**:
- [Multi-Module Event Design](./04_multi-module-event-design.md) - 멀티모듈 Event 설계
- [Event Modeling](../../06-java21-patterns/sealed-classes/02_event-modeling.md) - Sealed Classes 활용

**심화 학습**:
- [Saga Pattern](./03_saga-pattern.md) - 분산 트랜잭션
- [Event Sourcing](./02_event-sourcing.md) - Event Store 구현

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
