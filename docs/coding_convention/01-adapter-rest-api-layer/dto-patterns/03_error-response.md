# Error Response DTO - 에러 응답 DTO 표준화

> **목적**: REST API의 일관된 에러 응답 형식 정의 및 구현 패턴
>
> **위치**: `adapter/in/web/dto/`, `adapter/in/web/exception/`
>
> **관련 문서**:
> - `controller-design/03_response-handling.md` (응답 처리)
> - `08-error-handling/03_global-exception-handler.md` (예외 핸들러)
> - `08-error-handling/04_error-response-format.md` (에러 응답 형식)
> - `02-domain-layer/exception/01_domain-exception.md` (Domain 예외)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. Error Response의 역할

**모든 API 에러를 일관된 JSON 구조로 반환**

```
Exception → GlobalExceptionHandler → ErrorResponse DTO → HTTP Response
```

**핵심 특성**:
- **일관성**: 모든 API 에러가 동일한 JSON 구조
- **명확성**: 에러 코드, 메시지, 발생 위치 포함
- **보안**: 민감한 정보(Stack Trace, SQL) 노출 금지
- **표준 준수**: RFC 7807 (Problem Details) 영감

### 2. 에러 응답 구조

```json
{
  "code": "ORDER-001",
  "message": "Order not found: 999",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders/999"
}
```

**필수 필드**:
- `code`: 에러 식별자 (ErrorCode)
- `message`: 사람이 읽을 수 있는 메시지
- `timestamp`: 에러 발생 시각
- `path`: 에러 발생 요청 경로

---

## ❌ Anti-Pattern: 비일관적 에러 응답

### 문제 1: Controller마다 다른 형식

```java
// ❌ Bad: Controller A
@RestController
public class OrderController {
    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.getOrder(id));
        } catch (Exception ex) {
            // ❌ 임의의 Map 구조
            return ResponseEntity.status(500)
                .body(Map.of("error", "Not Found", "details", ex.getMessage()));
        }
    }
}

// ❌ Bad: Controller B
@RestController
public class CustomerController {
    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(customerService.getCustomer(id));
        } catch (Exception ex) {
            // ❌ 다른 구조
            return ResponseEntity.status(404)
                .body(Map.of("status", 404, "errorMessage", ex.getMessage()));
        }
    }
}
```

**문제점**:
- 🔴 클라이언트가 각 API마다 다른 파싱 로직 필요
- 🔴 에러 처리 코드 중복
- 🔴 유지보수 어려움

### 문제 2: Spring 기본 에러 응답 사용

```json
// ❌ Bad: Spring 기본 에러 응답
{
  "timestamp": "2025-10-17T10:30:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "path": "/api/v1/orders/999"
}
```

**문제점**:
- 🔴 비즈니스 에러 코드 없음
- 🔴 커스터마이징 불가
- 🔴 Validation 에러 상세 정보 부족

### 문제 3: 민감 정보 노출

```java
// ❌ Bad: Stack Trace 노출
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handle(Exception ex) {
    return ResponseEntity.status(500)
        .body(Map.of(
            "error", ex.getClass().getName(),
            "message", ex.getMessage(),
            "stackTrace", Arrays.toString(ex.getStackTrace())  // ❌ 보안 위험!
        ));
}
```

---

## ✅ Best Practice: 표준화된 ErrorResponse

### 패턴 1: ErrorResponse DTO (Record)

```java
package com.company.adapter.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 표준 API 에러 응답 DTO
 *
 * <p>모든 API 에러는 이 형식으로 반환됩니다.
 *
 * <p>RFC 7807 (Problem Details) 영감을 받았으나 완전 준수는 아님.
 *
 * @param code 에러 코드 (예: "ORDER-001")
 * @param message 에러 메시지 (사람이 읽을 수 있는 설명)
 * @param timestamp 에러 발생 시각
 * @param path 에러 발생 요청 경로
 * @param errors 검증 에러 목록 (Validation 실패 시)
 *
 * @author Development Team
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // ✅ null 필드 제외
public record ErrorResponse(
    String code,
    String message,
    LocalDateTime timestamp,
    String path,
    List<FieldError> errors  // ✅ Validation 에러 (선택적)
) {

    /**
     * 기본 에러 응답 생성 (Validation 에러 없음)
     *
     * @param code 에러 코드
     * @param message 에러 메시지
     * @param path 요청 경로
     * @return ErrorResponse
     */
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(
            code,
            message,
            LocalDateTime.now(),
            path,
            null
        );
    }

    /**
     * Validation 에러 포함 응답 생성
     *
     * @param code 에러 코드
     * @param message 에러 메시지
     * @param path 요청 경로
     * @param errors 필드 에러 목록
     * @return ErrorResponse
     */
    public static ErrorResponse of(
            String code,
            String message,
            String path,
            List<FieldError> errors) {

        return new ErrorResponse(
            code,
            message,
            LocalDateTime.now(),
            path,
            errors
        );
    }

    /**
     * 필드 검증 에러
     *
     * @param field 필드명
     * @param rejectedValue 거부된 값
     * @param message 에러 메시지
     */
    public record FieldError(
        String field,
        Object rejectedValue,
        String message
    ) {}
}
```

---

## ✅ GlobalExceptionHandler 통합

### 패턴: @RestControllerAdvice 구현

```java
package com.company.adapter.in.web.exception;

import com.company.domain.exception.BusinessException;
import com.company.domain.exception.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Global Exception Handler
 *
 * <p>모든 Controller의 예외를 중앙에서 처리하여 일관된 ErrorResponse를 반환합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Business Exception 처리 (404 Not Found)
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Entity not found: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
            ex.getErrorCode().getCode(),    // "ORDER-001"
            ex.getMessage(),                // "Order not found: 999"
            request.getRequestURI()         // "/api/v1/orders/999"
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(response);
    }

    /**
     * Business Exception 처리 (409 Conflict)
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        log.warn("Business exception: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
            ex.getErrorCode().getCode(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }

    /**
     * Validation Exception 처리 (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Validation failed: {}", ex.getBindingResult());

        // ✅ FieldError 목록 변환
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ErrorResponse.FieldError(
                error.getField(),
                error.getRejectedValue(),
                error.getDefaultMessage()
            ))
            .toList();

        ErrorResponse response = ErrorResponse.of(
            "VALIDATION_FAILED",
            "Validation failed",
            request.getRequestURI(),
            fieldErrors  // ✅ FieldError 목록 포함
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    /**
     * Internal Server Error 처리 (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error", ex);  // ✅ 로그에만 상세 내용

        ErrorResponse response = ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred. Please try again later.",  // ✅ 일반 메시지
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
}
```

---

## 🎯 에러 응답 시나리오별 예시

### 시나리오 1: 리소스 Not Found (404)

**요청**:
```http
GET /api/v1/orders/999
```

**Domain Exception**:
```java
package com.company.domain.order.exception;

import com.company.domain.exception.EntityNotFoundException;
import com.company.domain.exception.ErrorCode;

/**
 * Order Not Found Exception
 *
 * @author Development Team
 * @since 1.0.0
 */
public class OrderNotFoundException extends EntityNotFoundException {

    public OrderNotFoundException(Long orderId) {
        super(OrderErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId);
    }
}
```

**ErrorCode 정의**:
```java
package com.company.domain.order.exception;

import com.company.domain.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Order Error Codes
 *
 * @author Development Team
 * @since 1.0.0
 */
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND("ORDER-001", "Order not found", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK("ORDER-002", "Insufficient stock", HttpStatus.CONFLICT),
    INVALID_ORDER_STATUS("ORDER-003", "Invalid order status", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    OrderErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
```

**응답**:
```json
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "code": "ORDER-001",
  "message": "Order not found: 999",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders/999"
}
```

---

### 시나리오 2: Validation 실패 (400)

**요청**:
```http
POST /api/v1/customers
Content-Type: application/json

{
  "email": "invalid-email",
  "password": "123"
}
```

**응답**:
```json
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "code": "VALIDATION_FAILED",
  "message": "Validation failed",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/customers",
  "errors": [
    {
      "field": "email",
      "rejectedValue": "invalid-email",
      "message": "Invalid email format"
    },
    {
      "field": "password",
      "rejectedValue": "123",
      "message": "Password must be at least 8 characters"
    }
  ]
}
```

---

### 시나리오 3: 비즈니스 규칙 위반 (409)

**요청**:
```http
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": 1,
  "items": [
    {"productId": 100, "quantity": 50}
  ]
}
```

**Domain Exception**:
```java
package com.company.domain.order.exception;

import com.company.domain.exception.BusinessException;

/**
 * Insufficient Stock Exception
 *
 * @author Development Team
 * @since 1.0.0
 */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super(
            OrderErrorCode.INSUFFICIENT_STOCK,
            String.format(
                "Insufficient stock for product %d: requested=%d, available=%d",
                productId, requested, available
            )
        );
    }
}
```

**응답**:
```json
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "code": "ORDER-002",
  "message": "Insufficient stock for product 100: requested=50, available=10",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders"
}
```

---

### 시나리오 4: 서버 오류 (500)

**요청**:
```http
GET /api/v1/orders/123
```

**응답**:
```json
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred. Please try again later.",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders/123"
}
```

**로그** (서버 내부):
```
2025-10-17 10:30:00 ERROR [GlobalExceptionHandler] Unexpected error
java.lang.NullPointerException: Cannot invoke "Order.getId()" because "order" is null
    at com.company.application.order.service.OrderService.getOrder(OrderService.java:45)
    ...
```

---

## 🔧 고급 패턴: 상세 정보 포함

### 패턴: 추가 상세 정보 (Details Map)

```java
package com.company.adapter.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 확장 가능한 에러 응답 DTO
 *
 * @author Development Team
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,
    String message,
    LocalDateTime timestamp,
    String path,
    List<FieldError> errors,
    Map<String, Object> details  // ✅ 추가 상세 정보 (선택적)
) {

    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, LocalDateTime.now(), path, null, null);
    }

    public static ErrorResponse of(
            String code,
            String message,
            String path,
            List<FieldError> errors) {

        return new ErrorResponse(code, message, LocalDateTime.now(), path, errors, null);
    }

    /**
     * 상세 정보 포함 응답 생성
     */
    public static ErrorResponse withDetails(
            String code,
            String message,
            String path,
            Map<String, Object> details) {

        return new ErrorResponse(code, message, LocalDateTime.now(), path, null, details);
    }

    public record FieldError(
        String field,
        Object rejectedValue,
        String message
    ) {}
}
```

**사용 예시**:
```java
@ExceptionHandler(InsufficientStockException.class)
public ResponseEntity<ErrorResponse> handleInsufficientStock(
        InsufficientStockException ex,
        HttpServletRequest request) {

    Map<String, Object> details = Map.of(
        "productId", ex.getProductId(),
        "requested", ex.getRequestedQuantity(),
        "available", ex.getAvailableQuantity()
    );

    ErrorResponse response = ErrorResponse.withDetails(
        ex.getErrorCode().getCode(),
        ex.getMessage(),
        request.getRequestURI(),
        details  // ✅ 추가 상세 정보
    );

    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(response);
}
```

**응답**:
```json
{
  "code": "ORDER-002",
  "message": "Insufficient stock for product 100",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders",
  "details": {
    "productId": 100,
    "requested": 50,
    "available": 10
  }
}
```

---

## 📊 HTTP 상태 코드별 에러 응답 매핑

| HTTP 상태 | ErrorCode 예시 | 사용 시점 | Exception 예시 |
|-----------|---------------|----------|----------------|
| **400 Bad Request** | `VALIDATION_FAILED`, `INVALID_INPUT` | 검증 실패, 잘못된 요청 | `MethodArgumentNotValidException` |
| **401 Unauthorized** | `UNAUTHORIZED`, `INVALID_TOKEN` | 인증 필요, 토큰 만료 | `UnauthorizedException` |
| **403 Forbidden** | `FORBIDDEN` | 권한 없음 | `AccessDeniedException` |
| **404 Not Found** | `ORDER_NOT_FOUND`, `CUSTOMER_NOT_FOUND` | 리소스 없음 | `EntityNotFoundException` |
| **409 Conflict** | `INSUFFICIENT_STOCK`, `DUPLICATE_EMAIL` | 비즈니스 규칙 위반 | `BusinessException` |
| **500 Internal Server Error** | `INTERNAL_ERROR`, `DATABASE_ERROR` | 서버 오류 | `Exception`, `RuntimeException` |

---

## 🎯 클라이언트 에러 처리 가이드

### JavaScript/TypeScript 예시

```typescript
interface ErrorResponse {
  code: string;
  message: string;
  timestamp: string;
  path: string;
  errors?: FieldError[];
  details?: Record<string, any>;
}

interface FieldError {
  field: string;
  rejectedValue: any;
  message: string;
}

async function createOrder(orderData: CreateOrderRequest): Promise<Order> {
  try {
    const response = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(orderData)
    });

    if (!response.ok) {
      const error: ErrorResponse = await response.json();

      // ✅ 에러 코드별 처리
      switch (error.code) {
        case 'ORDER-002':  // Insufficient Stock
          showStockError(error.message, error.details);
          break;
        case 'VALIDATION_FAILED':
          showValidationErrors(error.errors);
          break;
        default:
          showGenericError(error.message);
      }

      throw new Error(error.message);
    }

    return await response.json();

  } catch (err) {
    console.error('Order creation failed', err);
    throw err;
  }
}

function showValidationErrors(errors?: FieldError[]) {
  if (!errors) return;

  errors.forEach(error => {
    // 필드별 에러 표시
    const field = document.getElementById(error.field);
    if (field) {
      field.classList.add('error');
      field.setAttribute('title', error.message);
    }
  });
}

function showStockError(message: string, details?: Record<string, any>) {
  alert(`재고 부족: ${message}\n요청: ${details?.requested}, 가용: ${details?.available}`);
}
```

---

## 📋 Error Response 설계 체크리스트

### 기본 구조
- [ ] `code` 필드를 포함하는가? (에러 코드)
- [ ] `message` 필드를 포함하는가? (사람이 읽을 수 있는 메시지)
- [ ] `timestamp` 필드를 포함하는가? (에러 발생 시각)
- [ ] `path` 필드를 포함하는가? (요청 경로)

### Validation 에러
- [ ] `errors` 필드를 포함하는가? (FieldError 목록)
- [ ] `FieldError`에 `field`, `rejectedValue`, `message`가 있는가?

### JSON 직렬화
- [ ] `@JsonInclude(JsonInclude.Include.NON_NULL)`을 사용하는가?
- [ ] null 필드는 JSON에서 제외되는가?

### GlobalExceptionHandler 통합
- [ ] `@RestControllerAdvice`로 중앙 처리하는가?
- [ ] 모든 예외 타입별 `@ExceptionHandler`가 있는가?
- [ ] 일관된 ErrorResponse를 반환하는가?

### 보안
- [ ] 민감한 정보 (스택 트레이스, SQL 쿼리 등)를 노출하지 않는가?
- [ ] 500 에러 시 일반 메시지만 반환하는가?
- [ ] 상세 내용은 로그에만 기록하는가?

### ErrorCode 정의
- [ ] Domain Layer에 ErrorCode Enum이 정의되어 있는가?
- [ ] ErrorCode에 HTTP 상태 코드 매핑이 있는가?
- [ ] 각 Domain별로 고유한 에러 코드 범위가 있는가? (ORDER-001, CUSTOMER-001 등)

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
