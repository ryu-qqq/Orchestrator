# Domain Modeling with Sealed Classes - 제한된 상속 계층으로 도메인 모델링

**목적**: Sealed Classes로 타입 안전한 도메인 모델 설계 및 패턴 매칭 활용

**관련 문서**:
- [Pattern Matching](../record-patterns/03_pattern-matching.md)
- [Aggregate Testing](../../05-testing/domain-testing/01_aggregate-testing.md)

**필수 버전**: Java 17+ (Sealed Classes), Java 21+ (Pattern Matching)

---

## 📌 핵심 원칙

### Sealed Classes란?

1. **제한된 상속**: 특정 클래스/인터페이스만 상속/구현 가능
2. **컴파일 타임 검증**: 모든 하위 타입을 컴파일러가 알고 있음
3. **Exhaustive Checking**: Switch 문에서 모든 케이스 처리 강제
4. **도메인 모델링**: 제한된 도메인 개념 표현에 최적

### Sealed Classes 구문

```java
// ✅ Sealed Interface
public sealed interface PaymentMethod
    permits CreditCard, BankTransfer, Cash {}

// ✅ Permitted 클래스/레코드
public final class CreditCard implements PaymentMethod {}
public final class BankTransfer implements PaymentMethod {}
public record Cash(BigDecimal amount) implements PaymentMethod {}
```

**키워드**:
- `sealed`: 상속/구현을 제한하는 부모
- `permits`: 허용된 하위 타입 명시
- `final`: 더 이상 상속 불가
- `sealed`: 다시 제한된 상속 허용
- `non-sealed`: 상속 제한 해제

---

## ❌ 기존 Enum/Inheritance 문제점

### 문제 1: Enum의 한계

```java
// ❌ Enum - 데이터 캡슐화 한계
public enum PaymentMethod {
    CREDIT_CARD,
    BANK_TRANSFER,
    CASH;

    // 문제: 각 결제 수단마다 다른 데이터를 가질 수 없음
    // CREDIT_CARD → cardNumber, cvv 필요
    // BANK_TRANSFER → accountNumber 필요
    // CASH → amount 필요
}

// ❌ Workaround: Map으로 데이터 관리 (타입 안전성 상실)
Map<PaymentMethod, Object> paymentData = new HashMap<>();
paymentData.put(PaymentMethod.CREDIT_CARD, new CreditCardData(...));
```

### 문제 2: 무제한 상속

```java
// ❌ 무제한 상속 - 새로운 하위 클래스 추가 시 기존 코드 영향
public interface PaymentMethod {
    void process();
}

// 누구나 구현 가능 → 컴파일러가 모든 타입을 알 수 없음
public class CreditCard implements PaymentMethod { ... }
public class BankTransfer implements PaymentMethod { ... }
// 나중에 추가
public class Cryptocurrency implements PaymentMethod { ... } // ⚠️ 기존 switch 문에서 미처리

// Switch 문에서 누락 위험
public void processPayment(PaymentMethod method) {
    if (method instanceof CreditCard) { ... }
    else if (method instanceof BankTransfer) { ... }
    // Cryptocurrency 케이스 누락! → 런타임 에러
}
```

---

## ✅ Sealed Classes 해결 방법

### 패턴 1: Sealed Interface + Records

```java
package com.company.domain.payment;

import java.math.BigDecimal;

/**
 * Payment Method - Sealed Interface
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface PaymentMethod
    permits CreditCard, BankTransfer, Cash {

    /**
     * 결제 처리 (각 하위 타입에서 구현)
     */
    PaymentResult process(Money amount);
}

/**
 * Credit Card Payment (Record)
 */
public record CreditCard(
    String cardNumber,
    String cvv,
    String expiryDate
) implements PaymentMethod {

    /**
     * Compact Constructor (Validation)
     */
    public CreditCard {
        if (cardNumber == null || cardNumber.length() != 16) {
            throw new IllegalArgumentException("Invalid card number");
        }
        if (cvv == null || cvv.length() != 3) {
            throw new IllegalArgumentException("Invalid CVV");
        }
    }

    @Override
    public PaymentResult process(Money amount) {
        // Credit Card 결제 로직
        return PaymentResult.success(amount, "Credit Card");
    }
}

/**
 * Bank Transfer Payment (Record)
 */
public record BankTransfer(
    String accountNumber,
    String bankCode
) implements PaymentMethod {

    public BankTransfer {
        if (accountNumber == null || accountNumber.isEmpty()) {
            throw new IllegalArgumentException("Account number is required");
        }
    }

    @Override
    public PaymentResult process(Money amount) {
        // Bank Transfer 결제 로직
        return PaymentResult.success(amount, "Bank Transfer");
    }
}

/**
 * Cash Payment (Record)
 */
public record Cash(
    BigDecimal receivedAmount
) implements PaymentMethod {

    public Cash {
        if (receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Received amount must be positive");
        }
    }

    @Override
    public PaymentResult process(Money amount) {
        // Cash 결제 로직 (거스름돈 계산 등)
        return PaymentResult.success(amount, "Cash");
    }
}
```

**핵심 장점**:
- ✅ 각 결제 수단이 고유한 데이터 보유 (Record 활용)
- ✅ 타입 안전성 (컴파일 타임 검증)
- ✅ 제한된 확장 (3가지 결제 수단만 허용)

---

### 패턴 2: Pattern Matching with Switch

```java
package com.company.domain.payment;

/**
 * Payment Processor with Pattern Matching
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class PaymentProcessor {

    /**
     * ✅ Pattern Matching + Sealed Classes
     *
     * - Exhaustive Checking: 모든 케이스 처리 강제
     * - Record Deconstruction: 데이터 자동 추출
     * - No default 필요: 컴파일러가 모든 타입 알고 있음
     */
    public String processPayment(PaymentMethod method, Money amount) {
        return switch (method) {
            case CreditCard(var cardNumber, var cvv, var expiry) ->
                "Processing credit card: " + maskCardNumber(cardNumber);

            case BankTransfer(var accountNumber, var bankCode) ->
                "Processing bank transfer to: " + accountNumber;

            case Cash(var receivedAmount) -> {
                BigDecimal change = receivedAmount.subtract(amount.amount());
                yield "Processing cash payment. Change: " + change;
            }
            // ✅ default 불필요 - Sealed Class로 모든 케이스 보장
        };
    }

    private String maskCardNumber(String cardNumber) {
        return "**** **** **** " + cardNumber.substring(12);
    }
}
```

**Before (기존 instanceof 체이닝)**:
```java
// ❌ Before - 장황한 instanceof + 캐스팅
public String processPayment(PaymentMethod method, Money amount) {
    if (method instanceof CreditCard) {
        CreditCard card = (CreditCard) method;
        return "Processing credit card: " + maskCardNumber(card.cardNumber());
    } else if (method instanceof BankTransfer) {
        BankTransfer transfer = (BankTransfer) method;
        return "Processing bank transfer to: " + transfer.accountNumber();
    } else if (method instanceof Cash) {
        Cash cash = (Cash) method;
        BigDecimal change = cash.receivedAmount().subtract(amount.amount());
        return "Processing cash payment. Change: " + change;
    } else {
        throw new IllegalArgumentException("Unknown payment method");
    }
}
```

**After (Sealed + Pattern Matching)**:
- ✅ 코드 간결성 (40% 감소)
- ✅ 타입 안전성 (컴파일 타임 검증)
- ✅ Exhaustive Checking (케이스 누락 방지)

---

## 🎯 실전 예제: Order Status

### ✅ Example 1: Sealed Interface + Records

```java
package com.company.domain.order;

import java.time.Instant;

/**
 * Order Status - Sealed Interface
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface OrderStatus
    permits Pending, Approved, Shipped, Delivered, Cancelled {

    /**
     * 상태 변경 가능 여부
     */
    boolean canTransitionTo(OrderStatus newStatus);
}

/**
 * Pending Status (주문 생성)
 */
public record Pending(Instant createdAt) implements OrderStatus {
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return newStatus instanceof Approved || newStatus instanceof Cancelled;
    }
}

/**
 * Approved Status (주문 승인)
 */
public record Approved(Instant approvedAt, UserId approvedBy) implements OrderStatus {
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return newStatus instanceof Shipped || newStatus instanceof Cancelled;
    }
}

/**
 * Shipped Status (배송 시작)
 */
public record Shipped(Instant shippedAt, String trackingNumber) implements OrderStatus {
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return newStatus instanceof Delivered;
    }
}

/**
 * Delivered Status (배송 완료)
 */
public record Delivered(Instant deliveredAt, String recipientName) implements OrderStatus {
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return false; // 최종 상태
    }
}

/**
 * Cancelled Status (주문 취소)
 */
public record Cancelled(Instant cancelledAt, String reason) implements OrderStatus {
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return false; // 최종 상태
    }
}
```

---

### ✅ Example 2: 상태 전환 로직

```java
package com.company.domain.order;

/**
 * Order Aggregate with Sealed Status
 *
 * @author development-team
 * @since 1.0.0
 */
public class Order {

    private final OrderId id;
    private OrderStatus status;

    private Order(OrderId id) {
        this.id = id;
        this.status = new Pending(Instant.now());
    }

    /**
     * ✅ 주문 승인 (상태 전환 검증)
     */
    public void approve(UserId approvedBy) {
        // ✅ Pattern Matching으로 현재 상태 확인
        if (status instanceof Pending pending) {
            // ✅ 상태 전환 검증
            OrderStatus newStatus = new Approved(Instant.now(), approvedBy);
            if (pending.canTransitionTo(newStatus)) {
                this.status = newStatus;
            } else {
                throw new IllegalStateException("Cannot approve order in current status");
            }
        } else {
            throw new IllegalStateException("Only PENDING orders can be approved");
        }
    }

    /**
     * ✅ 배송 시작 (상태 전환 검증)
     */
    public void ship(String trackingNumber) {
        if (status instanceof Approved approved) {
            OrderStatus newStatus = new Shipped(Instant.now(), trackingNumber);
            if (approved.canTransitionTo(newStatus)) {
                this.status = newStatus;
            } else {
                throw new IllegalStateException("Cannot ship order in current status");
            }
        } else {
            throw new IllegalStateException("Only APPROVED orders can be shipped");
        }
    }

    /**
     * ✅ 상태별 비즈니스 로직 (Pattern Matching)
     */
    public String getStatusMessage() {
        return switch (status) {
            case Pending(var createdAt) ->
                "Order created at " + createdAt;

            case Approved(var approvedAt, var approvedBy) ->
                "Order approved by " + approvedBy + " at " + approvedAt;

            case Shipped(var shippedAt, var trackingNumber) ->
                "Order shipped (Tracking: " + trackingNumber + ")";

            case Delivered(var deliveredAt, var recipient) ->
                "Order delivered to " + recipient + " at " + deliveredAt;

            case Cancelled(var cancelledAt, var reason) ->
                "Order cancelled: " + reason;
        };
    }
}
```

**Before (Enum + Map)**:
```java
// ❌ Before - Enum + 별도 데이터 저장
public enum OrderStatus { PENDING, APPROVED, SHIPPED, DELIVERED, CANCELLED }
private Map<String, Object> statusData = new HashMap<>();
```

**After (Sealed Class + Records)**:
```java
// ✅ After - Sealed Interface + 타입 안전 데이터
private OrderStatus status; // Pending | Approved | Shipped | Delivered | Cancelled
```

---

## 🔧 고급 패턴

### 패턴 1: Hierarchical Sealed Classes

```java
/**
 * Payment Event - 계층적 Sealed Classes
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface PaymentEvent
    permits PaymentStarted, PaymentCompleted, PaymentFailed {}

/**
 * Payment Started Events (하위 Sealed)
 */
public sealed interface PaymentStarted extends PaymentEvent
    permits PaymentInitiated, PaymentProcessing {}

public record PaymentInitiated(Instant timestamp, Money amount) implements PaymentStarted {}
public record PaymentProcessing(Instant timestamp, String gateway) implements PaymentStarted {}

/**
 * Payment Completed Events
 */
public record PaymentCompleted(Instant timestamp, String transactionId) implements PaymentEvent {}

/**
 * Payment Failed Events (하위 Sealed)
 */
public sealed interface PaymentFailed extends PaymentEvent
    permits PaymentRejected, PaymentTimeout {}

public record PaymentRejected(Instant timestamp, String reason) implements PaymentFailed {}
public record PaymentTimeout(Instant timestamp) implements PaymentFailed {}
```

---

### 패턴 2: Sealed Class + Visitor Pattern

```java
/**
 * Visitor Pattern with Sealed Classes
 *
 * @author development-team
 * @since 1.0.0
 */
public interface OrderStatusVisitor<R> {
    R visitPending(Pending pending);
    R visitApproved(Approved approved);
    R visitShipped(Shipped shipped);
    R visitDelivered(Delivered delivered);
    R visitCancelled(Cancelled cancelled);
}

/**
 * Sealed Interface에 accept 메서드 추가
 */
public sealed interface OrderStatus
    permits Pending, Approved, Shipped, Delivered, Cancelled {

    <R> R accept(OrderStatusVisitor<R> visitor);
}

/**
 * 각 Record에 accept 구현
 */
public record Pending(Instant createdAt) implements OrderStatus {
    @Override
    public <R> R accept(OrderStatusVisitor<R> visitor) {
        return visitor.visitPending(this);
    }
}

/**
 * Visitor 구현체
 */
public class OrderStatusEmailGenerator implements OrderStatusVisitor<String> {
    @Override
    public String visitPending(Pending pending) {
        return "Your order is being processed...";
    }

    @Override
    public String visitApproved(Approved approved) {
        return "Your order has been approved by " + approved.approvedBy();
    }

    // ... 나머지 visit 메서드
}
```

---

## 📋 Sealed Classes 체크리스트

### 설계
- [ ] 도메인 개념이 제한된 집합인가? (예: 결제 수단 3가지)
- [ ] 각 하위 타입이 고유한 데이터를 가지는가?
- [ ] 상태 전환 로직이 있는가?

### 구현
- [ ] `sealed` 키워드 사용
- [ ] `permits` 절에 모든 하위 타입 명시
- [ ] 하위 타입은 `final`, `sealed`, `non-sealed` 중 하나
- [ ] Pattern Matching과 함께 사용

### 테스트
- [ ] 모든 하위 타입에 대한 테스트
- [ ] Exhaustive Switch 검증
- [ ] 상태 전환 로직 테스트

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
