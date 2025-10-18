# Custom Error Codes - REST API Layer에서의 ErrorCode 활용

> **목적**: REST API Layer에서 Domain ErrorCode를 활용한 표준화된 에러 응답 구성
>
> **레이어**: Adapter Layer (REST API)
>
> **위치**:
> - ErrorCode 정의: `domain/[context]/exception/` (Domain Layer)
> - ErrorCode 사용: `adapter/in/web/exception/GlobalExceptionHandler.java` (Adapter Layer)
>
> **관련 문서**:
> - `../../08-error-handling/02_domain-exception-design.md` - Domain ErrorCode 정의
> - `./01_global-exception-handler.md` - GlobalExceptionHandler 구현
> - `../dto-patterns/03_error-response.md` - ErrorResponse DTO 표준
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. ErrorCode는 Domain Layer에서 정의

**REST API Layer는 ErrorCode를 사용만 함, 정의하지 않음**

```
Domain Layer
    ↓ 정의
ErrorCode (interface)
ErrorCode enum (OrderErrorCode, CustomerErrorCode 등)
    ↓ 의존
Adapter Layer (REST API)
    ↓ 사용
GlobalExceptionHandler → ErrorResponse 생성
```

**레이어별 역할**:
- **Domain Layer**: ErrorCode 인터페이스 정의, 각 Context별 ErrorCode enum 구현
- **Adapter Layer**: ErrorCode 인터페이스에만 의존, HTTP 응답 변환

### 2. ErrorCode 인터페이스 의존 (DIP)

**구체 enum 타입 의존 금지**

```java
// ✅ Good: 인터페이스 의존
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, ...) {

        ErrorCode errorCode = ex.getErrorCode();  // ✅ 인터페이스
        int httpStatus = errorCode.getHttpStatus();  // ✅ 인터페이스 메서드

        return ResponseEntity.status(httpStatus).body(...);
    }
}

// ❌ Bad: 구체 타입 의존
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex, ...) {

        OrderErrorCode errorCode = (OrderErrorCode) ex.getErrorCode();  // ❌ enum 타입 캐스팅

        return ResponseEntity.status(404).body(...);
    }
}
```

### 3. HTTP 상태 코드는 ErrorCode가 결정

**GlobalExceptionHandler는 ErrorCode의 HTTP 상태 코드를 그대로 사용**

```java
// ✅ Good: ErrorCode에서 HTTP 상태 코드 추출
int httpStatus = errorCode.getHttpStatus();  // 404, 409, 500 등
return ResponseEntity.status(httpStatus).body(errorResponse);

// ❌ Bad: Handler에서 하드코딩
return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);  // ❌ 하드코딩
```

---

## ❌ Anti-Pattern: Adapter Layer에서 ErrorCode 정의

### 문제 1: REST API Layer에서 ErrorCode enum 생성

```java
// ❌ Bad: Adapter Layer에 ErrorCode 정의
package com.company.adapter.in.web.exception;

public enum ApiErrorCode {  // ❌ Adapter Layer에 ErrorCode

    ORDER_NOT_FOUND("ORD-001", "Order not found"),
    INVALID_REQUEST("REQ-001", "Invalid request");

    private final String code;
    private final String message;

    ApiErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

**문제점**:
- 🔴 **레이어 경계 위반**: 비즈니스 로직(ErrorCode)이 Adapter Layer에 존재
- 🔴 **도메인 독립성 파괴**: Domain Layer가 Adapter Layer에 의존
- 🔴 **재사용성 저하**: 다른 Adapter (CLI, gRPC 등)에서 사용 불가
- 🔴 **테스트 어려움**: Domain 단위 테스트 시 Adapter 의존성 필요

### 문제 2: GlobalExceptionHandler에서 HTTP 상태 코드 하드코딩

```java
// ❌ Bad: 예외 타입별로 HTTP 상태 코드 하드코딩
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(...) {
        return ResponseEntity.status(404).body(...);  // ❌ 하드코딩
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(...) {
        return ResponseEntity.status(409).body(...);  // ❌ 하드코딩
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentFailed(...) {
        return ResponseEntity.status(500).body(...);  // ❌ 하드코딩
    }

    // 새로운 예외 추가 시마다 @ExceptionHandler 메서드 추가 필요...
}
```

**문제점**:
- 🔴 **중복**: 모든 예외마다 개별 핸들러 메서드 필요
- 🔴 **유지보수성 저하**: 새 예외 추가 시 Handler 수정 필요
- 🔴 **일관성 부족**: HTTP 상태 코드 결정 로직이 여러 곳에 분산

---

## ✅ Best Practice: ErrorCode 인터페이스 활용

### 패턴 1: ErrorCode 인터페이스 (Domain Layer)

**참조**: `domain/shared/exception/ErrorCode.java`

```java
package com.company.domain.shared.exception;

/**
 * ErrorCode 공통 인터페이스
 *
 * <p>모든 Bounded Context의 ErrorCode는 이 인터페이스를 구현합니다.
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

### 패턴 2: Domain별 ErrorCode 구현 (Domain Layer)

**참조**: `domain/order/exception/OrderErrorCode.java`

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

    // 400 Bad Request - 검증 실패
    INVALID_ORDER_ITEM("ORDER-101", 400, "Invalid order item"),
    EMPTY_ORDER("ORDER-102", 400, "Order items cannot be empty");

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

### 패턴 3: GlobalExceptionHandler에서 ErrorCode 활용 (Adapter Layer)

```java
package com.company.adapter.in.web.exception;

import com.company.adapter.in.web.dto.ErrorResponse;
import com.company.domain.shared.exception.BusinessException;
import com.company.domain.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST API Layer 전역 예외 핸들러
 *
 * <p>ErrorCode 인터페이스에만 의존하여 모든 Domain 예외를 처리합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * BusinessException 처리 (모든 Domain 예외)
     *
     * <p>ErrorCode 인터페이스를 통해 HTTP 상태 코드와 메시지를 추출합니다.
     * OrderErrorCode, CustomerErrorCode, PaymentErrorCode 등 모든 Domain ErrorCode를
     * 하나의 핸들러로 처리합니다.
     *
     * @param ex BusinessException
     * @param request HttpServletRequest
     * @return ResponseEntity with ErrorResponse
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        ErrorCode errorCode = ex.getErrorCode();  // ✅ 인터페이스 의존

        log.warn("Business exception occurred: code={}, message={}, path={}",
                errorCode.getCode(), ex.getMessage(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
            errorCode.getCode(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())  // ✅ ErrorCode에서 HTTP 상태 추출
            .body(response);
    }
}
```

**핵심 특징**:
1. ✅ **하나의 핸들러**: 모든 BusinessException을 하나의 메서드로 처리
2. ✅ **ErrorCode 인터페이스 의존**: 구체 enum 타입 의존 없음
3. ✅ **HTTP 상태 코드 자동 추출**: ErrorCode.getHttpStatus() 사용
4. ✅ **확장성**: 새로운 Domain ErrorCode 추가 시 Handler 수정 불필요

---

## 🎯 HTTP 상태 코드 매핑 전략

### 404 Not Found - 리소스 부재

```java
// Domain ErrorCode
ORDER_NOT_FOUND("ORDER-001", 404, "Order not found"),
CUSTOMER_NOT_FOUND("CUST-001", 404, "Customer not found"),
PRODUCT_NOT_FOUND("PROD-001", 404, "Product not found")
```

**사용 시나리오**:
- 주문 조회 시 해당 주문이 존재하지 않을 때
- 고객 조회 시 해당 고객이 존재하지 않을 때

**API 응답**:
```json
{
  "code": "ORDER-001",
  "message": "Order not found: 999",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders/999"
}
```

### 409 Conflict - 비즈니스 규칙 위반

```java
// Domain ErrorCode
INSUFFICIENT_STOCK("ORDER-002", 409, "Insufficient stock"),
INVALID_ORDER_STATUS("ORDER-003", 409, "Invalid order status transition"),
DUPLICATE_EMAIL("CUST-011", 409, "Email already exists")
```

**사용 시나리오**:
- 재고가 부족한 상품 주문 시도
- 잘못된 주문 상태 전이 (예: 배송완료 → 주문취소)
- 이미 존재하는 이메일로 회원가입 시도

**API 응답**:
```json
{
  "code": "ORDER-002",
  "message": "Insufficient stock: requested=100, available=50",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders"
}
```

### 400 Bad Request - 입력 검증 실패

```java
// Domain ErrorCode
INVALID_ORDER_ITEM("ORDER-101", 400, "Invalid order item"),
INVALID_EMAIL_FORMAT("CUST-101", 400, "Invalid email format"),
INVALID_CARD_NUMBER("PAY-101", 400, "Invalid card number")
```

**사용 시나리오**:
- 음수 수량으로 주문 시도
- 잘못된 이메일 형식
- 유효하지 않은 카드 번호

**API 응답**:
```json
{
  "code": "ORDER-101",
  "message": "Invalid order item: quantity must be positive",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders"
}
```

### 500 Internal Server Error - 시스템 오류

```java
// Domain ErrorCode
PAYMENT_GATEWAY_ERROR("PAY-011", 500, "Payment gateway error"),
PAYMENT_PROCESSING_FAILED("PAY-012", 500, "Payment processing failed")
```

**사용 시나리오**:
- 외부 결제 시스템 장애
- 예상치 못한 시스템 오류

**API 응답**:
```json
{
  "code": "PAY-011",
  "message": "Payment gateway error: connection timeout",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/payments"
}
```

---

## 🔍 ErrorCode 코드 체계

### Prefix 규칙

**Domain별로 고유한 Prefix 사용**

| Domain | Prefix | 범위 | 예시 |
|--------|--------|------|------|
| Order | ORDER- | 001-999 | ORDER-001, ORDER-002 |
| Customer | CUST- | 001-999 | CUST-001, CUST-011 |
| Payment | PAY- | 001-999 | PAY-001, PAY-011 |
| Product | PROD- | 001-999 | PROD-001, PROD-002 |
| Common | CMN- | 001-999 | CMN-001, CMN-999 |

### 번호 범위 규칙

**HTTP 상태 코드별 번호 범위 할당**

| 범위 | HTTP 상태 | 의미 | 예시 |
|------|-----------|------|------|
| 001-099 | 404 Not Found | 리소스 부재 | ORDER-001 |
| 011-050 | 409 Conflict | 비즈니스 규칙 위반 | ORDER-011 |
| 051-099 | 500 Internal Error | 시스템 오류 | PAY-051 |
| 101-199 | 400 Bad Request | 입력 검증 실패 | ORDER-101 |

**예시**:
```java
public enum OrderErrorCode implements ErrorCode {

    // 001-099: 404 Not Found
    ORDER_NOT_FOUND("ORDER-001", 404, "Order not found"),
    ORDER_ITEM_NOT_FOUND("ORDER-002", 404, "Order item not found"),

    // 011-050: 409 Conflict
    INSUFFICIENT_STOCK("ORDER-011", 409, "Insufficient stock"),
    INVALID_ORDER_STATUS("ORDER-012", 409, "Invalid order status"),
    DUPLICATE_ORDER("ORDER-013", 409, "Duplicate order"),

    // 051-099: 500 Internal Error
    ORDER_PROCESSING_FAILED("ORDER-051", 500, "Order processing failed"),

    // 101-199: 400 Bad Request
    INVALID_ORDER_ITEM("ORDER-101", 400, "Invalid order item"),
    EMPTY_ORDER("ORDER-102", 400, "Order items cannot be empty");
}
```

---

## 🎯 실전 예제: 완전한 ErrorCode 흐름

### 시나리오: 재고 부족으로 주문 실패

```java
// 1. Domain Layer - ErrorCode 정의
package com.company.domain.order.exception;

public enum OrderErrorCode implements ErrorCode {
    INSUFFICIENT_STOCK("ORDER-011", 409, "Insufficient stock");
    // ...
}

// 2. Domain Layer - Exception 정의
package com.company.domain.order.exception;

public class InsufficientStockException extends BusinessException {

    private final Long productId;
    private final int requestedQuantity;
    private final int availableQuantity;

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

    // getters...
}

// 3. Domain Layer - Aggregate에서 예외 발생
package com.company.domain.product.model;

public class Product {

    private ProductId id;
    private int stockQuantity;

    /**
     * 재고 검증
     *
     * @param requestedQuantity 요청 수량
     * @throws InsufficientStockException 재고 부족 시
     */
    public void checkStock(int requestedQuantity) {
        if (this.stockQuantity < requestedQuantity) {
            throw new InsufficientStockException(
                this.id.value(),
                requestedQuantity,
                this.stockQuantity
            );
        }
    }
}

// 4. Application Layer - UseCase에서 검증
package com.company.application.order;

@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    @Override
    public Response createOrder(Command command) {
        // Product 조회
        Product product = loadProductPort.findById(command.productId())
            .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        // 재고 검증 (InsufficientStockException 발생 가능)
        product.checkStock(command.quantity());

        // 주문 생성
        Order order = Order.create(command.customerId());
        order.addLineItem(product.getId(), command.quantity(), product.getPrice());

        return Response.from(saveOrderPort.save(order));
    }
}

// 5. Adapter Layer - GlobalExceptionHandler 자동 처리
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        ErrorCode errorCode = ex.getErrorCode();  // OrderErrorCode.INSUFFICIENT_STOCK

        ErrorResponse response = ErrorResponse.of(
            errorCode.getCode(),      // "ORDER-011"
            ex.getMessage(),          // "Insufficient stock for product 123: requested=100, available=50"
            request.getRequestURI()   // "/api/v1/orders"
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())  // 409 Conflict
            .body(response);
    }
}
```

### API 응답

**요청**:
```http
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": 1,
  "items": [
    {
      "productId": 123,
      "quantity": 100
    }
  ]
}
```

**응답**:
```http
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "code": "ORDER-011",
  "message": "Insufficient stock for product 123: requested=100, available=50",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders"
}
```

---

## 📋 ErrorCode 설계 체크리스트

### ErrorCode 정의 (Domain Layer)
- [ ] `ErrorCode` 인터페이스를 구현하는가?
- [ ] Bounded Context별로 ErrorCode enum을 분리했는가?
- [ ] 에러 코드 Prefix를 일관되게 사용하는가? (예: ORDER-xxx)
- [ ] HTTP 상태 코드를 적절히 매핑했는가?
- [ ] 에러 메시지가 명확하고 구체적인가?

### ErrorCode 활용 (Adapter Layer)
- [ ] `ErrorCode` 인터페이스에만 의존하는가?
- [ ] 구체 enum 타입을 참조하지 않는가?
- [ ] `errorCode.getHttpStatus()`로 HTTP 상태 코드를 추출하는가?
- [ ] 하드코딩된 HTTP 상태 코드가 없는가?

### HTTP 상태 코드 매핑
- [ ] 404 Not Found: 리소스 부재
- [ ] 409 Conflict: 비즈니스 규칙 위반
- [ ] 400 Bad Request: 입력 검증 실패
- [ ] 500 Internal Server Error: 시스템 오류

### 코드 체계
- [ ] Domain별 고유 Prefix가 있는가?
- [ ] 번호 범위가 HTTP 상태 코드별로 할당되었는가?
- [ ] 코드가 중복되지 않는가?

### 통합
- [ ] `GlobalExceptionHandler`와 연동되는가?
- [ ] `ErrorResponse` DTO 표준과 일치하는가?
- [ ] `BusinessException`을 통해 ErrorCode를 전달하는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
