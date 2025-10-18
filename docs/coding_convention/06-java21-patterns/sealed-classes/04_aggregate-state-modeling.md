# Aggregate State Modeling with Sealed Classes - 상태 전이 모델링

**목적**: Sealed Classes로 Aggregate 상태와 상태 전이를 타입 안전하게 모델링

**관련 문서**:
- [Domain Modeling](./01_domain-modeling.md)
- [Aggregate Boundaries](../../../02-domain-layer/aggregate-design/01_aggregate-boundaries.md)
- [Domain Events](../../../07-enterprise-patterns/event-driven/01_domain-events.md)
- [Result Types](./03_result-types.md)

**필수 버전**: Java 17+ (Sealed Classes), Java 21+ (Pattern Matching)

---

## 📌 핵심 원칙

### Aggregate State란?

1. **명시적 상태**: Aggregate의 라이프사이클을 Sealed로 표현
2. **타입 안전한 전이**: 허용된 상태 전이만 컴파일 타임 검증
3. **불변성**: Record + Sealed로 상태 변경은 새로운 인스턴스 생성
4. **도메인 이벤트**: 상태 전이 시 이벤트 발행

---

## ❌ 안티패턴 - Enum 기반 상태 관리

### 문제점: 단순 Enum + Flag 조합

```java
// ❌ Before - Enum + Flag (타입 안전성 부족)
public enum OrderStatus {
    DRAFT, PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}

public class Order {
    private Long id;
    private OrderStatus status;
    private boolean isPaid;        // ❌ Flag 필드
    private String trackingNumber; // ❌ 특정 상태에만 필요
    private String cancellationReason; // ❌ 특정 상태에만 필요

    /**
     * ❌ 문제점: 모든 상태에서 모든 필드 접근 가능
     */
    public void ship(String trackingNumber) {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Order must be confirmed");
        }

        if (!this.isPaid) {  // ❌ 런타임 검증
            throw new IllegalStateException("Order must be paid");
        }

        this.status = OrderStatus.SHIPPED;
        this.trackingNumber = trackingNumber;
    }

    /**
     * ❌ 문제점: 잘못된 상태 전이 런타임에만 발견
     */
    public void cancel(String reason) {
        if (this.status == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivered orders cannot be cancelled");
        }

        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
    }
}
```

**문제점**:
- ❌ 런타임 검증 (`if (status != CONFIRMED)`)
- ❌ 불필요한 필드 (`trackingNumber`는 SHIPPED 상태에만 필요)
- ❌ 타입 안전성 부족 (잘못된 상태 전이 컴파일 타임 검증 불가)
- ❌ 상태별 비즈니스 로직 분산

---

## ✅ 권장 패턴 - Sealed Aggregate State

### 패턴 1: Sealed State Hierarchy

```java
package com.company.domain.order;

import java.time.Instant;
import java.util.List;

/**
 * Order Aggregate State - Sealed Hierarchy
 *
 * - 각 상태를 별도 타입으로 모델링
 * - 타입 안전한 상태 전이
 * - 상태별 필수 데이터만 포함
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface OrderState
    permits DraftOrder, PendingOrder, ConfirmedOrder, ShippedOrder, DeliveredOrder, CancelledOrder {

    /**
     * ✅ 공통 메타데이터
     */
    Long orderId();
    Long customerId();
    List<OrderLineItem> items();
    Long totalAmount();
    Instant createdAt();
}

/**
 * ✅ Draft Order - 초안
 */
public record DraftOrder(
    Long orderId,
    Long customerId,
    List<OrderLineItem> items,
    Long totalAmount,
    Instant createdAt
) implements OrderState {

    /**
     * ✅ 상태 전이: Draft → Pending
     */
    public PendingOrder submit() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot submit empty order");
        }

        return new PendingOrder(
            orderId,
            customerId,
            items,
            totalAmount,
            createdAt,
            Instant.now()
        );
    }
}

/**
 * ✅ Pending Order - 결제 대기
 */
public record PendingOrder(
    Long orderId,
    Long customerId,
    List<OrderLineItem> items,
    Long totalAmount,
    Instant createdAt,
    Instant submittedAt
) implements OrderState {

    /**
     * ✅ 상태 전이: Pending → Confirmed (결제 완료)
     */
    public ConfirmedOrder confirm(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Payment ID is required");
        }

        return new ConfirmedOrder(
            orderId,
            customerId,
            items,
            totalAmount,
            createdAt,
            submittedAt,
            paymentId,
            Instant.now()
        );
    }

    /**
     * ✅ 상태 전이: Pending → Cancelled
     */
    public CancelledOrder cancel(String reason) {
        return new CancelledOrder(
            orderId,
            customerId,
            items,
            totalAmount,
            createdAt,
            reason,
            Instant.now()
        );
    }
}

/**
 * ✅ Confirmed Order - 결제 완료 (배송 준비)
 */
public record ConfirmedOrder(
    Long orderId,
    Long customerId,
    List<OrderLineItem> items,
    Long totalAmount,
    Instant createdAt,
    Instant submittedAt,
    String paymentId,  // ✅ 이 상태부터 paymentId 필수
    Instant confirmedAt
) implements OrderState {

    /**
     * ✅ 상태 전이: Confirmed → Shipped
     */
    public ShippedOrder ship(String trackingNumber, String carrier) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number is required");
        }

        return new ShippedOrder(
            orderId,
            customerId,
            items,
            totalAmount,
            createdAt,
            submittedAt,
            paymentId,
            confirmedAt,
            trackingNumber,
            carrier,
            Instant.now()
        );
    }

    /**
     * ✅ 상태 전이: Confirmed → Cancelled
     */
    public CancelledOrder cancel(String reason) {
        // 환불 로직 필요
        return new CancelledOrder(
            orderId,
            customerId,
            items,
            totalAmount,
            createdAt,
            reason,
            Instant.now()
        );
    }
}

/**
 * ✅ Shipped Order - 배송 중
 */
public record ShippedOrder(
    Long orderId,
    Long customerId,
    List<OrderLineItem> items,
    Long totalAmount,
    Instant createdAt,
    Instant submittedAt,
    String paymentId,
    Instant confirmedAt,
    String trackingNumber,  // ✅ 이 상태부터 trackingNumber 필수
    String carrier,
    Instant shippedAt
) implements OrderState {

    /**
     * ✅ 상태 전이: Shipped → Delivered
     */
    public DeliveredOrder deliver(String recipientName) {
        if (recipientName == null || recipientName.isBlank()) {
            throw new IllegalArgumentException("Recipient name is required");
        }

        return new DeliveredOrder(
            orderId,
            customerId,
            items,
            totalAmount,
            createdAt,
            submittedAt,
            paymentId,
            confirmedAt,
            trackingNumber,
            carrier,
            shippedAt,
            recipientName,
            Instant.now()
        );
    }
}

/**
 * ✅ Delivered Order - 배송 완료 (종료 상태)
 */
public record DeliveredOrder(
    Long orderId,
    Long customerId,
    List<OrderLineItem> items,
    Long totalAmount,
    Instant createdAt,
    Instant submittedAt,
    String paymentId,
    Instant confirmedAt,
    String trackingNumber,
    String carrier,
    Instant shippedAt,
    String recipientName,  // ✅ 이 상태부터 recipientName 필수
    Instant deliveredAt
) implements OrderState {
    // ✅ 종료 상태 - 더 이상 전이 불가
}

/**
 * ✅ Cancelled Order - 취소 (종료 상태)
 */
public record CancelledOrder(
    Long orderId,
    Long customerId,
    List<OrderLineItem> items,
    Long totalAmount,
    Instant createdAt,
    String cancellationReason,  // ✅ 취소 사유 필수
    Instant cancelledAt
) implements OrderState {
    // ✅ 종료 상태 - 더 이상 전이 불가
}
```

**핵심 포인트**:
- ✅ 각 상태를 별도 Record로 모델링
- ✅ 상태별 필수 데이터만 포함 (`trackingNumber`는 `ShippedOrder`부터)
- ✅ 타입 안전한 상태 전이 메서드 (`submit()`, `confirm()`, `ship()`)
- ✅ 컴파일 타임 검증 (잘못된 상태 전이 불가능)

---

### 패턴 2: Aggregate Root with State

```java
package com.company.domain.order;

import org.springframework.data.domain.AbstractAggregateRoot;

/**
 * Order Aggregate Root
 *
 * - OrderState를 내부에 포함
 * - 상태 전이 시 Domain Event 발행
 *
 * @author development-team
 * @since 1.0.0
 */
public class Order extends AbstractAggregateRoot<Order> {

    private OrderState state;

    /**
     * ✅ 정적 팩토리 메서드 - Draft 상태로 생성
     */
    public static Order createDraft(
        Long customerId,
        List<OrderLineItem> items
    ) {
        Long totalAmount = items.stream()
            .mapToLong(OrderLineItem::subtotal)
            .sum();

        DraftOrder draftState = new DraftOrder(
            null,  // ID는 Repository에서 할당
            customerId,
            List.copyOf(items),
            totalAmount,
            Instant.now()
        );

        return new Order(draftState);
    }

    private Order(OrderState state) {
        this.state = state;
    }

    /**
     * ✅ 상태 전이: Draft → Pending
     */
    public void submit() {
        if (!(this.state instanceof DraftOrder draft)) {
            throw new IllegalStateException("Only draft orders can be submitted");
        }

        // 상태 전이
        this.state = draft.submit();

        // ✅ Domain Event 발행
        registerEvent(new OrderSubmitted(
            state.orderId(),
            state.customerId(),
            ((PendingOrder) state).submittedAt()
        ));
    }

    /**
     * ✅ 상태 전이: Pending → Confirmed
     */
    public void confirm(String paymentId) {
        if (!(this.state instanceof PendingOrder pending)) {
            throw new IllegalStateException("Only pending orders can be confirmed");
        }

        // 상태 전이
        this.state = pending.confirm(paymentId);

        // ✅ Domain Event 발행
        registerEvent(new OrderConfirmed(
            state.orderId(),
            ((ConfirmedOrder) state).paymentId(),
            ((ConfirmedOrder) state).confirmedAt()
        ));
    }

    /**
     * ✅ 상태 전이: Confirmed → Shipped
     */
    public void ship(String trackingNumber, String carrier) {
        if (!(this.state instanceof ConfirmedOrder confirmed)) {
            throw new IllegalStateException("Only confirmed orders can be shipped");
        }

        // 상태 전이
        this.state = confirmed.ship(trackingNumber, carrier);

        // ✅ Domain Event 발행
        registerEvent(new OrderShipped(
            state.orderId(),
            ((ShippedOrder) state).trackingNumber(),
            ((ShippedOrder) state).carrier(),
            ((ShippedOrder) state).shippedAt()
        ));
    }

    /**
     * ✅ 상태 전이: Shipped → Delivered
     */
    public void deliver(String recipientName) {
        if (!(this.state instanceof ShippedOrder shipped)) {
            throw new IllegalStateException("Only shipped orders can be delivered");
        }

        // 상태 전이
        this.state = shipped.deliver(recipientName);

        // ✅ Domain Event 발행
        registerEvent(new OrderDelivered(
            state.orderId(),
            ((DeliveredOrder) state).recipientName(),
            ((DeliveredOrder) state).deliveredAt()
        ));
    }

    /**
     * ✅ 상태 전이: Pending/Confirmed → Cancelled
     */
    public void cancel(String reason) {
        CancelledOrder cancelledState = switch (this.state) {
            case PendingOrder pending -> pending.cancel(reason);
            case ConfirmedOrder confirmed -> confirmed.cancel(reason);
            case DeliveredOrder delivered ->
                throw new IllegalStateException("Delivered orders cannot be cancelled");
            default ->
                throw new IllegalStateException("Cannot cancel order in current state");
        };

        this.state = cancelledState;

        // ✅ Domain Event 발행
        registerEvent(new OrderCancelled(
            state.orderId(),
            cancelledState.cancellationReason(),
            cancelledState.cancelledAt()
        ));
    }

    /**
     * ✅ 상태 조회 (Pattern Matching)
     */
    public boolean isShippable() {
        return this.state instanceof ConfirmedOrder;
    }

    public boolean isCancellable() {
        return this.state instanceof PendingOrder || this.state instanceof ConfirmedOrder;
    }

    public boolean isDelivered() {
        return this.state instanceof DeliveredOrder;
    }

    // Getters
    public OrderState getState() { return state; }
    public Long getId() { return state.orderId(); }
    public Long getCustomerId() { return state.customerId(); }
}
```

---

### 패턴 3: Pattern Matching으로 상태별 처리

```java
/**
 * Order Service - Pattern Matching
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OrderQueryService {

    /**
     * ✅ Pattern Matching으로 상태별 처리
     */
    public String getOrderStatusDescription(Order order) {
        return switch (order.getState()) {
            case DraftOrder draft ->
                "주문 작성 중 (총 " + draft.items().size() + "개 상품)";

            case PendingOrder pending ->
                "결제 대기 중 (총액: " + pending.totalAmount() + "원)";

            case ConfirmedOrder confirmed ->
                "결제 완료 (결제 ID: " + confirmed.paymentId() + ")";

            case ShippedOrder shipped ->
                "배송 중 (운송장: " + shipped.trackingNumber() + ", " + shipped.carrier() + ")";

            case DeliveredOrder delivered ->
                "배송 완료 (수령인: " + delivered.recipientName() + ")";

            case CancelledOrder cancelled ->
                "주문 취소 (사유: " + cancelled.cancellationReason() + ")";
        };
    }

    /**
     * ✅ Guard Pattern으로 특정 상태 필터링
     */
    public List<String> getTrackingNumbers(List<Order> orders) {
        return orders.stream()
            .map(Order::getState)
            .filter(state -> state instanceof ShippedOrder)
            .map(state -> ((ShippedOrder) state).trackingNumber())
            .toList();
    }
}
```

---

## 🎯 상태 전이 다이어그램

```
┌─────────┐
│  Draft  │ ──submit()──> ┌─────────┐
└─────────┘                │ Pending │
                           └─────────┘
                                │
                     ┌──────────┼──────────┐
                     │          │          │
                 confirm()   cancel()      │
                     │          │          │
                     ↓          ↓          │
              ┌───────────┐ ┌───────────┐ │
              │ Confirmed │ │ Cancelled │ │
              └───────────┘ └───────────┘ │
                     │          ↑          │
                  ship()     cancel()      │
                     │          │          │
                     ↓          │          │
              ┌─────────┐       │          │
              │ Shipped │ ──────┘          │
              └─────────┘                  │
                     │                     │
                 deliver()                 │
                     │                     │
                     ↓                     ↓
              ┌───────────┐         ┌───────────┐
              │ Delivered │         │ Cancelled │
              └───────────┘         └───────────┘
                (종료 상태)            (종료 상태)
```

---

## 🔧 고급 패턴

### 패턴 1: Result Type과 함께 사용

```java
/**
 * ✅ Result Type과 Sealed State 조합
 */
@Service
public class OrderCommandService {

    /**
     * ✅ 상태 전이 실패를 Result로 반환
     */
    public Result<Order, OrderError> shipOrder(Long orderId, String trackingNumber, String carrier) {
        // 1. Order 조회
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 2. 상태 검증 (Pattern Matching)
        if (!(order.getState() instanceof ConfirmedOrder)) {
            return Result.failure(new OrderNotConfirmedError(orderId));
        }

        // 3. 상태 전이
        try {
            order.ship(trackingNumber, carrier);
            orderRepository.save(order);
            return Result.success(order);
        } catch (IllegalArgumentException e) {
            return Result.failure(new InvalidTrackingNumberError(trackingNumber));
        }
    }
}

/**
 * ✅ OrderError Sealed Hierarchy
 */
public sealed interface OrderError
    permits OrderNotFoundError, OrderNotConfirmedError, InvalidTrackingNumberError {

    String message();
}

public record OrderNotFoundError(Long orderId) implements OrderError {
    @Override
    public String message() {
        return "Order not found: " + orderId;
    }
}

public record OrderNotConfirmedError(Long orderId) implements OrderError {
    @Override
    public String message() {
        return "Order must be confirmed before shipping: " + orderId;
    }
}

public record InvalidTrackingNumberError(String trackingNumber) implements OrderError {
    @Override
    public String message() {
        return "Invalid tracking number: " + trackingNumber;
    }
}
```

---

### 패턴 2: Event Sourcing과 State 복원

```java
/**
 * ✅ Event Sourcing - State 복원
 */
@Service
public class OrderEventSourcingService {

    /**
     * ✅ Event로부터 State 복원
     */
    public Order rebuildFromEvents(List<OrderEvent> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("No events to rebuild");
        }

        // 초기 상태
        Order order = null;

        // 이벤트 재생
        for (OrderEvent event : events) {
            switch (event) {
                case OrderCreated created -> {
                    order = Order.createDraft(
                        created.customerId(),
                        created.items()
                    );
                }
                case OrderSubmitted submitted -> {
                    if (order != null) order.submit();
                }
                case OrderConfirmed confirmed -> {
                    if (order != null) order.confirm(confirmed.paymentId());
                }
                case OrderShipped shipped -> {
                    if (order != null) order.ship(shipped.trackingNumber(), shipped.carrier());
                }
                case OrderDelivered delivered -> {
                    if (order != null) order.deliver(delivered.recipientName());
                }
                case OrderCancelled cancelled -> {
                    if (order != null) order.cancel(cancelled.reason());
                }
            }
        }

        return order;
    }
}
```

---

## 📋 Aggregate State 체크리스트

### 설계
- [ ] 각 상태를 **별도 Record**로 모델링했는가?
- [ ] 상태별 **필수 데이터만** 포함하는가?
- [ ] **Sealed Interface**로 허용된 상태만 정의했는가?
- [ ] 상태 전이 메서드가 **타입 안전**한가?

### 구현
- [ ] Aggregate Root에서 **Domain Event** 발행하는가?
- [ ] Pattern Matching으로 **상태별 처리** 구현했는가?
- [ ] **Result Type**과 함께 사용하여 에러 처리하는가?
- [ ] 불변성 보장 (상태 전이 시 **새로운 인스턴스** 생성)

### 상태 전이
- [ ] 허용된 전이만 메서드로 제공하는가?
- [ ] 잘못된 전이는 **컴파일 타임**에 방지되는가?
- [ ] 종료 상태 (Delivered, Cancelled)는 더 이상 전이 불가능한가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
