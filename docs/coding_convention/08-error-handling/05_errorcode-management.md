# ErrorCode Management - ErrorCode 관리 전략

> **목적**: Bounded Context별 ErrorCode enum 관리 및 코드 체계 정의
>
> **위치**: `domain/[boundedContext]/exception/`
>
> **관련 문서**:
> - `01_error-handling-strategy.md` (전체 전략)
> - `02_domain-exception-design.md` (Domain 예외)
>
> **필수 버전**: Java 21+

---

## 📌 핵심 원칙

### 1. Bounded Context별 ErrorCode 분리

**DDD 원칙: 각 Context는 독립적인 ErrorCode를 보유**

```
domain/
├─ shared/exception/
│  ├─ ErrorCode.java (interface)
│  └─ CommonErrorCode.java (공통 에러만)
│
├─ order/exception/
│  └─ OrderErrorCode.java (Order 전용)
│
├─ customer/exception/
│  └─ CustomerErrorCode.java (Customer 전용)
│
└─ payment/exception/
   └─ PaymentErrorCode.java (Payment 전용)
```

### 2. 코드 Prefix 체계

**각 Domain은 고유한 Prefix를 사용**

| Domain | Prefix | 번호 대역 | 예시 |
|--------|--------|----------|------|
| **Common** | CMN | 001~099 | CMN-001, CMN-099 |
| **Order** | ORDER | 001~199 | ORDER-001, ORDER-199 |
| **Customer** | CUST | 001~199 | CUST-001, CUST-199 |
| **Payment** | PAY | 001~199 | PAY-001, PAY-199 |
| **Product** | PROD | 001~199 | PROD-001, PROD-199 |

---

## ❌ Anti-Pattern: 단일 거대 ErrorCode

### 문제: 모든 도메인을 하나의 enum에

```java
// ❌ Bad: 모든 도메인 에러를 하나의 enum에
package com.company.domain.shared.exception;

public enum ErrorCode {

    // Order Domain (100개)
    ORDER_NOT_FOUND("ORDER-001", 404),
    ORDER_ALREADY_CONFIRMED("ORDER-002", 409),
    INSUFFICIENT_STOCK("ORDER-003", 409),
    // ... 97개 더

    // Customer Domain (50개)
    CUSTOMER_NOT_FOUND("CUST-001", 404),
    DUPLICATE_EMAIL("CUST-002", 409),
    // ... 48개 더

    // Payment Domain (80개)
    PAYMENT_FAILED("PAY-001", 500),
    INVALID_CARD("PAY-002", 400),
    // ... 78개 더

    // Product Domain (120개)
    PRODUCT_NOT_FOUND("PROD-001", 404),
    OUT_OF_STOCK("PROD-002", 409),
    // ... 118개 더

    // 총 500개 이상의 에러 코드가 한 파일에...

    private final String code;
    private final int httpStatus;

    ErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
```

**문제점**:
- 🔴 **파일 비대화**: 500줄 이상의 enum
- 🔴 **머지 충돌**: 여러 팀이 동시 수정 시 충돌
- 🔴 **가독성 저하**: 특정 에러 찾기 어려움
- 🔴 **Bounded Context 경계 무시**: DDD 원칙 위반
- 🔴 **마이크로서비스 전환 불가**: 모듈 독립성 파괴

---

## ✅ Best Practice: Context별 ErrorCode 분리

### 패턴 1: 공통 인터페이스 정의

```java
package com.company.domain.shared.exception;

/**
 * ErrorCode 공통 인터페이스
 *
 * <p>모든 Bounded Context의 ErrorCode는 이 인터페이스를 구현해야 합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
public interface ErrorCode {

    /**
     * 에러 코드 (예: "ORDER-001", "CUST-001")
     */
    String getCode();

    /**
     * HTTP 상태 코드 (예: 404, 409, 500)
     */
    int getHttpStatus();

    /**
     * 에러 메시지 (예: "Order not found")
     */
    String getMessage();
}
```

### 패턴 2: 공통 ErrorCode (선택적)

```java
package com.company.domain.shared.exception;

/**
 * 공통 ErrorCode
 *
 * <p>모든 도메인에서 공통으로 사용 가능한 에러 코드입니다.
 * <ul>
 *   <li>인증/인가 에러</li>
 *   <li>입력 검증 에러</li>
 *   <li>서버 오류</li>
 * </ul>
 *
 * 코드 Prefix: CMN-xxx
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum CommonErrorCode implements ErrorCode {

    // 400 Bad Request
    INVALID_INPUT("CMN-001", 400, "Invalid input"),
    VALIDATION_FAILED("CMN-002", 400, "Validation failed"),

    // 401 Unauthorized
    UNAUTHORIZED("CMN-101", 401, "Authentication required"),
    INVALID_TOKEN("CMN-102", 401, "Invalid authentication token"),
    EXPIRED_TOKEN("CMN-103", 401, "Authentication token has expired"),

    // 403 Forbidden
    FORBIDDEN("CMN-201", 403, "Access denied"),

    // 500 Internal Server Error
    INTERNAL_ERROR("CMN-999", 500, "Internal server error"),
    DATABASE_ERROR("CMN-998", 500, "Database error occurred");

    private final String code;
    private final int httpStatus;
    private final String message;

    CommonErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

### 패턴 3: Context별 ErrorCode

#### Order Domain ErrorCode

```java
package com.company.domain.order.exception;

import com.company.domain.shared.exception.ErrorCode;

/**
 * Order Domain ErrorCode
 *
 * <p>주문 도메인 전용 에러 코드입니다.
 * <ul>
 *   <li>주문 조회 실패</li>
 *   <li>재고 부족</li>
 *   <li>주문 상태 전이 실패</li>
 * </ul>
 *
 * 코드 Prefix: ORDER-xxx
 * 번호 대역: 001~199
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum OrderErrorCode implements ErrorCode {

    // ===== 404 Not Found =====
    ORDER_NOT_FOUND("ORDER-001", 404, "Order not found"),

    // ===== 409 Conflict - 비즈니스 규칙 위반 =====
    INSUFFICIENT_STOCK("ORDER-010", 409, "Insufficient stock"),
    INVALID_ORDER_STATUS("ORDER-011", 409, "Invalid order status transition"),
    ORDER_ALREADY_CONFIRMED("ORDER-012", 409, "Order is already confirmed"),
    ORDER_ALREADY_CANCELLED("ORDER-013", 409, "Order is already cancelled"),
    DUPLICATE_ORDER("ORDER-014", 409, "Duplicate order detected"),
    MAX_ORDER_ITEMS_EXCEEDED("ORDER-015", 409, "Maximum order items exceeded"),
    MIN_ORDER_AMOUNT_NOT_MET("ORDER-016", 409, "Minimum order amount not met"),
    DUPLICATE_PRODUCT_IN_ORDER("ORDER-017", 409, "Duplicate product in order"),

    // ===== 400 Bad Request - 검증 실패 =====
    INVALID_ORDER_ITEM("ORDER-101", 400, "Invalid order item"),
    EMPTY_ORDER("ORDER-102", 400, "Order items cannot be empty"),
    INVALID_QUANTITY("ORDER-103", 400, "Invalid quantity"),
    INVALID_PRICE("ORDER-104", 400, "Invalid price");

    private final String code;
    private final int httpStatus;
    private final String message;

    OrderErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

#### Customer Domain ErrorCode

```java
package com.company.domain.customer.exception;

import com.company.domain.shared.exception.ErrorCode;

/**
 * Customer Domain ErrorCode
 *
 * <p>고객 도메인 전용 에러 코드입니다.
 * <ul>
 *   <li>고객 조회 실패</li>
 *   <li>이메일/전화번호 중복</li>
 *   <li>비밀번호 검증 실패</li>
 * </ul>
 *
 * 코드 Prefix: CUST-xxx
 * 번호 대역: 001~199
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum CustomerErrorCode implements ErrorCode {

    // ===== 404 Not Found =====
    CUSTOMER_NOT_FOUND("CUST-001", 404, "Customer not found"),
    ADDRESS_NOT_FOUND("CUST-002", 404, "Address not found"),

    // ===== 409 Conflict =====
    DUPLICATE_EMAIL("CUST-010", 409, "Email already exists"),
    DUPLICATE_PHONE("CUST-011", 409, "Phone number already exists"),

    // ===== 400 Bad Request =====
    INVALID_EMAIL_FORMAT("CUST-101", 400, "Invalid email format"),
    INVALID_PASSWORD("CUST-102", 400, "Invalid password format"),
    INVALID_PHONE_NUMBER("CUST-103", 400, "Invalid phone number format");

    private final String code;
    private final int httpStatus;
    private final String message;

    CustomerErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

#### Payment Domain ErrorCode

```java
package com.company.domain.payment.exception;

import com.company.domain.shared.exception.ErrorCode;

/**
 * Payment Domain ErrorCode
 *
 * <p>결제 도메인 전용 에러 코드입니다.
 * <ul>
 *   <li>결제 처리 실패</li>
 *   <li>카드 정보 오류</li>
 *   <li>잔액 부족</li>
 * </ul>
 *
 * 코드 Prefix: PAY-xxx
 * 번호 대역: 001~199
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum PaymentErrorCode implements ErrorCode {

    // ===== 404 Not Found =====
    PAYMENT_NOT_FOUND("PAY-001", 404, "Payment not found"),

    // ===== 500 Internal Server Error - 외부 결제 시스템 오류 =====
    PAYMENT_GATEWAY_ERROR("PAY-010", 500, "Payment gateway error"),
    PAYMENT_PROCESSING_FAILED("PAY-011", 500, "Payment processing failed"),
    PAYMENT_TIMEOUT("PAY-012", 500, "Payment processing timeout"),

    // ===== 400 Bad Request =====
    INVALID_CARD_NUMBER("PAY-101", 400, "Invalid card number"),
    EXPIRED_CARD("PAY-102", 400, "Card has expired"),
    INSUFFICIENT_FUNDS("PAY-103", 400, "Insufficient funds"),
    INVALID_CVV("PAY-104", 400, "Invalid CVV"),
    INVALID_AMOUNT("PAY-105", 400, "Invalid payment amount");

    private final String code;
    private final int httpStatus;
    private final String message;

    PaymentErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

---

## 🎯 ErrorCode 네이밍 컨벤션

### 규칙 1: HTTP 상태 코드별 그룹화

```java
public enum OrderErrorCode implements ErrorCode {

    // ===== 404 Not Found =====
    ORDER_NOT_FOUND("ORDER-001", 404, "Order not found"),

    // ===== 409 Conflict =====
    INSUFFICIENT_STOCK("ORDER-010", 409, "Insufficient stock"),
    INVALID_ORDER_STATUS("ORDER-011", 409, "Invalid order status"),

    // ===== 400 Bad Request =====
    INVALID_ORDER_ITEM("ORDER-101", 400, "Invalid order item");

    // ...
}
```

**장점**:
- ✅ 동일 상태 코드의 에러를 한눈에 파악
- ✅ 새 에러 추가 시 적절한 위치 쉽게 찾기

### 규칙 2: 번호 대역 할당

| HTTP 상태 | 번호 대역 | 예시 |
|----------|----------|------|
| **404 Not Found** | 001~009 | ORDER-001, ORDER-002 |
| **409 Conflict** | 010~099 | ORDER-010, ORDER-011 |
| **400 Bad Request** | 101~199 | ORDER-101, ORDER-102 |
| **500 Internal Error** | 900~999 | ORDER-999 |

### 규칙 3: enum 이름 컨벤션

```java
// ✅ Good: 명사 + 동작/상태
ORDER_NOT_FOUND           // 주문 + 없음
INSUFFICIENT_STOCK        // 재고 + 부족
INVALID_ORDER_STATUS      // 주문 상태 + 유효하지 않음
DUPLICATE_EMAIL           // 이메일 + 중복

// ❌ Bad: 모호하거나 너무 일반적
ERROR                     // ❌ 너무 일반적
FAIL                      // ❌ 동작이 명확하지 않음
ORDER_ERROR               // ❌ 어떤 에러인지 불명확
```

---

## 📊 ErrorCode 코드 체계 예시

### 전체 프로젝트 ErrorCode 맵

```
├─ CMN (Common)
│  ├─ CMN-001 ~ CMN-099: 일반 에러
│  ├─ CMN-101 ~ CMN-199: 인증/인가 에러
│  └─ CMN-998 ~ CMN-999: 서버 오류
│
├─ ORDER (Order Domain)
│  ├─ ORDER-001 ~ ORDER-009: Not Found
│  ├─ ORDER-010 ~ ORDER-099: Conflict
│  └─ ORDER-101 ~ ORDER-199: Bad Request
│
├─ CUST (Customer Domain)
│  ├─ CUST-001 ~ CUST-009: Not Found
│  ├─ CUST-010 ~ CUST-099: Conflict
│  └─ CUST-101 ~ CUST-199: Bad Request
│
├─ PAY (Payment Domain)
│  ├─ PAY-001 ~ PAY-009: Not Found
│  ├─ PAY-010 ~ PAY-099: Conflict
│  ├─ PAY-101 ~ PAY-199: Bad Request
│  └─ PAY-900 ~ PAY-999: Internal Error
│
└─ PROD (Product Domain)
   ├─ PROD-001 ~ PROD-009: Not Found
   ├─ PROD-010 ~ PROD-099: Conflict
   └─ PROD-101 ~ PROD-199: Bad Request
```

---

## 🔍 ErrorCode 관리 실무 FAQ

### Q1: ErrorCode가 너무 많아지면 어떻게 하나요?

**A: 카테고리별 하위 enum 생성**

```java
// domain/order/exception/
OrderErrorCode.java (집합 인터페이스)
OrderValidationErrorCode.java
OrderStateErrorCode.java
OrderBusinessErrorCode.java
```

```java
/**
 * Order 검증 에러
 */
public enum OrderValidationErrorCode implements ErrorCode {
    INVALID_ORDER_ITEM("ORDER-101", 400, "Invalid order item"),
    EMPTY_ORDER("ORDER-102", 400, "Order items cannot be empty");
    // ...
}

/**
 * Order 상태 전이 에러
 */
public enum OrderStateErrorCode implements ErrorCode {
    INVALID_ORDER_STATUS("ORDER-011", 409, "Invalid order status"),
    ORDER_ALREADY_CONFIRMED("ORDER-012", 409, "Order already confirmed");
    // ...
}
```

### Q2: 여러 도메인에서 공통으로 사용하는 에러는?

**A: CommonErrorCode 사용**

```java
// domain/shared/exception/CommonErrorCode.java
UNAUTHORIZED("CMN-101", 401, "Authentication required"),
FORBIDDEN("CMN-201", 403, "Access denied"),
INTERNAL_ERROR("CMN-999", 500, "Internal server error");
```

### Q3: 마이크로서비스로 전환 시?

**A: 이미 Context별로 분리되어 있어 쉽게 분리 가능**

```
order-service/
├─ domain/
│  └─ order/exception/
│     └─ OrderErrorCode.java  ✅ 독립적으로 이동 가능

customer-service/
├─ domain/
│  └─ customer/exception/
│     └─ CustomerErrorCode.java  ✅ 독립적으로 이동 가능
```

### Q4: 에러 코드 번호 관리 규칙은?

**A: Prefix + HTTP 상태별 번호 대역**

```
ORDER-001: 404 Not Found
ORDER-010~099: 409 Conflict
ORDER-101~199: 400 Bad Request
ORDER-900~999: 500 Internal Error
```

### Q5: 새 ErrorCode 추가 절차는?

**절차**:
1. 해당 Domain ErrorCode enum 파일 열기
2. HTTP 상태 코드에 맞는 섹션 찾기
3. 사용 가능한 다음 번호 할당
4. enum 상수 추가
5. Domain 예외 클래스 생성

**예시**:
```java
// 1. OrderErrorCode.java 열기
// 2. 409 Conflict 섹션 찾기
// 3. ORDER-018 사용 가능 확인
// 4. enum 상수 추가
PAYMENT_NOT_COMPLETED("ORDER-018", 409, "Payment not completed"),

// 5. Domain 예외 생성
public class PaymentNotCompletedException extends BusinessException {
    public PaymentNotCompletedException(Long orderId) {
        super(OrderErrorCode.PAYMENT_NOT_COMPLETED,
              "Payment not completed for order: " + orderId);
    }
}
```

---

## 📋 ErrorCode 관리 체크리스트

### 설계
- [ ] `ErrorCode` 인터페이스를 정의했는가?
- [ ] Bounded Context별로 ErrorCode enum을 분리했는가?
- [ ] 코드 Prefix를 일관되게 사용하는가?
- [ ] HTTP 상태별 번호 대역을 할당했는가?

### 네이밍
- [ ] enum 이름이 명확하고 구체적인가?
- [ ] HTTP 상태 코드별로 그룹화되어 있는가?
- [ ] 주석으로 카테고리를 구분했는가?

### 유지보수
- [ ] 새 ErrorCode 추가 절차가 명확한가?
- [ ] ErrorCode 중복이 없는가?
- [ ] 번호 대역 관리가 체계적인가?

### 문서화
- [ ] 각 ErrorCode의 사용 시점이 명확한가?
- [ ] Javadoc으로 설명이 작성되어 있는가?
- [ ] 코드 Prefix 체계가 문서화되어 있는가?

---

## 📝 ErrorCode 관리 템플릿

### 새 Domain ErrorCode 생성 템플릿

```java
package com.company.domain.[boundedContext].exception;

import com.company.domain.shared.exception.ErrorCode;

/**
 * [Domain Name] Domain ErrorCode
 *
 * <p>[도메인 설명]
 * <ul>
 *   <li>[에러 카테고리 1]</li>
 *   <li>[에러 카테고리 2]</li>
 * </ul>
 *
 * 코드 Prefix: [PREFIX]-xxx
 * 번호 대역: 001~199
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum [DomainName]ErrorCode implements ErrorCode {

    // ===== 404 Not Found =====
    [RESOURCE]_NOT_FOUND("[PREFIX]-001", 404, "[Resource] not found"),

    // ===== 409 Conflict =====
    [BUSINESS_RULE]_VIOLATION("[PREFIX]-010", 409, "[Business rule] violation"),

    // ===== 400 Bad Request =====
    INVALID_[FIELD]("[PREFIX]-101", 400, "Invalid [field]");

    private final String code;
    private final int httpStatus;
    private final String message;

    [DomainName]ErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
