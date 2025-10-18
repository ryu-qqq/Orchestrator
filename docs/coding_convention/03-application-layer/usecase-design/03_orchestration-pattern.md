# Orchestration Pattern - 복잡한 UseCase 조율

**목적**: 여러 Aggregate 조율 패턴

**관련 문서**:
- [Saga Pattern](../../07-enterprise-patterns/event-driven/03_saga-pattern.md)
- [Domain Events](../../07-enterprise-patterns/event-driven/01_domain-events.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### Orchestration이란?

1. **여러 Aggregate 조율**: 여러 UseCase 호출
2. **트랜잭션 분리**: Aggregate별 독립 트랜잭션
3. **보상 트랜잭션**: 실패 시 Rollback 대신 보상

---

## ❌ 거대한 트랜잭션

```java
// ❌ Before - 여러 Aggregate 동시 수정
@Service
@Transactional
public class OrderService {

    public void createOrder(CreateOrderCommand command) {
        Order order = orderRepository.save(Order.create(command));

        Inventory inventory = inventoryRepository.findByProductId(...);
        inventory.decrease(...);  // ❌ Lock

        Payment payment = paymentRepository.save(Payment.create(...));  // ❌ Lock
    }
}
```

---

## ✅ Orchestration 패턴

```java
/**
 * Order Orchestrator
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderOrchestrator {

    private final CreateOrderUseCase createOrderUseCase;
    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final ProcessPaymentUseCase processPaymentUseCase;

    /**
     * ✅ 여러 UseCase 조율
     */
    public OrderId orchestrateOrderCreation(CreateOrderCommand command) {
        // 1. Order 생성 (독립 트랜잭션)
        OrderId orderId = createOrderUseCase.createOrder(command);

        try {
            // 2. Inventory 예약 (독립 트랜잭션)
            ReservationId reservationId = reserveInventoryUseCase.reserve(
                new ReserveInventoryCommand(orderId, command.items())
            );

            // 3. Payment 처리 (독립 트랜잭션)
            PaymentId paymentId = processPaymentUseCase.process(
                new ProcessPaymentCommand(orderId, command.amount())
            );

            return orderId;

        } catch (Exception e) {
            // ⚠️ 보상 트랜잭션
            cancelOrder(orderId);
            throw new OrderCreationFailedException(e);
        }
    }

    private void cancelOrder(OrderId orderId) {
        // 보상 트랜잭션 로직
    }
}
```

---

## 📋 Orchestration 체크리스트

- [ ] UseCase별 독립 트랜잭션인가?
- [ ] 보상 트랜잭션 정의되어 있는가?
- [ ] Saga Pattern 고려했는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
