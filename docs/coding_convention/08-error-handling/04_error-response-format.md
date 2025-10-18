# Error Response Format - API 에러 응답 포맷

> **목적**: 일관된 RESTful API 에러 응답 형식 정의
>
> **위치**: `adapter/in/web/exception/ErrorResponse.java`
>
> **관련 문서**:
> - `01_error-handling-strategy.md` (전체 전략)
> - `03_global-exception-handler.md` (예외 핸들러)
> - `01-adapter-rest-api-layer/controller-design/01_restful-api-design.md` (HTTP 상태 코드)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. 일관된 에러 응답 형식

**모든 API 에러는 동일한 JSON 구조를 사용**

```json
{
  "code": "ORDER-001",
  "message": "Order not found: 999",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders/999"
}
```

### 2. RFC 7807 (Problem Details) 영감

**표준 기반 설계 (단, 완전 준수는 아님)**

- `code`: 에러 식별자 (ErrorCode)
- `message`: 사람이 읽을 수 있는 메시지
- `timestamp`: 에러 발생 시각
- `path`: 에러 발생 경로

---

## ❌ Anti-Pattern: 비일관적 에러 응답

### 문제 1: Controller마다 다른 형식

```java
// ❌ Bad: Controller A
{
  "error": "Not Found",
  "details": "Order not found"
}

// ❌ Bad: Controller B
{
  "status": 404,
  "errorMessage": "Order not found",
  "errorCode": "ORDER_NOT_FOUND"
}

// ❌ Bad: Controller C
{
  "success": false,
  "message": "Order not found"
}
```

**문제점**:
- 🔴 클라이언트가 각 API마다 다른 파싱 로직 필요
- 🔴 에러 처리 코드 중복
- 🔴 유지보수 어려움

### 문제 2: Spring 기본 에러 응답 사용

```java
// ❌ Bad: Spring 기본 에러 응답
{
  "timestamp": "2025-01-17T10:30:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "path": "/api/v1/orders/999"
}
```

**문제점**:
- 🔴 비즈니스 에러 코드 없음
- 🔴 커스터마이징 불가
- 🔴 Validation 에러 상세 정보 부족

---

## ✅ Best Practice: 표준화된 ErrorResponse

### 패턴 1: 기본 ErrorResponse (Record)

```java
package com.company.adapter.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 표준 API 에러 응답
 *
 * <p>모든 API 에러는 이 형식으로 반환됩니다.
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,
    String message,
    LocalDateTime timestamp,
    String path,
    List<FieldError> errors
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

## 🎯 에러 응답 시나리오별 예시

### 시나리오 1: 리소스 Not Found (404)

**요청**:
```http
GET /api/v1/orders/999
```

**응답**:
```json
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "code": "ORDER-001",
  "message": "Order not found: 999",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders/999"
}
```

**GlobalExceptionHandler**:
```java
@ExceptionHandler(OrderNotFoundException.class)
public ResponseEntity<ErrorResponse> handleNotFoundException(
        OrderNotFoundException ex,
        HttpServletRequest request) {

    ErrorResponse response = ErrorResponse.of(
        ex.getErrorCode().getCode(),    // "ORDER-001"
        ex.getMessage(),                // "Order not found: 999"
        request.getRequestURI()         // "/api/v1/orders/999"
    );

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(response);
}
```

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
  "timestamp": "2025-01-17T10:30:00",
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

**GlobalExceptionHandler**:
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {

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
```

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

**응답**:
```json
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "code": "ORDER-002",
  "message": "Insufficient stock for product 100: requested=50, available=10",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders"
}
```

**GlobalExceptionHandler**:
```java
@ExceptionHandler(InsufficientStockException.class)
public ResponseEntity<ErrorResponse> handleInsufficientStock(
        InsufficientStockException ex,
        HttpServletRequest request) {

    ErrorResponse response = ErrorResponse.of(
        ex.getErrorCode().getCode(),    // "ORDER-002"
        ex.getMessage(),                // 상세 메시지
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(response);
}
```

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
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders/123"
}
```

**GlobalExceptionHandler**:
```java
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
```

---

## 🔍 고급 패턴: 상세 에러 정보 포함

### 패턴: 추가 정보 포함 (선택적)

```java
package com.company.adapter.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 확장 가능한 에러 응답
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

    /**
     * 기본 에러 응답 생성
     */
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(
            code,
            message,
            LocalDateTime.now(),
            path,
            null,
            null
        );
    }

    /**
     * Validation 에러 포함 응답
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
            errors,
            null
        );
    }

    /**
     * 상세 정보 포함 응답
     */
    public static ErrorResponse withDetails(
            String code,
            String message,
            String path,
            Map<String, Object> details) {

        return new ErrorResponse(
            code,
            message,
            LocalDateTime.now(),
            path,
            null,
            details
        );
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
  "timestamp": "2025-01-17T10:30:00",
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

| HTTP 상태 | ErrorCode 예시 | 사용 시점 |
|-----------|---------------|----------|
| **400 Bad Request** | `VALIDATION_FAILED`, `INVALID_INPUT` | 검증 실패, 잘못된 요청 |
| **401 Unauthorized** | `UNAUTHORIZED`, `INVALID_TOKEN` | 인증 필요, 토큰 만료 |
| **403 Forbidden** | `FORBIDDEN` | 권한 없음 |
| **404 Not Found** | `ORDER_NOT_FOUND`, `CUSTOMER_NOT_FOUND` | 리소스 없음 |
| **409 Conflict** | `INSUFFICIENT_STOCK`, `DUPLICATE_EMAIL` | 비즈니스 규칙 위반 |
| **500 Internal Server Error** | `INTERNAL_ERROR`, `DATABASE_ERROR` | 서버 오류 |

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
          showStockError(error.message);
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
```

---

## 📋 ErrorResponse 설계 체크리스트

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

### 일관성
- [ ] 모든 API 에러가 동일한 형식을 사용하는가?
- [ ] GlobalExceptionHandler에서 통일된 ErrorResponse를 반환하는가?

### 보안
- [ ] 민감한 정보 (스택 트레이스, SQL 쿼리 등)를 노출하지 않는가?
- [ ] 500 에러 시 일반 메시지만 반환하는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
