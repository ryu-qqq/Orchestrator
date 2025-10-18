# GlobalExceptionHandler - REST API Layer 전역 예외 처리

> **목적**: `@RestControllerAdvice`를 사용한 중앙 집중식 예외 처리 패턴
>
> **레이어**: Adapter Layer (REST API)
>
> **위치**: `adapter/in/web/exception/GlobalExceptionHandler.java`
>
> **관련 문서**:
> - `../dto-patterns/03_error-response.md` - ErrorResponse DTO 표준
> - `../../08-error-handling/02_domain-exception-design.md` - Domain 예외 설계
> - `../../08-error-handling/04_error-response-format.md` - 에러 응답 포맷
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. 중앙 집중식 예외 처리

**모든 Controller의 예외를 한 곳에서 처리**

```
Controller → 예외 발생
     ↓
@RestControllerAdvice
     ↓
GlobalExceptionHandler
     ↓
ErrorResponse + HTTP Status Code
     ↓
JSON Response
```

**장점**:
- 🎯 **일관성**: 모든 API가 동일한 에러 형식 반환
- 🔧 **유지보수성**: 에러 처리 로직 중앙 관리
- 📝 **가독성**: Controller는 비즈니스 로직에만 집중
- 🔒 **보안**: 민감 정보 노출 방지 로직 일원화

### 2. ErrorResponse DTO와 통합

**표준화된 에러 응답 형식 사용**

```java
// ✅ Good: 표준 ErrorResponse 사용
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handleBusinessException(...) {
    return ResponseEntity.status(ex.getHttpStatus())
        .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), path));
}

// ❌ Bad: 비표준 응답 형식
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Map<String, String>> handleBusinessException(...) {
    Map<String, String> error = Map.of("error", ex.getMessage());  // ❌ 비표준
    return ResponseEntity.status(500).body(error);
}
```

### 3. ErrorCode 인터페이스 의존

**Domain 구체 타입 의존 금지**

```java
// ✅ Good: 인터페이스 의존 (DIP)
ErrorCode errorCode = ex.getErrorCode();  // 인터페이스
int httpStatus = errorCode.getHttpStatus();

// ❌ Bad: 구체 타입 의존
OrderErrorCode errorCode = (OrderErrorCode) ex.getErrorCode();  // enum 타입 캐스팅
```

---

## ❌ Anti-Pattern: Controller별 개별 예외 처리

### 문제 1: 모든 Controller에서 try-catch 반복

```java
// ❌ Bad: 각 Controller에서 개별 처리
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable Long orderId) {
        try {
            Order order = orderService.getOrder(orderId);
            return ResponseEntity.ok(order);

        } catch (OrderNotFoundException ex) {
            // ❌ 중복: 동일한 예외 처리 로직 반복
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            // ❌ 중복: 일반 예외 처리도 반복
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            // ...
        } catch (ValidationException ex) {
            // ❌ 중복: 모든 메서드에서 동일한 처리 반복
            return ResponseEntity.status(400).body(...);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(...);
        }
    }
}
```

**문제점**:
- 🔴 **코드 중복**: 모든 Controller와 메서드에서 동일한 예외 처리 반복
- 🔴 **유지보수성 저하**: 에러 응답 형식 변경 시 모든 Controller 수정 필요
- 🔴 **일관성 부족**: Controller마다 다른 에러 응답 형식 가능
- 🔴 **가독성 저하**: 비즈니스 로직과 예외 처리 코드 혼재

### 문제 2: 비표준 에러 응답

```java
// ❌ Bad: Controller마다 다른 에러 응답 형식
@RestController
public class OrderController {
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        try {
            // ...
        } catch (OrderNotFoundException ex) {
            // ❌ Map 사용
            return ResponseEntity.status(404)
                .body(Map.of("error", ex.getMessage()));
        }
    }
}

@RestController
public class CustomerController {
    @GetMapping("/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable Long id) {
        try {
            // ...
        } catch (CustomerNotFoundException ex) {
            // ❌ 다른 형식 사용
            return ResponseEntity.status(404)
                .body(new ErrorDto(ex.getMessage()));
        }
    }
}
```

---

## ✅ Best Practice: GlobalExceptionHandler

### 패턴: @RestControllerAdvice 기반 중앙 처리

```java
package com.company.adapter.in.web.exception;

import com.company.adapter.in.web.dto.ErrorResponse;
import com.company.domain.shared.exception.BusinessException;
import com.company.domain.shared.exception.ErrorCode;
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
 * REST API Layer 전역 예외 핸들러
 *
 * <p>모든 Controller에서 발생하는 예외를 중앙에서 처리합니다.
 * Domain Layer의 BusinessException을 표준 ErrorResponse로 변환합니다.
 *
 * <p><b>핵심 원칙</b>:
 * <ul>
 *   <li>ErrorCode 인터페이스에만 의존 (DIP)</li>
 *   <li>ErrorResponse DTO 표준 사용</li>
 *   <li>민감 정보 노출 방지</li>
 *   <li>적절한 로깅 레벨 사용</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 * @see ErrorResponse
 * @see BusinessException
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * BusinessException 처리
     *
     * <p>Domain Layer에서 발생한 모든 비즈니스 예외를 처리합니다.
     * ErrorCode 인터페이스를 통해 HTTP 상태 코드를 추출하고,
     * 표준 ErrorResponse 형식으로 변환합니다.
     *
     * <p><b>처리 흐름</b>:
     * <pre>
     * 1. ErrorCode에서 HTTP 상태 코드 추출
     * 2. 로그 기록 (WARN 레벨)
     * 3. ErrorResponse.of() 생성
     * 4. ResponseEntity 반환
     * </pre>
     *
     * @param ex BusinessException (OrderNotFoundException, InsufficientStockException 등)
     * @param request HttpServletRequest (URI 추출용)
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
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    /**
     * Bean Validation 예외 처리
     *
     * <p>{@code @Valid} 어노테이션으로 발생하는 검증 실패를 처리합니다.
     * FieldError 목록을 ErrorResponse에 포함하여 반환합니다.
     *
     * <p><b>예시 시나리오</b>:
     * <pre>
     * public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRequest request) {
     *     // customerId가 null → MethodArgumentNotValidException
     *     // items가 empty → MethodArgumentNotValidException
     * }
     * </pre>
     *
     * @param ex MethodArgumentNotValidException
     * @param request HttpServletRequest
     * @return 400 Bad Request with validation errors
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

        log.warn("Validation failed: {} field errors at path={}",
                fieldErrors.size(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
            "VALIDATION_FAILED",
            "Request validation failed",
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
     * <p>잘못된 인자로 인한 예외를 처리합니다.
     * 주로 컴팩트 생성자나 메서드 파라미터 검증 실패 시 발생합니다.
     *
     * @param ex IllegalArgumentException
     * @param request HttpServletRequest
     * @return 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Illegal argument: message={}, path={}",
                ex.getMessage(), request.getRequestURI());

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
     * <p>잘못된 상태에서의 작업 시도 예외를 처리합니다.
     * 예: 이미 취소된 주문을 다시 취소하려는 경우
     *
     * @param ex IllegalStateException
     * @param request HttpServletRequest
     * @return 409 Conflict
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {

        log.warn("Illegal state: message={}, path={}",
                ex.getMessage(), request.getRequestURI());

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
     * 예상치 못한 예외 처리 (Fallback Handler)
     *
     * <p>위에서 처리되지 않은 모든 예외를 처리합니다.
     * 상세 에러는 로그에만 기록하고, 클라이언트에는 일반 메시지만 반환합니다.
     *
     * <p><b>보안 고려사항</b>:
     * <ul>
     *   <li>스택 트레이스를 클라이언트에 노출하지 않음</li>
     *   <li>민감한 정보 (SQL, 경로 등)를 숨김</li>
     *   <li>상세 내용은 로그에만 기록</li>
     * </ul>
     *
     * @param ex Exception
     * @param request HttpServletRequest
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error occurred at path={}", request.getRequestURI(), ex);

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

**핵심 특징**:
1. ✅ `@RestControllerAdvice` - 모든 Controller 예외 처리
2. ✅ `ErrorResponse` - 표준화된 에러 응답 (Phase 1-7 참조)
3. ✅ `ErrorCode` 인터페이스 의존 - DIP 준수
4. ✅ 적절한 로깅 레벨 - WARN (비즈니스), ERROR (예상 외)
5. ✅ 민감 정보 보호 - 상세 내용은 로그에만

---

## 🎯 예외 타입별 처리 전략

### 1. BusinessException (Domain 예외)

**처리**: ErrorCode의 HTTP 상태 코드 사용

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException ex,
        HttpServletRequest request) {

    ErrorCode errorCode = ex.getErrorCode();  // ✅ 인터페이스

    ErrorResponse response = ErrorResponse.of(
        errorCode.getCode(),      // "ORDER-001"
        ex.getMessage(),          // "Order not found: 999"
        request.getRequestURI()   // "/api/v1/orders/999"
    );

    return ResponseEntity
        .status(errorCode.getHttpStatus())  // ✅ ErrorCode에서 HTTP 상태 추출
        .body(response);
}
```

**예시 요청/응답**:
```http
GET /api/v1/orders/999
```

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

### 2. MethodArgumentNotValidException (Bean Validation 실패)

**처리**: 400 Bad Request + FieldError 목록

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {

    // BindingResult에서 FieldError 추출
    List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> new ErrorResponse.FieldError(
            error.getField(),           // "customerId"
            error.getRejectedValue(),   // null
            error.getDefaultMessage()   // "Customer ID is required"
        ))
        .toList();

    ErrorResponse response = ErrorResponse.of(
        "VALIDATION_FAILED",
        "Request validation failed",
        request.getRequestURI(),
        fieldErrors  // ✅ 필드별 에러 포함
    );

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(response);
}
```

**예시 요청/응답**:
```http
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": null,
  "items": []
}
```

```json
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders",
  "errors": [
    {
      "field": "customerId",
      "rejectedValue": null,
      "message": "Customer ID is required"
    },
    {
      "field": "items",
      "rejectedValue": [],
      "message": "Order items cannot be empty"
    }
  ]
}
```

### 3. IllegalArgumentException (잘못된 인자)

**처리**: 400 Bad Request

**발생 시나리오**:
- Java Record 컴팩트 생성자 검증 실패
- 메서드 파라미터 검증 실패

```java
// Record 컴팩트 생성자
public record CreateOrderRequest(
    Long customerId,
    List<OrderItemRequest> items
) {
    public CreateOrderRequest {
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException("Customer ID must be positive");
        }
    }
}
```

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

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
}
```

**예시 응답**:
```json
{
  "code": "INVALID_ARGUMENT",
  "message": "Customer ID must be positive",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders"
}
```

### 4. IllegalStateException (잘못된 상태)

**처리**: 409 Conflict

**발생 시나리오**:
- 이미 취소된 주문을 다시 취소하려는 경우
- 이미 완료된 작업을 재시도하는 경우

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

    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
}
```

### 5. Exception (예상치 못한 예외)

**처리**: 500 Internal Server Error + 보안 강화

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleInternalServerError(
        Exception ex,
        HttpServletRequest request) {

    log.error("Unexpected error occurred", ex);  // ✅ 상세 에러는 로그에만

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

## 🔒 보안 고려사항

### 1. 민감한 정보 노출 방지

```java
// ✅ Good: 일반 메시지만 반환
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleInternalServerError(
        Exception ex,
        HttpServletRequest request) {

    log.error("Unexpected error", ex);  // ✅ 상세 내용은 로그에만

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred"  // ✅ 일반 메시지
        ));
}

// ❌ Bad: 스택 트레이스 노출
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleInternalServerError(
        Exception ex,
        HttpServletRequest request) {

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            ex.toString()  // ❌ 스택 트레이스 노출 위험
        ));
}
```

### 2. SQL Injection 정보 노출 방지

```java
// ✅ Good: DB 에러는 일반 메시지로 변환
@ExceptionHandler(DataAccessException.class)
public ResponseEntity<ErrorResponse> handleDataAccessException(
        DataAccessException ex,
        HttpServletRequest request) {

    log.error("Database error", ex);  // ✅ 로그에만 상세 내용

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of(
            "DATABASE_ERROR",
            "A database error occurred"  // ✅ SQL 쿼리 노출 방지
        ));
}

// ❌ Bad: SQL 정보 노출
@ExceptionHandler(DataAccessException.class)
public ResponseEntity<ErrorResponse> handleDataAccessException(
        DataAccessException ex,
        HttpServletRequest request) {

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of(
            "DATABASE_ERROR",
            ex.getMessage()  // ❌ SQL 쿼리 노출 위험
        ));
}
```

---

## 🎯 실전 예제: 완전한 예외 처리 흐름

### 시나리오: 주문 생성 API

```java
// 1. Controller (Adapter Layer)
@RestController
@RequestMapping("/api/v1/orders")
public class OrderCommandController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {  // ✅ @Valid

        // Mapper: API Request → UseCase Command
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // UseCase 실행 (예외 발생 가능)
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // Mapper: UseCase Response → API Response
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/api/v1/orders/" + apiResponse.orderId())
            .body(apiResponse);
    }
}

// 2. UseCase (Application Layer)
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    private final LoadProductPort loadProductPort;
    private final SaveOrderPort saveOrderPort;

    @Override
    public Response createOrder(Command command) {
        // Product 조회 (예외 발생 가능: ProductNotFoundException)
        Product product = loadProductPort.findById(command.productId())
            .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        // Order 생성
        Order order = Order.create(command.customerId());

        // 재고 검증 (예외 발생 가능: InsufficientStockException)
        product.checkStock(command.quantity());

        // Order에 항목 추가
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

### 예외 발생 시 흐름

**시나리오 1: Bean Validation 실패**

```
Controller (@Valid 검증)
    ↓
CreateOrderRequest.customerId = null
    ↓
MethodArgumentNotValidException 발생
    ↓
GlobalExceptionHandler.handleValidationException()
    ↓
400 Bad Request + FieldError 목록
```

**응답**:
```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders",
  "errors": [
    {
      "field": "customerId",
      "rejectedValue": null,
      "message": "Customer ID is required"
    }
  ]
}
```

**시나리오 2: Product 찾지 못함**

```
UseCase.createOrder()
    ↓
loadProductPort.findById(999)
    ↓
ProductNotFoundException 발생
    ↓
GlobalExceptionHandler.handleBusinessException()
    ↓
404 Not Found + ErrorResponse
```

**응답**:
```json
{
  "code": "PRODUCT-001",
  "message": "Product not found: 999",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders"
}
```

**시나리오 3: 재고 부족**

```
UseCase.createOrder()
    ↓
product.checkStock(100)
    ↓
InsufficientStockException 발생
    ↓
GlobalExceptionHandler.handleBusinessException()
    ↓
409 Conflict + ErrorResponse
```

**응답**:
```json
{
  "code": "PRODUCT-002",
  "message": "Insufficient stock: requested=100, available=50",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders"
}
```

---

## 📐 계층별 역할 분담

### Adapter Layer (GlobalExceptionHandler)

**책임**:
- ✅ 모든 예외를 HTTP 응답으로 변환
- ✅ ErrorResponse DTO 생성
- ✅ HTTP 상태 코드 설정
- ✅ 로깅

**금지**:
- ❌ 비즈니스 로직 포함
- ❌ Domain 구체 타입 의존
- ❌ 예외 생성 (Domain/Application에서 생성)

### Domain Layer (BusinessException)

**책임**:
- ✅ 비즈니스 예외 정의
- ✅ ErrorCode 인터페이스 구현
- ✅ HTTP 상태 코드 결정

**금지**:
- ❌ HTTP 응답 생성
- ❌ ErrorResponse DTO 생성

### Application Layer (UseCase)

**책임**:
- ✅ 비즈니스 예외 발생
- ✅ 트랜잭션 경계 관리

**금지**:
- ❌ HTTP 예외 처리
- ❌ ErrorResponse 생성

---

## 📋 GlobalExceptionHandler 체크리스트

### 기본 구조
- [ ] `@RestControllerAdvice` 어노테이션을 사용하는가?
- [ ] 모든 예외를 중앙에서 처리하는가?
- [ ] `ErrorCode` 인터페이스에만 의존하는가?
- [ ] Domain 구체 타입 의존을 피하는가?

### 예외 타입별 처리
- [ ] `BusinessException` 핸들러가 있는가?
- [ ] `MethodArgumentNotValidException` 핸들러가 있는가?
- [ ] `IllegalArgumentException` 핸들러가 있는가?
- [ ] `IllegalStateException` 핸들러가 있는가?
- [ ] `Exception` (Fallback) 핸들러가 있는가?

### 응답 형식
- [ ] 표준 `ErrorResponse` DTO를 사용하는가?
- [ ] HTTP 상태 코드를 적절히 반환하는가?
- [ ] Validation 에러 시 `FieldError` 목록을 포함하는가?
- [ ] `timestamp`와 `path`를 포함하는가?

### 보안
- [ ] 예상치 못한 예외의 상세 내용을 노출하지 않는가?
- [ ] 스택 트레이스를 클라이언트에 반환하지 않는가?
- [ ] 민감한 정보 (SQL, 내부 경로 등)를 노출하지 않는가?
- [ ] DB 에러 시 SQL 쿼리를 숨기는가?

### 로깅
- [ ] 예외 발생 시 로그를 남기는가?
- [ ] 심각한 예외는 `ERROR` 레벨로 로깅하는가?
- [ ] 비즈니스 예외는 `WARN` 레벨로 로깅하는가?
- [ ] 로그에 `code`, `message`, `path`를 포함하는가?

### 통합
- [ ] `ErrorResponse` DTO 표준과 일치하는가? (`../dto-patterns/03_error-response.md`)
- [ ] Domain ErrorCode 설계와 호환되는가?
- [ ] Controller에서 try-catch를 제거했는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
