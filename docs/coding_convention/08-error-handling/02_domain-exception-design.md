# Domain Exception Design - Domain Layer 예외 설계

> **목적**: 순수 도메인 예외 설계 및 Bounded Context별 ErrorCode 관리
>
> **위치**: `domain/[boundedContext]/exception/`
>
> **관련 문서**:
> - `01_error-handling-strategy.md` (전체 전략)
> - `05_errorcode-management.md` (ErrorCode 관리)
> - `02-domain-layer/package-guide/01_domain_package_guide.md` (Domain 패키지 구조)
>
> **필수 버전**: Java 21+

---

## 📌 핵심 원칙

### 1. Framework 독립성

**Domain Layer는 순수 Java만 사용**

```java
// ✅ Good: 순수 Java 예외
package com.company.domain.order.exception;

public class OrderNotFoundException extends BusinessException {
    // Framework 의존성 없음
}

// ❌ Bad: Spring 의존성
package com.company.domain.order.exception;

import org.springframework.http.HttpStatus;  // ❌ Spring 의존

@ResponseStatus(HttpStatus.NOT_FOUND)  // ❌ Framework 어노테이션
public class OrderNotFoundException extends RuntimeException {
    // ...
}
```

### 2. Bounded Context별 ErrorCode 분리

**DDD 원칙: 각 Context는 독립적인 ErrorCode를 보유**

```
domain/
├─ shared/exception/
│  ├─ ErrorCode.java (interface)          # ✅ 공통 인터페이스
│  ├─ BusinessException.java (abstract)   # ✅ 추상 예외
│  └─ CommonErrorCode.java (enum)         # ✅ 공통 에러
│
├─ order/exception/
│  └─ OrderErrorCode.java (enum)          # ✅ Order 전용
│
├─ customer/exception/
│  └─ CustomerErrorCode.java (enum)       # ✅ Customer 전용
│
└─ payment/exception/
   └─ PaymentErrorCode.java (enum)        # ✅ Payment 전용
```

---

## ❌ Anti-Pattern: 단일 거대 ErrorCode

### 문제: 모든 도메인을 하나의 enum에

```java
// ❌ Bad: 모든 도메인 에러를 하나의 enum에
package com.company.domain.shared.exception;

public enum ErrorCode {

    // Order Domain (100개)
    ORDER_NOT_FOUND("ORDER-001", 404, "Order not found"),
    INSUFFICIENT_STOCK("ORDER-002", 409, "Insufficient stock"),
    INVALID_ORDER_STATUS("ORDER-003", 409, "Invalid order status"),
    // ... 100개

    // Customer Domain (50개)
    CUSTOMER_NOT_FOUND("CUST-001", 404, "Customer not found"),
    DUPLICATE_EMAIL("CUST-002", 409, "Email already exists"),
    // ... 50개

    // Payment Domain (80개)
    PAYMENT_FAILED("PAY-001", 500, "Payment failed"),
    INVALID_CARD("PAY-002", 400, "Invalid card"),
    // ... 80개

    // Product Domain (120개)
    PRODUCT_NOT_FOUND("PROD-001", 404, "Product not found"),
    // ... 120개

    // 총 500개 이상의 에러 코드가 한 파일에...

    private final String code;
    private final int httpStatus;
    private final String message;

    ErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
```

**문제점**:
- 🔴 **Bounded Context 경계 무시**: DDD 원칙 위반
- 🔴 **단일 파일 비대화**: 500줄 이상의 enum 관리 불가
- 🔴 **팀 간 머지 충돌**: 여러 팀이 동시에 수정 시 충돌 빈번
- 🔴 **모듈 독립성 파괴**: 마이크로서비스 전환 시 분리 불가
- 🔴 **가독성 저하**: 특정 도메인 에러 찾기 어려움

---

## ✅ Best Practice: Bounded Context별 ErrorCode 분리

### 패턴 1: 공통 인터페이스 정의

```java
package com.company.domain.shared.exception;

/**
 * ErrorCode 공통 인터페이스
 *
 * <p>모든 Bounded Context의 ErrorCode는 이 인터페이스를 구현해야 합니다.
 * GlobalExceptionHandler는 이 인터페이스에만 의존합니다.
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

### 패턴 2: 추상 비즈니스 예외

```java
package com.company.domain.shared.exception;

/**
 * 비즈니스 예외 추상 클래스
 *
 * <p>모든 도메인 예외는 이 클래스를 상속해야 합니다.
 * RuntimeException을 상속하므로 Unchecked Exception입니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * ErrorCode만으로 예외 생성
     *
     * @param errorCode ErrorCode enum
     */
    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode + 커스텀 메시지로 예외 생성
     *
     * @param errorCode ErrorCode enum
     * @param message 커스텀 메시지 (ErrorCode 메시지 오버라이드)
     */
    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode + 커스텀 메시지 + 원인 예외로 예외 생성
     *
     * @param errorCode ErrorCode enum
     * @param message 커스텀 메시지
     * @param cause 원인 예외
     */
    protected BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

### 패턴 3: Context별 ErrorCode enum

#### Order Domain ErrorCode

```java
package com.company.domain.order.exception;

import com.company.domain.shared.exception.ErrorCode;

/**
 * Order Domain ErrorCode
 *
 * <p>주문 도메인 전용 에러 코드입니다.
 * 코드 Prefix: ORDER-xxx
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum OrderErrorCode implements ErrorCode {

    // 404 Not Found
    ORDER_NOT_FOUND("ORDER-001", 404, "Order not found"),

    // 409 Conflict - 비즈니스 규칙 위반
    INSUFFICIENT_STOCK("ORDER-002", 409, "Insufficient stock"),
    INVALID_ORDER_STATUS("ORDER-003", 409, "Invalid order status transition"),
    DUPLICATE_ORDER("ORDER-004", 409, "Duplicate order detected"),
    MAX_ORDER_ITEMS_EXCEEDED("ORDER-005", 409, "Maximum order items exceeded"),
    MIN_ORDER_AMOUNT_NOT_MET("ORDER-006", 409, "Minimum order amount not met"),

    // 400 Bad Request - 검증 실패
    INVALID_ORDER_ITEM("ORDER-101", 400, "Invalid order item"),
    EMPTY_ORDER("ORDER-102", 400, "Order items cannot be empty"),
    INVALID_QUANTITY("ORDER-103", 400, "Invalid quantity");

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
 * 코드 Prefix: CUST-xxx
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum CustomerErrorCode implements ErrorCode {

    // 404 Not Found
    CUSTOMER_NOT_FOUND("CUST-001", 404, "Customer not found"),
    ADDRESS_NOT_FOUND("CUST-002", 404, "Address not found"),

    // 409 Conflict
    DUPLICATE_EMAIL("CUST-011", 409, "Email already exists"),
    DUPLICATE_PHONE("CUST-012", 409, "Phone number already exists"),

    // 400 Bad Request
    INVALID_EMAIL_FORMAT("CUST-101", 400, "Invalid email format"),
    INVALID_PASSWORD("CUST-102", 400, "Invalid password format");

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
 * 코드 Prefix: PAY-xxx
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum PaymentErrorCode implements ErrorCode {

    // 404 Not Found
    PAYMENT_NOT_FOUND("PAY-001", 404, "Payment not found"),

    // 500 Internal Server Error - 외부 결제 시스템 오류
    PAYMENT_GATEWAY_ERROR("PAY-011", 500, "Payment gateway error"),
    PAYMENT_PROCESSING_FAILED("PAY-012", 500, "Payment processing failed"),

    // 400 Bad Request
    INVALID_CARD_NUMBER("PAY-101", 400, "Invalid card number"),
    EXPIRED_CARD("PAY-102", 400, "Card has expired"),
    INSUFFICIENT_FUNDS("PAY-103", 400, "Insufficient funds");

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

### 패턴 4: 공통 ErrorCode (선택적)

```java
package com.company.domain.shared.exception;

/**
 * 공통 ErrorCode
 *
 * <p>모든 도메인에서 공통으로 사용 가능한 에러 코드입니다.
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

    // 403 Forbidden
    FORBIDDEN("CMN-103", 403, "Access denied"),

    // 500 Internal Server Error
    INTERNAL_ERROR("CMN-999", 500, "Internal server error");

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

---

## 🎯 Domain Exception 구현 패턴

### 패턴 1: 단순 예외 (ErrorCode만 사용)

```java
package com.company.domain.order.exception;

import com.company.domain.shared.exception.BusinessException;

/**
 * 주문을 찾을 수 없을 때 발생하는 예외
 *
 * @author Development Team
 * @since 1.0.0
 */
public class OrderNotFoundException extends BusinessException {

    /**
     * 주문 ID로 예외 생성
     *
     * @param orderId 주문 ID
     */
    public OrderNotFoundException(Long orderId) {
        super(OrderErrorCode.ORDER_NOT_FOUND,
              "Order not found: " + orderId);
    }
}
```

### 패턴 2: 상세 정보 포함 예외

```java
package com.company.domain.order.exception;

import com.company.domain.shared.exception.BusinessException;

/**
 * 재고 부족 예외
 *
 * @author Development Team
 * @since 1.0.0
 */
public class InsufficientStockException extends BusinessException {

    private final Long productId;
    private final int requestedQuantity;
    private final int availableQuantity;

    /**
     * 재고 부족 예외 생성
     *
     * @param productId 상품 ID
     * @param requestedQuantity 요청 수량
     * @param availableQuantity 가용 수량
     */
    public InsufficientStockException(
            Long productId,
            int requestedQuantity,
            int availableQuantity) {

        super(OrderErrorCode.INSUFFICIENT_STOCK,
              String.format("Insufficient stock for product %d: requested=%d, available=%d",
                          productId, requestedQuantity, availableQuantity));

        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
```

### 패턴 3: 상태 전이 예외

```java
package com.company.domain.order.exception;

import com.company.domain.order.model.OrderStatus;
import com.company.domain.shared.exception.BusinessException;

/**
 * 잘못된 주문 상태 전이 시도 예외
 *
 * @author Development Team
 * @since 1.0.0
 */
public class InvalidOrderStatusException extends BusinessException {

    private final Long orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus targetStatus;

    /**
     * 잘못된 상태 전이 예외 생성
     *
     * @param orderId 주문 ID
     * @param currentStatus 현재 상태
     * @param targetStatus 목표 상태
     */
    public InvalidOrderStatusException(
            Long orderId,
            OrderStatus currentStatus,
            OrderStatus targetStatus) {

        super(OrderErrorCode.INVALID_ORDER_STATUS,
              String.format("Invalid order status transition: order=%d, current=%s, target=%s",
                          orderId, currentStatus, targetStatus));

        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getTargetStatus() {
        return targetStatus;
    }
}
```

---

## 🏗️ Domain 예외 사용 예시

### Aggregate Root에서 예외 발생

```java
package com.company.domain.order.model;

import com.company.domain.order.exception.*;

/**
 * Order Aggregate Root
 *
 * @author Development Team
 * @since 1.0.0
 */
public class Order {

    private OrderId id;
    private List<OrderLineItem> lineItems;
    private OrderStatus status;
    private Money totalAmount;

    /**
     * 주문 확정
     *
     * @throws EmptyOrderException 주문 항목이 비어있을 때
     * @throws InvalidOrderStatusException 잘못된 상태 전이 시도 시
     */
    public void confirm() {
        // ✅ 불변식 검증: 최소 1개 상품 필수
        if (lineItems.isEmpty()) {
            throw new EmptyOrderException(this.id.value());
        }

        // ✅ 불변식 검증: DRAFT → CONFIRMED만 허용
        if (this.status != OrderStatus.DRAFT) {
            throw new InvalidOrderStatusException(
                this.id.value(),
                this.status,
                OrderStatus.CONFIRMED
            );
        }

        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    /**
     * 주문 항목 추가
     *
     * @param productId 상품 ID
     * @param quantity 수량
     * @param unitPrice 단가
     * @throws OrderAlreadyConfirmedException 이미 확정된 주문일 때
     * @throws DuplicateProductException 중복 상품 추가 시도 시
     * @throws MaxOrderItemsExceededException 최대 항목 수 초과 시
     */
    public void addLineItem(ProductId productId, int quantity, Money unitPrice) {
        // ✅ 불변식 1: DRAFT 상태에서만 수정 가능
        if (this.status != OrderStatus.DRAFT) {
            throw new OrderAlreadyConfirmedException(this.id.value());
        }

        // ✅ 불변식 2: 중복 상품 금지
        boolean exists = lineItems.stream()
            .anyMatch(item -> item.getProductId().equals(productId));

        if (exists) {
            throw new DuplicateProductException(productId.value());
        }

        // ✅ 불변식 3: 최대 100개 상품
        if (lineItems.size() >= 100) {
            throw new MaxOrderItemsExceededException(this.id.value());
        }

        OrderLineItem lineItem = OrderLineItem.create(productId, quantity, unitPrice);
        lineItems.add(lineItem);

        // ✅ 불변식 4: 총액 = 상품 합계
        this.totalAmount = calculateTotal();
    }
}
```

---

## 📋 Domain Exception 설계 체크리스트

### ErrorCode 설계
- [ ] `ErrorCode` 인터페이스를 구현하는가?
- [ ] Bounded Context별로 ErrorCode enum을 분리했는가?
- [ ] 에러 코드 Prefix를 일관되게 사용하는가? (예: ORDER-xxx)
- [ ] HTTP 상태 코드를 적절히 매핑했는가?
- [ ] 에러 메시지가 명확하고 구체적인가?

### BusinessException 설계
- [ ] `BusinessException` 추상 클래스를 상속하는가?
- [ ] RuntimeException을 사용하는가? (Unchecked Exception)
- [ ] 생성자에서 ErrorCode를 받는가?
- [ ] 상세 정보를 필드로 보유하는가? (선택적)

### Framework 독립성
- [ ] `org.springframework.*` 의존성이 없는가?
- [ ] `jakarta.persistence.*` 의존성이 없는가?
- [ ] 순수 Java만 사용하는가?
- [ ] Framework 어노테이션을 사용하지 않는가?

### 예외 메시지
- [ ] 메시지가 구체적인가? (예: "Order not found: 999")
- [ ] 동적 정보를 포함하는가? (ID, 상태 등)
- [ ] 사용자에게 노출 가능한 메시지인가?

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
