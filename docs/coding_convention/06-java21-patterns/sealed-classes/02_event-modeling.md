# Event Modeling with Sealed Classes - 도메인 이벤트 타입 안전 모델링

**목적**: Sealed Classes로 도메인 이벤트 계층 구조 설계 및 이벤트 핸들러 타입 안전성 보장

**관련 문서**:
- [Domain Modeling](./01_domain-modeling.md)
- [Event-Driven Architecture](../../07-enterprise-patterns/event-driven/01_domain-events.md)

**필수 버전**: Java 17+ (Sealed Classes), Java 21+ (Pattern Matching)

---

## 📌 핵심 원칙

### 도메인 이벤트 특징

1. **불변성**: 과거 사실이므로 변경 불가 (Record 사용)
2. **타입 안전성**: Sealed Classes로 제한된 이벤트 타입
3. **Exhaustive Handling**: 모든 이벤트 타입 처리 강제
4. **계층 구조**: 이벤트 카테고리별 그룹화

---

## ✅ Event Modeling 패턴

### 패턴 1: Sealed Event Hierarchy

```java
package com.company.domain.order.event;

import java.time.Instant;

/**
 * Order Event - Root Sealed Interface
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface OrderEvent
    permits OrderCreated, OrderApproved, OrderShipped, OrderDelivered, OrderCancelled {

    /**
     * 모든 이벤트의 공통 메타데이터
     */
    OrderId orderId();
    Instant occurredAt();
}

/**
 * Order Created Event
 */
public record OrderCreated(
    OrderId orderId,
    CustomerId customerId,
    Money totalAmount,
    Instant occurredAt
) implements OrderEvent {}

/**
 * Order Approved Event
 */
public record OrderApproved(
    OrderId orderId,
    UserId approvedBy,
    Instant occurredAt
) implements OrderEvent {}

/**
 * Order Shipped Event
 */
public record OrderShipped(
    OrderId orderId,
    String trackingNumber,
    Address shippingAddress,
    Instant occurredAt
) implements OrderEvent {}

/**
 * Order Delivered Event
 */
public record OrderDelivered(
    OrderId orderId,
    String recipientName,
    Instant occurredAt
) implements OrderEvent {}

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

### 패턴 2: Event Handler with Pattern Matching

```java
package com.company.application.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Order Event Handler - Pattern Matching
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderEventHandler {

    private final EmailService emailService;
    private final NotificationService notificationService;

    /**
     * ✅ Pattern Matching으로 이벤트 처리
     *
     * - Exhaustive Checking: 모든 이벤트 타입 처리 강제
     * - Record Deconstruction: 이벤트 데이터 자동 추출
     */
    @EventListener
    public void handleOrderEvent(OrderEvent event) {
        switch (event) {
            case OrderCreated(var orderId, var customerId, var amount, var occurredAt) -> {
                emailService.sendOrderConfirmation(customerId, orderId);
                notificationService.notifyOrderCreated(orderId);
            }

            case OrderApproved(var orderId, var approvedBy, var occurredAt) -> {
                notificationService.notifyOrderApproved(orderId, approvedBy);
            }

            case OrderShipped(var orderId, var tracking, var address, var occurredAt) -> {
                emailService.sendShippingNotification(orderId, tracking);
                notificationService.notifyOrderShipped(orderId);
            }

            case OrderDelivered(var orderId, var recipient, var occurredAt) -> {
                emailService.sendDeliveryConfirmation(orderId, recipient);
            }

            case OrderCancelled(var orderId, var reason, var occurredAt) -> {
                emailService.sendCancellationNotification(orderId, reason);
                notificationService.notifyOrderCancelled(orderId);
            }
        }
    }
}
```

**Before (기존 if-else 체이닝)**:
```java
// ❌ Before - 장황한 instanceof + 타입 캐스팅
@EventListener
public void handleOrderEvent(OrderEvent event) {
    if (event instanceof OrderCreated) {
        OrderCreated created = (OrderCreated) event;
        emailService.sendOrderConfirmation(created.customerId(), created.orderId());
    } else if (event instanceof OrderApproved) {
        OrderApproved approved = (OrderApproved) event;
        notificationService.notifyOrderApproved(approved.orderId(), approved.approvedBy());
    }
    // ... 반복
}
```

**After (Sealed + Pattern Matching)**:
- ✅ 코드 간결성 (50% 감소)
- ✅ 컴파일 타임 검증 (이벤트 타입 누락 방지)
- ✅ 자동 타입 추론 (명시적 캐스팅 불필요)

---

### 패턴 3: Hierarchical Event Modeling

```java
/**
 * Payment Event - 계층적 이벤트 모델
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface PaymentEvent
    permits PaymentStarted, PaymentCompleted, PaymentFailed {

    PaymentId paymentId();
    Instant occurredAt();
}

/**
 * Payment Started Events (하위 Sealed)
 */
public sealed interface PaymentStarted extends PaymentEvent
    permits PaymentInitiated, PaymentProcessing {}

public record PaymentInitiated(
    PaymentId paymentId,
    Money amount,
    Instant occurredAt
) implements PaymentStarted {}

public record PaymentProcessing(
    PaymentId paymentId,
    String gateway,
    Instant occurredAt
) implements PaymentStarted {}

/**
 * Payment Completed Event
 */
public record PaymentCompleted(
    PaymentId paymentId,
    String transactionId,
    Instant occurredAt
) implements PaymentEvent {}

/**
 * Payment Failed Events (하위 Sealed)
 */
public sealed interface PaymentFailed extends PaymentEvent
    permits PaymentRejected, PaymentTimeout, PaymentError {}

public record PaymentRejected(
    PaymentId paymentId,
    String reason,
    Instant occurredAt
) implements PaymentFailed {}

public record PaymentTimeout(
    PaymentId paymentId,
    Instant occurredAt
) implements PaymentFailed {}

public record PaymentError(
    PaymentId paymentId,
    String errorCode,
    String errorMessage,
    Instant occurredAt
) implements PaymentFailed {}
```

**계층적 이벤트 처리**:
```java
/**
 * 계층적 Pattern Matching
 */
@EventListener
public void handlePaymentEvent(PaymentEvent event) {
    switch (event) {
        // ✅ 상위 타입으로 그룹 처리
        case PaymentStarted started -> {
            logPaymentStarted(started.paymentId());
        }

        case PaymentCompleted(var paymentId, var txId, var time) -> {
            updateOrderStatus(paymentId, "COMPLETED");
        }

        // ✅ 하위 타입별 세부 처리
        case PaymentFailed failed -> switch (failed) {
            case PaymentRejected(var id, var reason, var time) ->
                notifyPaymentRejected(id, reason);

            case PaymentTimeout(var id, var time) ->
                retryPayment(id);

            case PaymentError(var id, var code, var msg, var time) ->
                logPaymentError(id, code, msg);
        };
    }
}
```

---

## 🎯 실전 예제: Saga Pattern with Events

### ✅ Example: Order Saga

```java
/**
 * Order Saga Event - Sealed Hierarchy
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface OrderSagaEvent
    permits SagaStarted, SagaStepCompleted, SagaFailed, SagaCompleted {}

public record SagaStarted(OrderId orderId, Instant occurredAt) implements OrderSagaEvent {}

public sealed interface SagaStepCompleted extends OrderSagaEvent
    permits InventoryReserved, PaymentProcessed, ShippingScheduled {}

public record InventoryReserved(
    OrderId orderId,
    List<ProductId> reservedProducts,
    Instant occurredAt
) implements SagaStepCompleted {}

public record PaymentProcessed(
    OrderId orderId,
    PaymentId paymentId,
    Instant occurredAt
) implements SagaStepCompleted {}

public record ShippingScheduled(
    OrderId orderId,
    String trackingNumber,
    Instant occurredAt
) implements SagaStepCompleted {}

public record SagaFailed(
    OrderId orderId,
    String failedStep,
    String reason,
    Instant occurredAt
) implements OrderSagaEvent {}

public record SagaCompleted(
    OrderId orderId,
    Instant occurredAt
) implements OrderSagaEvent {}

/**
 * Saga Orchestrator
 */
@Component
public class OrderSagaOrchestrator {

    @EventListener
    public void handleSagaEvent(OrderSagaEvent event) {
        switch (event) {
            case SagaStarted(var orderId, var time) -> {
                // Step 1: Reserve Inventory
                reserveInventory(orderId);
            }

            case InventoryReserved(var orderId, var products, var time) -> {
                // Step 2: Process Payment
                processPayment(orderId);
            }

            case PaymentProcessed(var orderId, var paymentId, var time) -> {
                // Step 3: Schedule Shipping
                scheduleShipping(orderId);
            }

            case ShippingScheduled(var orderId, var tracking, var time) -> {
                // Saga 완료
                publishEvent(new SagaCompleted(orderId, Instant.now()));
            }

            case SagaFailed(var orderId, var step, var reason, var time) -> {
                // Compensating Transaction (보상 트랜잭션)
                compensate(orderId, step);
            }

            case SagaCompleted(var orderId, var time) -> {
                // 주문 완료 처리
                completeOrder(orderId);
            }
        }
    }
}
```

---

## 📋 Event Modeling 체크리스트

### 설계
- [ ] 이벤트는 과거 사실 (불변 Record)
- [ ] Sealed Interface로 제한된 이벤트 타입
- [ ] 계층 구조로 이벤트 그룹화

### 구현
- [ ] Pattern Matching으로 이벤트 처리
- [ ] Exhaustive Checking 활용
- [ ] 공통 메타데이터 (id, occurredAt)

### 테스트
- [ ] 모든 이벤트 타입 핸들러 테스트
- [ ] 계층적 이벤트 처리 검증

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
