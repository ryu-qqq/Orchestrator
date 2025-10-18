# Global Exception Handler - Adapter Layer 예외 처리

> **목적**: Spring `@RestControllerAdvice`를 사용한 전역 예외 핸들러 패턴
>
> **위치**: `adapter/in/web/exception/GlobalExceptionHandler.java`
>
> **관련 문서**:
> - `01_error-handling-strategy.md` (전체 전략)
> - `02_domain-exception-design.md` (Domain 예외)
> - `04_error-response-format.md` (에러 응답 포맷)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. 중앙 집중식 예외 처리

**모든 예외는 GlobalExceptionHandler에서 처리**

```
Controller → Exception 발생
     ↓
GlobalExceptionHandler (@RestControllerAdvice)
     ↓
ErrorResponse 생성 + HTTP 상태 코드
     ↓
JSON 응답 반환
```

### 2. ErrorCode 인터페이스에만 의존

**Domain 구체 타입 의존 금지**

```java
// ✅ Good: 인터페이스 의존
ErrorCode errorCode = ex.getErrorCode();  // 인터페이스

// ❌ Bad: 구체 타입 의존
OrderErrorCode errorCode = ex.getOrderErrorCode();  // enum 직접 참조
```

---

## ❌ Anti-Pattern: Controller별 예외 처리

### 문제: 각 Controller에서 개별 처리

```java
// ❌ Bad: Controller마다 try-catch
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable Long orderId) {
        try {
            Order order = orderService.getOrder(orderId);
            return ResponseEntity.ok(order);

        } catch (OrderNotFoundException ex) {
            // ❌ 중복: 모든 Controller에서 동일한 예외 처리 반복
            ErrorResponse error = new ErrorResponse(
                "ORDER_NOT_FOUND",
                ex.getMessage()
            );
            return ResponseEntity.status(404).body(error);

        } catch (Exception ex) {
            // ❌ 중복: 일반 예외 처리도 반복
            ErrorResponse error = new ErrorResponse(
                "INTERNAL_ERROR",
                "An error occurred"
            );
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // ...
        } catch (ValidationException ex) {
            // ❌ 중복: 동일한 예외 처리 로직 반복
            return ResponseEntity.status(400).body(...);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(...);
        }
    }
}
```

**문제점**:
- 🔴 **코드 중복**: 모든 Controller에서 동일한 예외 처리 반복
- 🔴 **유지보수성 저하**: 에러 응답 형식 변경 시 모든 Controller 수정 필요
- 🔴 **일관성 부족**: Controller마다 다른 에러 응답 형식 사용 가능
- 🔴 **가독성 저하**: 비즈니스 로직과 예외 처리 코드 혼재

---

## ✅ Best Practice: GlobalExceptionHandler

### 패턴: @RestControllerAdvice 사용

```java
package com.company.adapter.in.web.exception;

import com.company.domain.shared.exception.BusinessException;
import com.company.domain.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 전역 예외 핸들러
 *
 * <p>모든 Controller에서 발생하는 예외를 중앙에서 처리합니다.
 * Domain Layer의 BusinessException을 HTTP 응답으로 변환합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * BusinessException 처리
     *
     * <p>Domain Layer에서 발생한 모든 비즈니스 예외를 처리합니다.
     * ErrorCode 인터페이스를 통해 HTTP 상태 코드 및 메시지를 추출합니다.
     *
     * @param ex BusinessException
     * @param request HttpServletRequest
     * @return ErrorResponse with HTTP status
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        ErrorCode errorCode = ex.getErrorCode();

        log.warn("Business exception occurred: code={}, message={}",
                errorCode.getCode(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
            errorCode.getCode(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    /**
     * Bean Validation 예외 처리
     *
     * <p>@Valid 어노테이션으로 발생하는 검증 실패 예외를 처리합니다.
     * FieldError 목록을 ErrorResponse에 포함합니다.
     *
     * @param ex MethodArgumentNotValidException
     * @param request HttpServletRequest
     * @return ErrorResponse with validation errors (400 Bad Request)
     */
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

        log.warn("Validation failed: {} errors", fieldErrors.size());

        ErrorResponse response = ErrorResponse.of(
            "VALIDATION_FAILED",
            "Validation failed",
            request.getRequestURI(),
            fieldErrors
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    /**
     * IllegalArgumentException 처리
     *
     * <p>잘못된 인자로 인한 예외를 처리합니다 (400 Bad Request).
     *
     * @param ex IllegalArgumentException
     * @param request HttpServletRequest
     * @return ErrorResponse (400 Bad Request)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
            "INVALID_ARGUMENT",
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    /**
     * IllegalStateException 처리
     *
     * <p>잘못된 상태에서의 작업 시도 예외를 처리합니다 (409 Conflict).
     *
     * @param ex IllegalStateException
     * @param request HttpServletRequest
     * @return ErrorResponse (409 Conflict)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {

        log.warn("Illegal state: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
            "ILLEGAL_STATE",
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(response);
    }

    /**
     * 예상치 못한 예외 처리
     *
     * <p>위에서 처리되지 않은 모든 예외를 처리합니다 (500 Internal Server Error).
     * 상세 에러는 로그에만 기록하고, 클라이언트에는 일반 메시지만 반환합니다.
     *
     * @param ex Exception
     * @param request HttpServletRequest
     * @return ErrorResponse (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse response = ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred. Please try again later.",
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
}
```

---

## 🎯 예외 타입별 처리 전략

### 1. BusinessException (Domain 예외)

**처리**: ErrorCode의 HTTP 상태 코드 사용

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException ex,
        HttpServletRequest request) {

    ErrorCode errorCode = ex.getErrorCode();  // ✅ 인터페이스 의존

    // ErrorCode에서 HTTP 상태 코드 추출
    int httpStatus = errorCode.getHttpStatus();  // 404, 409 등

    ErrorResponse response = ErrorResponse.of(
        errorCode.getCode(),      // "ORDER-001"
        ex.getMessage(),          // "Order not found: 999"
        request.getRequestURI()   // "/api/v1/orders/999"
    );

    return ResponseEntity
        .status(httpStatus)
        .body(response);
}
```

**예시 응답**:
```json
{
  "code": "ORDER-001",
  "message": "Order not found: 999",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders/999"
}
```

### 2. MethodArgumentNotValidException (Bean Validation 실패)

**처리**: 400 Bad Request + 필드별 에러 목록

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {

    // FieldError 목록 추출
    List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> new ErrorResponse.FieldError(
            error.getField(),           // "email"
            error.getRejectedValue(),   // "invalid-email"
            error.getDefaultMessage()   // "Invalid email format"
        ))
        .toList();

    ErrorResponse response = ErrorResponse.of(
        "VALIDATION_FAILED",
        "Validation failed",
        request.getRequestURI(),
        fieldErrors  // ✅ 필드별 에러 포함
    );

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(response);
}
```

**예시 응답**:
```json
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

### 3. IllegalArgumentException (잘못된 인자)

**처리**: 400 Bad Request

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
        IllegalArgumentException ex,
        HttpServletRequest request) {

    ErrorResponse response = ErrorResponse.of(
        "INVALID_ARGUMENT",
        ex.getMessage(),
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(response);
}
```

### 4. IllegalStateException (잘못된 상태)

**처리**: 409 Conflict

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponse> handleIllegalStateException(
        IllegalStateException ex,
        HttpServletRequest request) {

    ErrorResponse response = ErrorResponse.of(
        "ILLEGAL_STATE",
        ex.getMessage(),
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(response);
}
```

### 5. Exception (예상치 못한 예외)

**처리**: 500 Internal Server Error + 로그

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleInternalServerError(
        Exception ex,
        HttpServletRequest request) {

    log.error("Unexpected error occurred", ex);  // ✅ 상세 에러는 로그에만

    ErrorResponse response = ErrorResponse.of(
        "INTERNAL_SERVER_ERROR",
        "An unexpected error occurred. Please try again later.",  // ✅ 일반 메시지만 반환
        request.getRequestURI()
    );

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(response);
}
```

---

## 🔒 보안 고려사항

### 1. 민감한 정보 노출 방지

```java
// ✅ Good: 일반 메시지만 반환
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleInternalServerError(Exception ex, ...) {
    log.error("Unexpected error", ex);  // ✅ 상세 내용은 로그에만

    return ResponseEntity
        .status(500)
        .body(ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred"  // ✅ 일반 메시지
        ));
}

// ❌ Bad: 스택 트레이스 노출
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleInternalServerError(Exception ex, ...) {
    return ResponseEntity
        .status(500)
        .body(ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            ex.toString()  // ❌ 스택 트레이스 노출 위험
        ));
}
```

### 2. SQL Injection 정보 노출 방지

```java
// ✅ Good: 일반 메시지로 변환
@ExceptionHandler(DataAccessException.class)
public ResponseEntity<ErrorResponse> handleDataAccessException(
        DataAccessException ex,
        HttpServletRequest request) {

    log.error("Database error", ex);  // ✅ 로그에만 상세 내용

    return ResponseEntity
        .status(500)
        .body(ErrorResponse.of(
            "DATABASE_ERROR",
            "A database error occurred"  // ✅ SQL 쿼리 노출 방지
        ));
}
```

---

## 🎯 실전 예제: 완전한 예외 처리 흐름

### 시나리오: 주문 생성 API

```java
// 1. Controller
@RestController
@RequestMapping("/api/v1/orders")
public class OrderCommandController {

    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {  // ✅ @Valid

        // DTO → Command 변환
        CreateOrderCommand command = orderMapper.toCommand(request);

        // UseCase 실행 (예외 발생 가능)
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // Response 변환
        OrderApiResponse apiResponse = orderMapper.toApiResponse(response);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(apiResponse);
    }
}

// 2. UseCase (Application Layer)
@Service
public class CreateOrderService implements CreateOrderUseCase {

    @Override
    @Transactional
    public Response createOrder(Command command) {
        // Product 조회 (예외 발생 가능)
        Product product = loadProductPort.findById(command.productId())
            .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        // Order 생성
        Order order = Order.create(command.customerId());

        // 재고 검증 (예외 발생 가능)
        product.checkStock(command.quantity());

        // Order에 항목 추가 (예외 발생 가능)
        order.addLineItem(product.getId(), command.quantity(), product.getPrice());

        // 저장
        Order savedOrder = saveOrderPort.save(order);

        return Response.from(savedOrder);
    }
}

// 3. GlobalExceptionHandler가 자동 처리
// - ProductNotFoundException → 404 Not Found
// - InsufficientStockException → 409 Conflict
// - MethodArgumentNotValidException → 400 Bad Request
```

**예외 발생 시 흐름**:
```
Controller (@Valid 검증 실패)
    ↓
MethodArgumentNotValidException 발생
    ↓
GlobalExceptionHandler.handleValidationException()
    ↓
400 Bad Request + FieldError 목록
```

```
UseCase (재고 부족)
    ↓
InsufficientStockException 발생
    ↓
GlobalExceptionHandler.handleBusinessException()
    ↓
409 Conflict + ErrorResponse
```

---

## 📋 GlobalExceptionHandler 체크리스트

### 기본 구조
- [ ] `@RestControllerAdvice` 어노테이션 사용하는가?
- [ ] 모든 예외를 중앙에서 처리하는가?
- [ ] ErrorCode 인터페이스에만 의존하는가?

### 예외 타입별 처리
- [ ] `BusinessException` 핸들러가 있는가?
- [ ] `MethodArgumentNotValidException` 핸들러가 있는가?
- [ ] `IllegalArgumentException` 핸들러가 있는가?
- [ ] `IllegalStateException` 핸들러가 있는가?
- [ ] `Exception` (일반 예외) 핸들러가 있는가?

### 응답 형식
- [ ] `ErrorResponse`를 표준화했는가?
- [ ] HTTP 상태 코드를 적절히 반환하는가?
- [ ] Validation 에러 시 FieldError 목록을 포함하는가?

### 보안
- [ ] 예상치 못한 예외의 상세 내용을 노출하지 않는가?
- [ ] 스택 트레이스를 클라이언트에 반환하지 않는가?
- [ ] 민감한 정보 (SQL, 내부 경로 등)를 노출하지 않는가?

### 로깅
- [ ] 예외 발생 시 로그를 남기는가?
- [ ] 심각한 예외는 ERROR 레벨로 로깅하는가?
- [ ] 비즈니스 예외는 WARN 레벨로 로깅하는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
