# Value Objects with Records - DDD Value Object를 Record로 구현

**목적**: Java 21 Record로 불변 Value Object를 구현하고 Compact Constructor로 Validation 강화

**관련 문서**:
- [DTO with Records](./01_dto-with-records.md)
- [Value Object Testing](../../05-testing/domain-testing/02_value-object-testing.md)

**필수 버전**: Java 21+

---

## 📌 핵심 원칙

### Value Object 특징

1. **불변성**: 생성 후 상태 변경 불가
2. **값 동등성**: ID가 아닌 속성으로 비교
3. **자가 검증**: Compact Constructor에서 Validation
4. **Side-Effect Free**: 연산 메서드는 새 인스턴스 반환

---

## ✅ Value Object Record 패턴

### 패턴 1: Simple Value Object (ID)

```java
package com.company.domain.order;

/**
 * Order ID Value Object (Record)
 *
 * @param value 주문 ID (양수)
 * @author development-team
 * @since 1.0.0
 */
public record OrderId(Long value) {
    /**
     * Compact Constructor (Validation)
     */
    public OrderId {
        if (value == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (value <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
    }

    /**
     * 정적 팩토리 메서드
     */
    public static OrderId of(Long value) {
        return new OrderId(value);
    }

    /**
     * 새 ID 생성 (UUID 기반)
     */
    public static OrderId generate() {
        return new OrderId(System.currentTimeMillis()); // 실제는 UUID 사용
    }
}
```

**핵심 기능**:
- ✅ Compact Constructor에서 Null, Negative 검증
- ✅ `of()` 정적 팩토리 메서드
- ✅ `generate()` 새 ID 생성

---

### 패턴 2: Money Value Object (연산 메서드)

```java
package com.company.domain.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money Value Object (Record)
 *
 * @param amount 금액 (음수 불가)
 * @author development-team
 * @since 1.0.0
 */
public record Money(BigDecimal amount) {
    /**
     * Compact Constructor (Validation + Normalization)
     */
    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        // 소수점 2자리로 정규화
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    /**
     * 덧셈 (새 인스턴스 반환 - 불변성 유지)
     */
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    /**
     * 뺄셈
     */
    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    /**
     * 곱셈
     */
    public Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /**
     * 비교
     */
    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        return this.amount.compareTo(other.amount) < 0;
    }
}
```

**핵심 패턴**:
- ✅ Normalization (소수점 정규화)
- ✅ 연산 메서드 (add, subtract, multiply)
- ✅ 비교 메서드 (isGreaterThan, isLessThan)
- ✅ 불변성 유지 (새 인스턴스 반환)

---

### 패턴 3: Quantity Value Object

```java
package com.company.domain.order;

/**
 * Quantity Value Object (Record)
 *
 * @param value 수량 (1 이상)
 * @author development-team
 * @since 1.0.0
 */
public record Quantity(int value) {
    /**
     * Compact Constructor (Validation)
     */
    public Quantity {
        if (value <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    /**
     * 수량 증가
     */
    public Quantity increase(int amount) {
        return new Quantity(this.value + amount);
    }

    /**
     * 수량 감소
     */
    public Quantity decrease(int amount) {
        int newValue = this.value - amount;
        if (newValue < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative after decrease");
        }
        return new Quantity(newValue);
    }
}
```

---

## 🎯 실전 예제: Aggregate에서 Value Object 활용

### ✅ Example: Order Aggregate

```java
package com.company.domain.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order Aggregate Root (Value Objects 활용)
 *
 * @author development-team
 * @since 1.0.0
 */
public class Order {
    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderLineItem> items;
    private final Instant createdAt;

    private Order(OrderId id, CustomerId customerId) {
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.PENDING;
        this.items = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    public static Order create(CustomerId customerId) {
        return new Order(OrderId.generate(), customerId);
    }

    /**
     * 주문 항목 추가
     */
    public void addItem(ProductId productId, Quantity quantity, Money price) {
        OrderLineItem item = OrderLineItem.of(productId, quantity, price);
        items.add(item);
    }

    /**
     * 총 가격 계산 (Value Object 연산 활용)
     */
    public Money getTotalPrice() {
        return items.stream()
            .map(OrderLineItem::getSubtotal)
            .reduce(Money.zero(), Money::add);
    }

    /**
     * 주문 승인
     */
    public void approve() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be approved");
        }
        this.status = OrderStatus.APPROVED;
    }

    // Getters
    public OrderId getId() { return id; }
    public CustomerId getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public Money getTotalPrice() { return getTotalPrice(); }
}
```

**Value Object 활용**:
- ✅ `OrderId`, `CustomerId`, `ProductId` (Type-Safe ID)
- ✅ `Money` (금액 연산)
- ✅ `Quantity` (수량 연산)

---

### ✅ Example: OrderLineItem Value Object

```java
package com.company.domain.order;

/**
 * Order Line Item Value Object (Record)
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderLineItem(
    ProductId productId,
    Quantity quantity,
    Money price
) {
    /**
     * Compact Constructor (Validation)
     */
    public OrderLineItem {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID must not be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity must not be null");
        }
        if (price == null) {
            throw new IllegalArgumentException("Price must not be null");
        }
    }

    public static OrderLineItem of(ProductId productId, Quantity quantity, Money price) {
        return new OrderLineItem(productId, quantity, price);
    }

    /**
     * 소계 계산 (Value Object 연산 활용)
     */
    public Money getSubtotal() {
        return price.multiply(quantity.value());
    }
}
```

---

## 🔧 고급 Value Object 패턴

### 패턴 1: Range Value Object

```java
/**
 * Date Range Value Object (Record)
 *
 * @author development-team
 * @since 1.0.0
 */
public record DateRange(LocalDate start, LocalDate end) {
    /**
     * Compact Constructor (Validation)
     */
    public DateRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates must not be null");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
    }

    public static DateRange of(LocalDate start, LocalDate end) {
        return new DateRange(start, end);
    }

    /**
     * 날짜가 범위 내에 있는지 확인
     */
    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * 기간 (일수)
     */
    public long getDays() {
        return ChronoUnit.DAYS.between(start, end);
    }
}
```

---

### 패턴 2: Email Value Object (Regex Validation)

```java
import java.util.regex.Pattern;

/**
 * Email Value Object (Record)
 *
 * @author development-team
 * @since 1.0.0
 */
public record Email(String value) {
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Compact Constructor (Regex Validation)
     */
    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email must not be null or blank");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }
        value = value.toLowerCase(); // 정규화
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
```

---

## 📋 Value Object Record 체크리스트

### 기본 규칙
- [ ] Record로 선언 (불변성)
- [ ] Compact Constructor에서 Validation
- [ ] `of()` 정적 팩토리 메서드
- [ ] Javadoc 작성

### 연산 메서드
- [ ] 새 인스턴스 반환 (불변성 유지)
- [ ] Side-Effect 없음
- [ ] 도메인 의미를 가진 메서드명

### 검증 규칙
- [ ] Null 체크
- [ ] Range 체크 (Min/Max)
- [ ] Format 체크 (Regex)
- [ ] Business Rule 체크

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
