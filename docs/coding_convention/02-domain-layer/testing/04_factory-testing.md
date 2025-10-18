# Factory Testing - 도메인 객체 생성 로직 테스트

**목적**: Factory의 복잡한 생성 로직과 불변식 보장을 검증

**관련 문서**:
- [Domain Encapsulation](../law-of-demeter/03_domain-encapsulation.md)
- [Aggregate Root Design](../aggregate-design/02_aggregate-root-design.md)

**검증 도구**: JUnit 5, AssertJ

---

## 📌 핵심 원칙

### Factory 테스트 특징

1. **복잡한 생성 로직**: 여러 VO를 조합하여 Aggregate 생성
2. **불변식 검증**: 생성 시점에 도메인 규칙 보장
3. **유효성 검사**: Invalid 입력에 대한 예외 처리
4. **정적 팩토리 vs Factory 클래스**: 둘 다 테스트

---

## ✅ Factory 테스트 패턴

### 패턴 1: 정적 팩토리 메서드 테스트

```java
package com.company.domain.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Order 정적 팩토리 메서드 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderFactoryMethodTest {

    @Test
    void create_WithValidInput_ShouldInitializeOrder() {
        // Given
        CustomerId customerId = CustomerId.of(1L);

        // When
        Order order = Order.create(customerId);

        // Then
        assertThat(order.getId()).isNotNull();
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getLines()).isEmpty();
        assertThat(order.getTotalPrice()).isEqualTo(Money.zero());
    }

    @Test
    void create_WithItems_ShouldCalculateTotal() {
        // Given
        CustomerId customerId = CustomerId.of(1L);
        List<OrderLineItem> items = List.of(
            OrderLineItem.of(ProductId.of(101L), Quantity.of(2), Money.of(1000)),
            OrderLineItem.of(ProductId.of(102L), Quantity.of(1), Money.of(500))
        );

        // When
        Order order = Order.createWithItems(customerId, items);

        // Then
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getTotalPrice()).isEqualTo(Money.of(2500));
    }

    @Test
    void create_WithNullCustomerId_ShouldThrowException() {
        assertThatThrownBy(() -> Order.create(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Customer ID must not be null");
    }
}
```

---

### 패턴 2: Factory 클래스 테스트 (복잡한 생성 로직)

```java
package com.company.domain.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderFactory 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderFactoryTest {

    private OrderFactory orderFactory;

    @BeforeEach
    void setUp() {
        orderFactory = new OrderFactory();
    }

    @Test
    void createFromCart_WithValidCart_ShouldCreateOrder() {
        // Given
        ShoppingCart cart = ShoppingCart.create(CustomerId.of(1L));
        cart.addItem(ProductId.of(101L), Quantity.of(2), Money.of(1000));
        cart.addItem(ProductId.of(102L), Quantity.of(1), Money.of(500));

        ShippingAddress address = ShippingAddress.of(
            "Seoul", "Gangnam-gu", "12345"
        );

        // When
        Order order = orderFactory.createFromCart(cart, address);

        // Then
        assertThat(order.getCustomerId()).isEqualTo(cart.getCustomerId());
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getShippingAddress()).isEqualTo(address);
        assertThat(order.getTotalPrice()).isEqualTo(Money.of(2500));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void createFromCart_WithEmptyCart_ShouldThrowException() {
        // Given
        ShoppingCart emptyCart = ShoppingCart.create(CustomerId.of(1L));
        ShippingAddress address = ShippingAddress.of("Seoul", "Gangnam-gu", "12345");

        // When & Then
        assertThatThrownBy(() -> orderFactory.createFromCart(emptyCart, address))
            .isInstanceOf(EmptyCartException.class)
            .hasMessageContaining("Cannot create order from empty cart");
    }

    @Test
    void createFromCart_WithInvalidAddress_ShouldThrowException() {
        // Given
        ShoppingCart cart = ShoppingCart.create(CustomerId.of(1L));
        cart.addItem(ProductId.of(101L), Quantity.of(1), Money.of(1000));

        // When & Then
        assertThatThrownBy(() -> orderFactory.createFromCart(cart, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Shipping address must not be null");
    }
}
```

---

### 패턴 3: 복잡한 Aggregate 재구성 (from Persistence)

```java
/**
 * OrderFactory - Persistence에서 재구성
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderFactoryReconstitutionTest {

    private OrderFactory orderFactory;

    @BeforeEach
    void setUp() {
        orderFactory = new OrderFactory();
    }

    @Test
    void reconstitute_WithValidData_ShouldRecreateOrder() {
        // Given - Persistence 데이터 시뮬레이션
        OrderId orderId = OrderId.of(1L);
        CustomerId customerId = CustomerId.of(1L);
        List<OrderLineData> lineData = List.of(
            new OrderLineData(ProductId.of(101L), Quantity.of(2), Money.of(1000)),
            new OrderLineData(ProductId.of(102L), Quantity.of(1), Money.of(500))
        );
        OrderStatus status = OrderStatus.APPROVED;
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);

        // When
        Order order = orderFactory.reconstitute(
            orderId, customerId, lineData, status, createdAt
        );

        // Then
        assertThat(order.getId()).isEqualTo(orderId);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getTotalPrice()).isEqualTo(Money.of(2500));
        assertThat(order.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void reconstitute_WithInvalidTotal_ShouldThrowException() {
        // Given - 불변식 위반: 라인 합계 ≠ 주문 합계
        OrderId orderId = OrderId.of(1L);
        CustomerId customerId = CustomerId.of(1L);
        List<OrderLineData> lineData = List.of(
            new OrderLineData(ProductId.of(101L), Quantity.of(2), Money.of(1000)),
            new OrderLineData(ProductId.of(102L), Quantity.of(1), Money.of(500))
        );
        Money invalidTotal = Money.of(9999); // 잘못된 합계

        // When & Then
        assertThatThrownBy(() -> orderFactory.reconstituteWithTotal(
            orderId, customerId, lineData, OrderStatus.APPROVED, invalidTotal
        ))
            .isInstanceOf(InvariantViolationException.class)
            .hasMessageContaining("Total price must equal sum of line prices");
    }
}
```

---

### 패턴 4: Builder Pattern과 Factory 조합

```java
/**
 * OrderBuilder 테스트 (Fluent Factory)
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderBuilderTest {

    @Test
    void build_WithFluentAPI_ShouldCreateOrder() {
        // Given & When
        Order order = Order.builder()
            .customerId(CustomerId.of(1L))
            .addItem(ProductId.of(101L), Quantity.of(2), Money.of(1000))
            .addItem(ProductId.of(102L), Quantity.of(1), Money.of(500))
            .shippingAddress(ShippingAddress.of("Seoul", "Gangnam-gu", "12345"))
            .build();

        // Then
        assertThat(order.getCustomerId()).isEqualTo(CustomerId.of(1L));
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getTotalPrice()).isEqualTo(Money.of(2500));
        assertThat(order.getShippingAddress()).isNotNull();
    }

    @Test
    void build_WithoutMandatoryFields_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> Order.builder()
            .addItem(ProductId.of(101L), Quantity.of(1), Money.of(1000))
            .build()) // customerId 누락
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Customer ID is required");
    }
}
```

---

## 📋 Factory 테스트 체크리스트

- [ ] 정적 팩토리 메서드 테스트 (create, of, from)
- [ ] Factory 클래스 복잡한 생성 로직 테스트
- [ ] Persistence 재구성 테스트 (reconstitute)
- [ ] 불변식 보장 검증 (생성 시점 검증)
- [ ] Invalid 입력 예외 처리
- [ ] Builder Pattern 테스트 (선택적)

---

## 🔗 Testing Support Toolkit 연계

**`00_testing-support-toolkit.md` 활용:**

```java
// IdGeneratorFake 사용
@Test
void createMultipleOrders_ShouldGenerateSequentialIds() {
    IdGenerator idGen = new SequentialIdGeneratorFake("order-", 1);

    Order order1 = orderFactory.create(CustomerId.of(1L), idGen);
    Order order2 = orderFactory.create(CustomerId.of(2L), idGen);

    assertThat(order1.getId().value()).isEqualTo("order-1");
    assertThat(order2.getId().value()).isEqualTo("order-2");
}

// ClockFixtures 사용
@Test
void createOrder_ShouldUseProvidedTimestamp() {
    Clock clock = ClockFixtures.fixedAt("2025-10-16T10:00:00Z");

    Order order = orderFactory.create(CustomerId.of(1L), clock);

    assertThat(order.getCreatedAt()).isEqualTo(clock.instant());
}
```

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
