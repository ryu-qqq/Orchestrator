# Pattern Matching - Java 21 Record Pattern Matching

**목적**: Record + Switch Expression + Pattern Matching으로 타입 안전한 분기 처리

**관련 문서**:
- [Sealed Classes](../sealed-classes/01_domain-modeling.md)
- [DTO with Records](./01_dto-with-records.md)

**필수 버전**: Java 21+

---

## 📌 핵심 원칙

### Pattern Matching 장점

1. **타입 안전성**: 컴파일 타임 검증
2. **Null 안전성**: NPE 방지
3. **코드 간결성**: instanceof + cast 제거
4. **Exhaustive Checking**: 모든 케이스 처리 강제

---

## ✅ Pattern Matching 패턴

### 패턴 1: instanceof Pattern Matching

```java
// ❌ Before (Java 17)
if (obj instanceof String) {
    String str = (String) obj; // 명시적 캐스팅
    System.out.println(str.length());
}

// ✅ After (Java 21)
if (obj instanceof String str) { // 자동 캐스팅
    System.out.println(str.length());
}
```

---

### 패턴 2: Switch Expression with Records

```java
package com.company.domain.payment;

/**
 * Payment Method (Sealed Interface + Records)
 */
public sealed interface PaymentMethod
    permits CreditCard, BankTransfer, Cash {}

public record CreditCard(String cardNumber, String cvv) implements PaymentMethod {}
public record BankTransfer(String accountNumber) implements PaymentMethod {}
public record Cash(BigDecimal amount) implements PaymentMethod {}

/**
 * Pattern Matching with Switch Expression
 */
public class PaymentProcessor {

    public String processPayment(PaymentMethod method) {
        return switch (method) {
            case CreditCard(var cardNumber, var cvv) ->
                "Processing credit card: " + maskCardNumber(cardNumber);

            case BankTransfer(var accountNumber) ->
                "Processing bank transfer to: " + accountNumber;

            case Cash(var amount) ->
                "Processing cash payment: " + amount;
        }; // ✅ Exhaustive - 모든 케이스 강제
    }

    private String maskCardNumber(String cardNumber) {
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
```

**핵심 기능**:
- ✅ Record Deconstruction (`CreditCard(var cardNumber, var cvv)`)
- ✅ Exhaustive Checking (모든 케이스 처리 강제)
- ✅ No default 필요 (Sealed + Pattern Matching)

---

### 패턴 3: Nested Pattern Matching

```java
/**
 * Order Event (Sealed Interface)
 */
public sealed interface OrderEvent permits OrderCreated, OrderApproved, OrderCancelled {}

public record OrderCreated(OrderId orderId, CustomerId customerId) implements OrderEvent {}
public record OrderApproved(OrderId orderId, Instant approvedAt) implements OrderEvent {}
public record OrderCancelled(OrderId orderId, String reason) implements OrderEvent {}

/**
 * Event Handler with Nested Pattern Matching
 */
public class OrderEventHandler {

    public void handleEvent(OrderEvent event) {
        switch (event) {
            case OrderCreated(var orderId, var customerId) -> {
                System.out.println("Order created: " + orderId.value());
                notifyCustomer(customerId);
            }

            case OrderApproved(var orderId, var approvedAt) ->
                System.out.println("Order approved: " + orderId.value() + " at " + approvedAt);

            case OrderCancelled(var orderId, var reason) ->
                System.out.println("Order cancelled: " + orderId.value() + " - " + reason);
        }
    }
}
```

---

## 📋 Pattern Matching 체크리스트

- [ ] instanceof 대신 Pattern Matching
- [ ] Switch Expression 활용
- [ ] Sealed Class + Pattern Matching 조합
- [ ] Exhaustive Checking 활용

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
