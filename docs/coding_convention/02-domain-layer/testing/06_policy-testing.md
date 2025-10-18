# Policy Testing - 도메인 정책 객체 테스트

**목적**: Domain Policy의 비즈니스 규칙과 전략 로직을 검증

**관련 문서**:
- [Domain Package Guide](../package-guide/01_domain_package_guide.md)
- [Value Object Testing](02_value-object-testing.md)

**검증 도구**: JUnit 5, AssertJ, JUnit Params (Parameterized Tests)

---

## 📌 핵심 원칙

### Policy 테스트 특징

1. **비즈니스 규칙 검증**: 도메인 정책의 결정 로직
2. **전략 패턴 테스트**: 여러 정책 구현체 검증
3. **경계값 테스트**: Edge Case와 Boundary 검증
4. **Stateless 검증**: 정책 객체는 상태를 가지지 않음

---

## ✅ Policy 테스트 패턴

### 패턴 1: 단순 정책 테스트

```java
package com.company.domain.order.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * FreeShippingPolicy 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class FreeShippingPolicyTest {

    private final FreeShippingPolicy policy = new FreeShippingPolicy();

    @Test
    void isEligible_WhenOrderAboveThreshold_ShouldReturnTrue() {
        // Given
        Money orderAmount = Money.of(50000);

        // When
        boolean eligible = policy.isEligible(orderAmount);

        // Then
        assertThat(eligible).isTrue();
    }

    @Test
    void isEligible_WhenOrderBelowThreshold_ShouldReturnFalse() {
        // Given
        Money orderAmount = Money.of(30000);

        // When
        boolean eligible = policy.isEligible(orderAmount);

        // Then
        assertThat(eligible).isFalse();
    }

    @Test
    void isEligible_WhenExactlyAtThreshold_ShouldReturnTrue() {
        // Given - 경계값 테스트
        Money orderAmount = Money.of(50000);

        // When
        boolean eligible = policy.isEligible(orderAmount);

        // Then
        assertThat(eligible).isTrue();
    }

    @Test
    void isEligible_WithNullAmount_ShouldThrowException() {
        assertThatThrownBy(() -> policy.isEligible(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order amount must not be null");
    }
}
```

---

### 패턴 2: Parameterized Test (여러 시나리오)

```java
package com.company.domain.discount.policy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * VolumeDiscountPolicy 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class VolumeDiscountPolicyTest {

    private final VolumeDiscountPolicy policy = new VolumeDiscountPolicy();

    @ParameterizedTest
    @CsvSource({
        "1, 0",      // 1개: 할인 없음
        "5, 0",      // 5개: 할인 없음
        "10, 5",     // 10개: 5% 할인
        "50, 10",    // 50개: 10% 할인
        "100, 15"    // 100개: 15% 할인
    })
    void calculateDiscountRate_WithVariousQuantities(int quantity, int expectedRate) {
        // When
        int discountRate = policy.calculateDiscountRate(Quantity.of(quantity));

        // Then
        assertThat(discountRate).isEqualTo(expectedRate);
    }

    @Test
    void calculateDiscountAmount_WithQuantity50_ShouldApply10PercentDiscount() {
        // Given
        Quantity quantity = Quantity.of(50);
        Money unitPrice = Money.of(1000);

        // When
        Money discount = policy.calculateDiscountAmount(quantity, unitPrice);

        // Then
        Money expected = Money.of(5000); // 50 * 1000 * 10% = 5000
        assertThat(discount).isEqualTo(expected);
    }
}
```

---

### 패턴 3: 전략 패턴 테스트

```java
package com.company.domain.shipping.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * ShippingCostCalculationPolicy 전략 패턴 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class ShippingCostCalculationPolicyTest {

    @Test
    void flatRatePolicy_ShouldReturnFixedCost() {
        // Given
        ShippingCostPolicy policy = new FlatRateShippingPolicy(Money.of(3000));
        Order order = Order.create(CustomerId.of(1L));

        // When
        Money cost = policy.calculate(order);

        // Then
        assertThat(cost).isEqualTo(Money.of(3000));
    }

    @Test
    void weightBasedPolicy_ShouldCalculateByWeight() {
        // Given
        ShippingCostPolicy policy = new WeightBasedShippingPolicy(Money.of(100)); // 100원/kg
        Order order = Order.createWithWeight(CustomerId.of(1L), Weight.of(5)); // 5kg

        // When
        Money cost = policy.calculate(order);

        // Then
        assertThat(cost).isEqualTo(Money.of(500)); // 5kg * 100원
    }

    @Test
    void distanceBasedPolicy_ShouldCalculateByDistance() {
        // Given
        ShippingCostPolicy policy = new DistanceBasedShippingPolicy(Money.of(50)); // 50원/km
        Order order = Order.createWithDistance(CustomerId.of(1L), Distance.of(100)); // 100km

        // When
        Money cost = policy.calculate(order);

        // Then
        assertThat(cost).isEqualTo(Money.of(5000)); // 100km * 50원
    }
}
```

---

### 패턴 4: 복합 정책 테스트 (정책 조합)

```java
package com.company.domain.pricing.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * CompositePricingPolicy 테스트 (여러 정책 조합)
 *
 * @author development-team
 * @since 1.0.0
 */
class CompositePricingPolicyTest {

    @Test
    void calculate_WithMultiplePolicies_ShouldApplyAllDiscounts() {
        // Given
        PricingPolicy basePolicy = new BasePricingPolicy();
        PricingPolicy volumeDiscount = new VolumeDiscountPolicy();
        PricingPolicy memberDiscount = new MemberDiscountPolicy();

        CompositePricingPolicy policy = new CompositePricingPolicy(
            List.of(basePolicy, volumeDiscount, memberDiscount)
        );

        Order order = Order.create(CustomerId.of(1L));
        order.addItem(ProductId.of(101L), Quantity.of(100), Money.of(1000));

        // When
        Money finalPrice = policy.calculate(order);

        // Then
        // Base: 100 * 1000 = 100,000
        // Volume Discount (15%): -15,000
        // Member Discount (10%): -8,500 (85,000의 10%)
        assertThat(finalPrice).isEqualTo(Money.of(76500));
    }

    @Test
    void calculate_WithNoApplicablePolicies_ShouldReturnBasePrice() {
        // Given
        CompositePricingPolicy policy = new CompositePricingPolicy(List.of());
        Order order = Order.create(CustomerId.of(1L));
        order.addItem(ProductId.of(101L), Quantity.of(1), Money.of(1000));

        // When
        Money finalPrice = policy.calculate(order);

        // Then
        assertThat(finalPrice).isEqualTo(Money.of(1000));
    }
}
```

---

### 패턴 5: 조건부 정책 테스트

```java
package com.company.domain.coupon.policy;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * TimeLimitedCouponPolicy 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class TimeLimitedCouponPolicyTest {

    @Test
    void isValid_WithinValidPeriod_ShouldReturnTrue() {
        // Given
        LocalDateTime validFrom = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime validUntil = LocalDateTime.of(2025, 10, 31, 23, 59);
        TimeLimitedCouponPolicy policy = new TimeLimitedCouponPolicy(validFrom, validUntil);

        LocalDateTime now = LocalDateTime.of(2025, 10, 15, 12, 0);

        // When
        boolean valid = policy.isValid(now);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void isValid_BeforeValidPeriod_ShouldReturnFalse() {
        // Given
        LocalDateTime validFrom = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime validUntil = LocalDateTime.of(2025, 10, 31, 23, 59);
        TimeLimitedCouponPolicy policy = new TimeLimitedCouponPolicy(validFrom, validUntil);

        LocalDateTime now = LocalDateTime.of(2025, 9, 30, 23, 59);

        // When
        boolean valid = policy.isValid(now);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void isValid_AfterValidPeriod_ShouldReturnFalse() {
        // Given
        LocalDateTime validFrom = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime validUntil = LocalDateTime.of(2025, 10, 31, 23, 59);
        TimeLimitedCouponPolicy policy = new TimeLimitedCouponPolicy(validFrom, validUntil);

        LocalDateTime now = LocalDateTime.of(2025, 11, 1, 0, 0);

        // When
        boolean valid = policy.isValid(now);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void isValid_ExactlyAtStartTime_ShouldReturnTrue() {
        // Given - 경계값 테스트
        LocalDateTime validFrom = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime validUntil = LocalDateTime.of(2025, 10, 31, 23, 59);
        TimeLimitedCouponPolicy policy = new TimeLimitedCouponPolicy(validFrom, validUntil);

        // When
        boolean valid = policy.isValid(validFrom);

        // Then
        assertThat(valid).isTrue();
    }
}
```

---

### 패턴 6: Stateless 검증

```java
/**
 * Policy Stateless 검증 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class PolicyStatelessTest {

    @Test
    void policy_ShouldBeStateless_NoSideEffects() {
        // Given
        FreeShippingPolicy policy = new FreeShippingPolicy();
        Money amount1 = Money.of(50000);
        Money amount2 = Money.of(30000);

        // When - 여러 번 호출
        boolean result1 = policy.isEligible(amount1);
        boolean result2 = policy.isEligible(amount2);
        boolean result3 = policy.isEligible(amount1); // 같은 입력 재호출

        // Then - 결과가 항상 동일 (Stateless)
        assertThat(result1).isTrue();
        assertThat(result2).isFalse();
        assertThat(result3).isTrue(); // 같은 입력 → 같은 결과
    }

    @Test
    void policy_ShouldNotMaintainState() {
        // Given
        VolumeDiscountPolicy policy = new VolumeDiscountPolicy();

        // When
        int rate1 = policy.calculateDiscountRate(Quantity.of(10));
        int rate2 = policy.calculateDiscountRate(Quantity.of(50));

        // Then - 이전 호출이 다음 호출에 영향을 주지 않음
        assertThat(rate1).isEqualTo(5);
        assertThat(rate2).isEqualTo(10);

        // 다시 첫 번째 값으로 호출해도 같은 결과
        int rate3 = policy.calculateDiscountRate(Quantity.of(10));
        assertThat(rate3).isEqualTo(5);
    }
}
```

---

## 📋 Policy 테스트 체크리스트

- [ ] 비즈니스 규칙 검증 (정상 케이스)
- [ ] 경계값 테스트 (Boundary Case)
- [ ] Edge Case (Null, Zero, Negative)
- [ ] Stateless 검증 (부작용 없음)
- [ ] 전략 패턴 (여러 구현체 검증)
- [ ] 복합 정책 (정책 조합)
- [ ] Parameterized Test (여러 시나리오)

---

## 🔗 Testing Support Toolkit 연계

**`00_testing-support-toolkit.md` 활용:**

```java
// ClockFixtures 사용 (시간 기반 정책)
@Test
void isValid_WithFixedClock_ShouldBeConsistent() {
    Clock clock = ClockFixtures.fixedAt("2025-10-16T10:00:00Z");

    TimeLimitedCouponPolicy policy = new TimeLimitedCouponPolicy(
        LocalDateTime.of(2025, 10, 1, 0, 0),
        LocalDateTime.of(2025, 10, 31, 23, 59)
    );

    boolean valid = policy.isValid(LocalDateTime.now(clock));

    assertThat(valid).isTrue();
}
```

---

## 🚫 Anti-Pattern

### ❌ Policy에 상태를 저장하지 말 것

```java
// ❌ Stateful Policy (금지)
public class BadDiscountPolicy {
    private int callCount = 0; // ❌ 상태 저장 금지!

    public Money calculate(Money amount) {
        callCount++; // ❌ 부작용 금지!
        return amount.multiply(0.9);
    }
}
```

**올바른 방법**: Policy는 항상 Stateless, 입력만으로 결과 결정

---

## 📊 Policy vs Value Object 비교

| 구분 | Policy | Value Object |
|------|--------|--------------|
| **목적** | 비즈니스 규칙 결정 | 데이터 표현 |
| **상태** | Stateless | Immutable |
| **메서드** | 규칙 평가 | 값 연산 |
| **예시** | `DiscountPolicy` | `Money`, `Quantity` |

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
