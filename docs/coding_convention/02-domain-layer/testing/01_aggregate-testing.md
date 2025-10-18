# Aggregate Testing - Aggregate 단위 테스트

**목적**: Aggregate Root의 비즈니스 로직을 순수한 단위 테스트로 검증 (Framework 의존성 없음)

**관련 문서**:
- [Aggregate Design](../aggregate-design/01_aggregate-boundaries.md)
- [Domain Encapsulation](../law-of-demeter/03_domain-encapsulation.md)

**검증 도구**: JUnit 5, AssertJ

---

## 📌 핵심 원칙

### Aggregate 테스트 특징

1. **순수 단위 테스트**: Spring, JPA 의존성 없음
2. **비즈니스 규칙 검증**: 도메인 불변식(Invariant) 보호
3. **빠른 실행**: 1,000개 테스트 < 1초
4. **Mocking 최소화**: Real Object 우선

---

## ❌ 금지 패턴

### Anti-Pattern 1: @SpringBootTest로 Aggregate 테스트

```java
// ❌ Spring Context 로드 - 순수 단위 테스트가 아님
@SpringBootTest
class OrderTest {
    @Autowired
    private OrderRepository repository; // ❌ Aggregate 테스트에 Repository 불필요

    @Test
    void createOrder() {
        Order order = Order.create(CustomerId.of(1L));
        repository.save(order); // ❌ DB 저장 로직은 통합 테스트에서
    }
}
```

**올바른 패턴**:
```java
// ✅ 순수 단위 테스트 (POJO)
class OrderTest {
    @Test
    void createOrder() {
        Order order = Order.create(CustomerId.of(1L));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
```

---

## ✅ Aggregate 테스트 패턴

### 패턴 1: 상태 전이 테스트

```java
package com.company.domain.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Order Aggregate 상태 전이 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderStateTransitionTest {

    @Test
    void createOrder_ShouldInitializeWithPendingStatus() {
        Order order = Order.create(CustomerId.of(1L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void approveOrder_WhenPending_ShouldTransitionToApproved() {
        Order order = Order.create(CustomerId.of(1L));

        order.approve();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    void approveOrder_WhenAlreadyApproved_ShouldThrowException() {
        Order order = Order.create(CustomerId.of(1L));
        order.approve();

        assertThatThrownBy(() -> order.approve())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already approved");
    }

    @Test
    void cancelOrder_WhenApproved_ShouldThrowException() {
        Order order = Order.create(CustomerId.of(1L));
        order.approve();

        assertThatThrownBy(() -> order.cancel())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot cancel approved order");
    }
}
```

---

### 패턴 2: 비즈니스 규칙 검증

```java
/**
 * Order 비즈니스 규칙 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderBusinessRuleTest {

    @Test
    void addItem_ShouldCalculateTotalPrice() {
        Order order = Order.create(CustomerId.of(1L));

        order.addItem(ProductId.of(101L), Quantity.of(2), Money.of(1000));
        order.addItem(ProductId.of(102L), Quantity.of(1), Money.of(500));

        assertThat(order.getTotalPrice()).isEqualTo(Money.of(2500));
    }

    @Test
    void addItem_WithZeroQuantity_ShouldThrowException() {
        Order order = Order.create(CustomerId.of(1L));

        assertThatThrownBy(() -> order.addItem(ProductId.of(101L), Quantity.of(0), Money.of(1000)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Quantity must be positive");
    }

    @Test
    void addItem_WithNegativePrice_ShouldThrowException() {
        Order order = Order.create(CustomerId.of(1L));

        assertThatThrownBy(() -> order.addItem(ProductId.of(101L), Quantity.of(1), Money.of(-100)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Price must be positive");
    }
}
```

---

### 패턴 3: Domain Event 발행 검증

```java
/**
 * Domain Event 발행 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderDomainEventTest {

    @Test
    void approveOrder_ShouldPublishOrderApprovedEvent() {
        Order order = Order.create(CustomerId.of(1L));

        order.approve();

        List<DomainEvent> events = order.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(OrderApprovedEvent.class);

        OrderApprovedEvent event = (OrderApprovedEvent) events.get(0);
        assertThat(event.orderId()).isEqualTo(order.getId());
    }

    @Test
    void cancelOrder_ShouldPublishOrderCancelledEvent() {
        Order order = Order.create(CustomerId.of(1L));

        order.cancel();

        assertThat(order.getDomainEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(OrderCancelledEvent.class);
    }
}
```

---

## 📋 Aggregate 테스트 체크리스트

- [ ] 상태 전이 (Create → Approve → Complete)
- [ ] 비즈니스 규칙 (가격 계산, 수량 검증)
- [ ] 불변식 보호 (Invalid State 방지)
- [ ] Domain Event 발행
- [ ] 예외 시나리오 (Null, Negative, Zero)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
