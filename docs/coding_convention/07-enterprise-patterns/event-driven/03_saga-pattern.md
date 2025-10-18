# Saga Pattern - 분산 트랜잭션 조율

**목적**: 여러 Aggregate/서비스에 걸친 분산 트랜잭션을 보상 트랜잭션으로 관리

**관련 문서**:
- [Domain Events](./01_domain-events.md)
- [Event Modeling](../../06-java21-patterns/sealed-classes/02_event-modeling.md)

**필수 버전**: Spring Framework 5.0+

---

## 📌 핵심 원칙

### Saga Pattern이란?

1. **분산 트랜잭션**: 여러 Aggregate/서비스에 걸친 작업 조율
2. **보상 트랜잭션**: 실패 시 이전 단계 롤백 (Compensating Transaction)
3. **Eventual Consistency**: 최종 일관성 보장
4. **2가지 방식**: Orchestration vs Choreography

### ACID vs Saga

| 항목 | ACID (단일 트랜잭션) | Saga (분산 트랜잭션) |
|------|-------------------|---------------------|
| 트랜잭션 경계 | 단일 DB | 여러 서비스/Aggregate |
| 일관성 | 강한 일관성 (Immediate) | 최종 일관성 (Eventual) |
| 롤백 | 자동 (ROLLBACK) | 수동 (보상 트랜잭션) |
| 사용 사례 | 단일 Aggregate | 여러 Aggregate 조율 |

---

## ❌ 단일 트랜잭션 문제점

### 문제: 여러 Aggregate 동시 수정

```java
// ❌ Before - 하나의 트랜잭션에서 여러 Aggregate 수정
@Service
public class OrderService {

    /**
     * ❌ 문제점:
     * - 3개 Aggregate (Order, Inventory, Payment) 동시 수정
     * - Payment 실패 시 Order, Inventory도 롤백
     * - 트랜잭션 길어짐 (외부 API 호출 포함)
     * - DDD 원칙 위반 (Aggregate 경계 무시)
     */
    @Transactional
    public void processOrder(OrderCommand cmd) {
        // 1. Order 생성
        Order order = orderRepository.save(Order.create(cmd));

        // 2. Inventory 차감 (다른 Aggregate!)
        Inventory inventory = inventoryRepository.findById(cmd.productId()).orElseThrow();
        inventory.decrease(cmd.quantity());  // ❌ Order 트랜잭션 내부
        inventoryRepository.save(inventory);

        // 3. Payment 처리 (또 다른 Aggregate!)
        Payment payment = Payment.create(order.getId(), cmd.amount());
        paymentRepository.save(payment);

        // 4. 외부 결제 API 호출
        paymentGateway.charge(payment);  // ❌ 외부 API (느림, 실패 위험)
    }
}
```

**문제점**:
- ❌ DDD 원칙 위반 (하나의 트랜잭션 = 하나의 Aggregate)
- ❌ Payment API 실패 시 Order도 롤백 (불필요한 롤백)
- ❌ 긴 트랜잭션 (DB 커넥션 점유 시간 길어짐)
- ❌ 확장 어려움 (서비스 분리 시 분산 트랜잭션 문제)

---

## ✅ Saga Pattern - Orchestration

### 패턴: 중앙 조율자가 단계별 실행

```java
package com.company.application.saga;

/**
 * Order Saga - Orchestration 방식
 *
 * - 중앙 Orchestrator가 각 단계 순차 실행
 * - 실패 시 보상 트랜잭션 실행
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderSagaOrchestrator {

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;

    /**
     * ✅ Saga 시작
     */
    public void startOrderSaga(CreateOrderCommand cmd) {
        SagaState state = new SagaState(cmd);

        try {
            // Step 1: Order 생성
            state.orderId = orderService.createOrder(cmd);

            // Step 2: Inventory 예약
            state.inventoryReservationId = inventoryService.reserveStock(cmd.items());

            // Step 3: Payment 처리
            state.paymentId = paymentService.processPayment(state.orderId, cmd.amount());

            // Step 4: Shipping 예약
            state.shippingId = shippingService.scheduleShipping(state.orderId);

            // ✅ Saga 성공
            completeSaga(state);

        } catch (InventoryNotAvailableException e) {
            // ⚠️ Step 2 실패 → Step 1 보상
            compensateOrderCreation(state);
            throw new SagaFailedException("Inventory not available", e);

        } catch (PaymentFailedException e) {
            // ⚠️ Step 3 실패 → Step 2, Step 1 보상
            compensateInventoryReservation(state);
            compensateOrderCreation(state);
            throw new SagaFailedException("Payment failed", e);

        } catch (ShippingFailedException e) {
            // ⚠️ Step 4 실패 → Step 3, Step 2, Step 1 보상
            compensatePayment(state);
            compensateInventoryReservation(state);
            compensateOrderCreation(state);
            throw new SagaFailedException("Shipping failed", e);
        }
    }

    /**
     * ✅ 보상 트랜잭션: Order 취소
     */
    private void compensateOrderCreation(SagaState state) {
        if (state.orderId != null) {
            orderService.cancelOrder(state.orderId, "Saga failed");
        }
    }

    /**
     * ✅ 보상 트랜잭션: Inventory 예약 취소
     */
    private void compensateInventoryReservation(SagaState state) {
        if (state.inventoryReservationId != null) {
            inventoryService.cancelReservation(state.inventoryReservationId);
        }
    }

    /**
     * ✅ 보상 트랜잭션: Payment 환불
     */
    private void compensatePayment(SagaState state) {
        if (state.paymentId != null) {
            paymentService.refund(state.paymentId);
        }
    }

    /**
     * Saga 상태 저장
     */
    private static class SagaState {
        OrderId orderId;
        ReservationId inventoryReservationId;
        PaymentId paymentId;
        ShippingId shippingId;
        CreateOrderCommand command;

        SagaState(CreateOrderCommand cmd) {
            this.command = cmd;
        }
    }
}
```

**Orchestration 특징**:
- ✅ 중앙 집중식 제어 (흐름 파악 쉬움)
- ✅ 명시적 보상 로직
- ❌ Orchestrator가 모든 서비스 의존 (결합도 높음)

---

## ✅ Saga Pattern - Choreography

### 패턴: Event 기반 분산 조율

```java
package com.company.application.saga;

/**
 * Order Saga - Choreography 방식
 *
 * - 각 서비스가 Event를 발행/구독
 * - 중앙 Orchestrator 없음 (분산 제어)
 *
 * @author development-team
 * @since 1.0.0
 */

/**
 * Step 1: Order Service
 */
@Service
public class OrderService {

    @Transactional
    public OrderId createOrder(CreateOrderCommand cmd) {
        Order order = Order.create(cmd);
        orderRepository.save(order);

        // ✅ Event 발행 (다음 단계 트리거)
        eventPublisher.publish(new OrderCreated(order.getId(), cmd.items()));

        return order.getId();
    }
}

/**
 * Step 2: Inventory Service (Event 구독)
 */
@Component
public class InventoryEventHandler {

    /**
     * ✅ OrderCreated Event 수신 → Inventory 예약
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreated event) {
        try {
            ReservationId reservationId = inventoryService.reserveStock(event.items());

            // ✅ 성공 Event 발행
            eventPublisher.publish(new InventoryReserved(event.orderId(), reservationId));

        } catch (InventoryNotAvailableException e) {
            // ⚠️ 실패 Event 발행
            eventPublisher.publish(new InventoryReservationFailed(event.orderId(), e.getMessage()));
        }
    }

    /**
     * ✅ PaymentFailed Event 수신 → 보상 트랜잭션
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailed event) {
        inventoryService.cancelReservation(event.orderId());
    }
}

/**
 * Step 3: Payment Service (Event 구독)
 */
@Component
public class PaymentEventHandler {

    /**
     * ✅ InventoryReserved Event 수신 → Payment 처리
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInventoryReserved(InventoryReserved event) {
        try {
            PaymentId paymentId = paymentService.processPayment(event.orderId());

            // ✅ 성공 Event 발행
            eventPublisher.publish(new PaymentCompleted(event.orderId(), paymentId));

        } catch (PaymentFailedException e) {
            // ⚠️ 실패 Event 발행
            eventPublisher.publish(new PaymentFailed(event.orderId(), e.getMessage()));
        }
    }
}

/**
 * Step 4: Order Service (Event 구독 - 최종 상태 업데이트)
 */
@Component
public class OrderSagaEventHandler {

    /**
     * ✅ PaymentCompleted Event 수신 → Order 완료
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompleted event) {
        Order order = orderRepository.findById(event.orderId()).orElseThrow();
        order.markAsCompleted();
        orderRepository.save(order);
    }

    /**
     * ⚠️ InventoryReservationFailed Event 수신 → Order 취소
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInventoryReservationFailed(InventoryReservationFailed event) {
        Order order = orderRepository.findById(event.orderId()).orElseThrow();
        order.cancel("Inventory not available");
        orderRepository.save(order);
    }
}
```

**Choreography 특징**:
- ✅ 낮은 결합도 (각 서비스 독립적)
- ✅ 확장 용이 (새 서비스 추가 시 Event만 구독)
- ❌ 전체 흐름 파악 어려움 (Event 추적 필요)
- ❌ 순환 의존 위험 (Event 체이닝)

---

## 🎯 실전 예제: Saga State Machine

### ✅ Example: 상태 기반 Saga

```java
/**
 * Saga State - Event Sourcing 통합
 *
 * @author development-team
 * @since 1.0.0
 */
public enum SagaStatus {
    STARTED,
    ORDER_CREATED,
    INVENTORY_RESERVED,
    PAYMENT_COMPLETED,
    SHIPPING_SCHEDULED,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED
}

@Entity
@Table(name = "saga_instances")
public class SagaInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sagaType;  // OrderSaga, PaymentSaga

    @Column(nullable = false)
    private String aggregateId;  // OrderId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;

    /**
     * 현재 단계 (Step 번호)
     */
    @Column(nullable = false)
    private Integer currentStep;

    /**
     * Saga 상태 (JSON)
     */
    @Column(columnDefinition = "JSONB")
    private String stateData;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant completedAt;

    /**
     * ✅ 다음 단계로 진행
     */
    public void advanceToStep(Integer step, SagaStatus status) {
        this.currentStep = step;
        this.status = status;
    }

    /**
     * ⚠️ 실패 → 보상 단계로
     */
    public void failAndCompensate() {
        this.status = SagaStatus.COMPENSATING;
    }
}

/**
 * Saga Manager
 */
@Service
public class SagaManager {

    /**
     * ✅ Saga 진행 상황 모니터링
     */
    public void trackSaga(OrderId orderId, SagaStatus status) {
        SagaInstance saga = sagaRepository.findByAggregateId(orderId.value())
            .orElseGet(() -> createNewSaga("OrderSaga", orderId.value()));

        saga.advanceToStep(saga.getCurrentStep() + 1, status);
        sagaRepository.save(saga);
    }

    /**
     * ⚠️ Timeout 처리 (5분 이상 진행 중인 Saga 보상)
     */
    @Scheduled(fixedRate = 60000)  // 1분마다 실행
    public void compensateTimeoutSagas() {
        Instant fiveMinutesAgo = Instant.now().minus(Duration.ofMinutes(5));

        List<SagaInstance> timeoutSagas = sagaRepository
            .findByStatusInAndStartedAtBefore(
                List.of(SagaStatus.STARTED, SagaStatus.ORDER_CREATED), fiveMinutesAgo
            );

        for (SagaInstance saga : timeoutSagas) {
            compensateSaga(saga);
        }
    }
}
```

---

## 📋 Saga Pattern 체크리스트

### 설계
- [ ] Orchestration vs Choreography 선택
- [ ] 보상 트랜잭션 정의 (각 단계별)
- [ ] Saga 상태 저장 테이블 설계

### 구현
- [ ] Idempotent Operations (중복 실행 방지)
- [ ] Timeout 처리 (Saga 진행 모니터링)
- [ ] Event Ordering 보장

### 테스트
- [ ] 각 단계별 실패 시나리오 테스트
- [ ] 보상 트랜잭션 검증
- [ ] 동시성 처리 (동일 Saga 중복 실행)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
