# Value Object Testing - 불변 객체 테스트

**목적**: Value Object의 불변성, Equals/HashCode, Validation을 테스트

**관련 문서**:
- [Java Record Guide](../../../../JAVA_RECORD_GUIDE.md)
- [Entity Immutability](../../04-persistence-layer/jpa-entity-design/02_entity-immutability.md)

**검증 도구**: JUnit 5, AssertJ

---

## 📌 핵심 원칙

### Value Object 특징

1. **불변성**: 생성 후 상태 변경 불가
2. **값 동등성**: ID가 아닌 속성으로 비교
3. **자가 검증**: Constructor에서 Validation
4. **Record 권장**: Java 21 Record 활용

---

## ✅ Value Object 테스트 패턴

### 패턴 1: 불변성 검증 (Record)

```java
package com.company.domain.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Money Value Object 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class MoneyTest {

    @Test
    void createMoney_ShouldBeImmutable() {
        Money money = Money.of(1000);

        assertThat(money.amount()).isEqualTo(1000);
        // Record는 Setter 없음 - 불변성 보장
    }

    @Test
    void add_ShouldReturnNewInstance() {
        Money money1 = Money.of(1000);
        Money money2 = Money.of(500);

        Money result = money1.add(money2);

        assertThat(result).isNotSameAs(money1);
        assertThat(result.amount()).isEqualTo(1500);
        assertThat(money1.amount()).isEqualTo(1000); // 원본 불변
    }

    @Test
    void equals_WithSameAmount_ShouldBeEqual() {
        Money money1 = Money.of(1000);
        Money money2 = Money.of(1000);

        assertThat(money1).isEqualTo(money2);
    }

    @Test
    void hashCode_WithSameAmount_ShouldBeSame() {
        Money money1 = Money.of(1000);
        Money money2 = Money.of(1000);

        assertThat(money1.hashCode()).isEqualTo(money2.hashCode());
    }
}
```

---

### 패턴 2: Validation 테스트

```java
/**
 * CustomerId Value Object 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class CustomerIdTest {

    @Test
    void of_WithValidId_ShouldCreateInstance() {
        CustomerId customerId = CustomerId.of(1L);

        assertThat(customerId.value()).isEqualTo(1L);
    }

    @Test
    void of_WithNullId_ShouldThrowException() {
        assertThatThrownBy(() -> CustomerId.of(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Customer ID must not be null");
    }

    @Test
    void of_WithNegativeId_ShouldThrowException() {
        assertThatThrownBy(() -> CustomerId.of(-1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Customer ID must be positive");
    }
}
```

---

### 패턴 3: Collection Value Object 테스트

```java
/**
 * OrderLineItems Value Object 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderLineItemsTest {

    @Test
    void create_WithValidItems_ShouldInitialize() {
        OrderLineItems items = OrderLineItems.of(List.of(
            OrderLineItem.of(ProductId.of(101L), Quantity.of(2)),
            OrderLineItem.of(ProductId.of(102L), Quantity.of(1))
        ));

        assertThat(items.size()).isEqualTo(2);
    }

    @Test
    void create_WithEmptyList_ShouldThrowException() {
        assertThatThrownBy(() -> OrderLineItems.of(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Items must not be empty");
    }

    @Test
    void calculateTotalQuantity_ShouldSumAllItems() {
        OrderLineItems items = OrderLineItems.of(List.of(
            OrderLineItem.of(ProductId.of(101L), Quantity.of(2)),
            OrderLineItem.of(ProductId.of(102L), Quantity.of(3))
        ));

        assertThat(items.totalQuantity()).isEqualTo(5);
    }
}
```

---

## 📋 Value Object 테스트 체크리스트

- [ ] 불변성 (Setter 없음)
- [ ] Equals/HashCode (값 기반 비교)
- [ ] Validation (Null, Negative, Empty)
- [ ] 연산 메서드 (add, subtract 등)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
