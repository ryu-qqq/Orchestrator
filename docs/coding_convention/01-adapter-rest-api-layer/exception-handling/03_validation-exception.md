# Validation Exception - Bean Validation 예외 처리

> **목적**: `@Valid` 검증 실패 시 표준화된 에러 응답 처리
>
> **레이어**: Adapter Layer (REST API)
>
> **위치**: `adapter/in/web/exception/GlobalExceptionHandler.java`
>
> **관련 문서**:
> - `../controller-design/02_request-validation.md` - API 요청 검증 전략
> - `../dto-patterns/01_api-request-dto.md` - API Request DTO 패턴
> - `../dto-patterns/03_error-response.md` - ErrorResponse DTO 표준
> - `./01_global-exception-handler.md` - GlobalExceptionHandler 구현
>
> **필수 버전**: Spring Boot 3.0+, Java 21+, Jakarta Bean Validation 3.0+

---

## 📌 핵심 원칙

### 1. Bean Validation 표준 활용

**Jakarta Bean Validation (JSR 380) 사용**

```java
// ✅ Good: Jakarta Bean Validation 어노테이션
import jakarta.validation.constraints.*;

public record CreateOrderRequest(
    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    Long customerId,

    @NotEmpty(message = "Order items cannot be empty")
    @Valid  // ✅ 중첩 객체 검증
    List<OrderItemRequest> items
) {}
```

### 2. FieldError 목록 포함

**검증 실패 시 필드별 에러 정보 반환**

```
@Valid 검증 실패
    ↓
MethodArgumentNotValidException 발생
    ↓
GlobalExceptionHandler.handleValidationException()
    ↓
ErrorResponse with FieldError 목록
```

**응답 형식**:
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
    },
    {
      "field": "items",
      "rejectedValue": [],
      "message": "Order items cannot be empty"
    }
  ]
}
```

### 3. 일관된 에러 응답 형식

**모든 검증 에러는 동일한 형식으로 반환**

```java
// ✅ Good: ErrorResponse 표준 사용
ErrorResponse response = ErrorResponse.of(
    "VALIDATION_FAILED",
    "Request validation failed",
    request.getRequestURI(),
    fieldErrors  // ✅ FieldError 목록 포함
);

// ❌ Bad: 비표준 응답 형식
Map<String, String> errors = new HashMap<>();
errors.put("error", "validation failed");  // ❌ 비표준
```

---

## ❌ Anti-Pattern: 개별 필드 검증

### 문제 1: Controller에서 수동 검증

```java
// ❌ Bad: Controller에서 if-else 수동 검증
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        // ❌ 수동 검증
        if (request.customerId() == null) {
            return ResponseEntity
                .badRequest()
                .body(Map.of("error", "Customer ID is required"));
        }

        if (request.customerId() <= 0) {
            return ResponseEntity
                .badRequest()
                .body(Map.of("error", "Customer ID must be positive"));
        }

        if (request.items() == null || request.items().isEmpty()) {
            return ResponseEntity
                .badRequest()
                .body(Map.of("error", "Order items cannot be empty"));
        }

        // 비즈니스 로직...
    }
}
```

**문제점**:
- 🔴 **코드 중복**: 모든 Controller에서 동일한 검증 로직 반복
- 🔴 **가독성 저하**: 비즈니스 로직과 검증 로직 혼재
- 🔴 **일관성 부족**: Controller마다 다른 에러 형식 가능
- 🔴 **유지보수성 저하**: 검증 규칙 변경 시 모든 Controller 수정 필요

### 문제 2: 비표준 에러 응답

```java
// ❌ Bad: 검증 실패 시 비표준 응답 반환
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        // ❌ Map 형식 (비표준)
        return ResponseEntity.status(400).body(errors);
    }
}
```

**응답** (비표준):
```json
{
  "customerId": "Customer ID is required",
  "items": "Order items cannot be empty"
}
```

**문제점**:
- 🔴 **비표준**: ErrorResponse DTO 표준 미준수
- 🔴 **정보 부족**: timestamp, path, code 누락
- 🔴 **클라이언트 혼란**: 다른 에러와 형식 불일치

---

## ✅ Best Practice: Bean Validation + GlobalExceptionHandler

### 패턴 1: Request DTO에서 @Valid 사용

```java
package com.company.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * 주문 생성 API Request DTO
 *
 * <p>Bean Validation 어노테이션으로 입력 검증을 선언적으로 처리합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
public record CreateOrderRequest(

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    Long customerId,

    @NotEmpty(message = "Order items cannot be empty")
    @Size(min = 1, max = 100, message = "Order items must be between 1 and 100")
    @Valid  // ✅ 중첩 객체 검증
    List<OrderItemRequest> items,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes

) {
    /**
     * Compact Constructor - 추가 검증
     */
    public CreateOrderRequest {
        // null-safe 처리
        if (notes == null) {
            notes = "";
        }
    }

    /**
     * 주문 항목 DTO
     */
    public record OrderItemRequest(

        @NotNull(message = "Product ID is required")
        @Positive(message = "Product ID must be positive")
        Long productId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1000, message = "Quantity cannot exceed 1000")
        Integer quantity

    ) {}
}
```

### 패턴 2: Controller에서 @Valid 적용

```java
package com.company.adapter.in.web.controller;

import com.company.adapter.in.web.dto.*;
import com.company.adapter.in.web.mapper.OrderApiMapper;
import com.company.application.port.in.CreateOrderUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 주문 Command API Controller
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderCommandController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    public OrderCommandController(
            CreateOrderUseCase createOrderUseCase,
            OrderApiMapper orderApiMapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderApiMapper = orderApiMapper;
    }

    /**
     * 주문 생성
     *
     * <p>{@code @Valid} 어노테이션이 Request DTO 검증을 자동 수행합니다.
     * 검증 실패 시 {@link org.springframework.web.bind.MethodArgumentNotValidException} 발생
     *
     * @param request 주문 생성 요청 DTO
     * @return 201 Created with OrderApiResponse
     */
    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {  // ✅ @Valid

        // Mapper: API Request → UseCase Command
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // UseCase 실행
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // Mapper: UseCase Response → API Response
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/api/v1/orders/" + apiResponse.orderId())
            .body(apiResponse);
    }
}
```

### 패턴 3: GlobalExceptionHandler에서 FieldError 추출

```java
package com.company.adapter.in.web.exception;

import com.company.adapter.in.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * REST API Layer 전역 예외 핸들러
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean Validation 예외 처리
     *
     * <p>{@code @Valid} 어노테이션으로 발생하는 검증 실패를 처리합니다.
     * {@link MethodArgumentNotValidException}에서 {@link FieldError} 목록을 추출하여
     * 표준 {@link ErrorResponse} 형식으로 반환합니다.
     *
     * <p><b>처리 흐름</b>:
     * <pre>
     * 1. BindingResult에서 FieldError 목록 추출
     * 2. ErrorResponse.FieldError로 변환
     * 3. ErrorResponse.of()로 표준 응답 생성
     * 4. 400 Bad Request 반환
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

        // BindingResult에서 FieldError 목록 추출
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ErrorResponse.FieldError(
                error.getField(),           // "customerId"
                error.getRejectedValue(),   // null
                error.getDefaultMessage()   // "Customer ID is required"
            ))
            .toList();

        log.warn("Validation failed: {} field errors at path={}",
                fieldErrors.size(), request.getRequestURI());

        // 상세 로깅 (디버깅용)
        if (log.isDebugEnabled()) {
            fieldErrors.forEach(fieldError ->
                log.debug("  - Field: {}, RejectedValue: {}, Message: {}",
                    fieldError.field(), fieldError.rejectedValue(), fieldError.message())
            );
        }

        ErrorResponse response = ErrorResponse.of(
            "VALIDATION_FAILED",
            "Request validation failed",
            request.getRequestURI(),
            fieldErrors  // ✅ FieldError 목록 포함
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
}
```

---

## 🎯 검증 실패 시나리오별 응답

### 시나리오 1: Required 필드 누락

**요청**:
```http
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": null,
  "items": []
}
```

**응답**:
```http
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

### 시나리오 2: 범위 검증 실패

**요청**:
```http
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": -1,
  "items": [
    {
      "productId": 123,
      "quantity": 0
    }
  ]
}
```

**응답**:
```http
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
      "rejectedValue": -1,
      "message": "Customer ID must be positive"
    },
    {
      "field": "items[0].quantity",
      "rejectedValue": 0,
      "message": "Quantity must be at least 1"
    }
  ]
}
```

### 시나리오 3: 중첩 객체 검증 실패

**요청**:
```http
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": 1,
  "items": [
    {
      "productId": null,
      "quantity": 1001
    },
    {
      "productId": 456,
      "quantity": null
    }
  ],
  "notes": "This note is too long... (501자 이상)"
}
```

**응답**:
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/orders",
  "errors": [
    {
      "field": "items[0].productId",
      "rejectedValue": null,
      "message": "Product ID is required"
    },
    {
      "field": "items[0].quantity",
      "rejectedValue": 1001,
      "message": "Quantity cannot exceed 1000"
    },
    {
      "field": "items[1].quantity",
      "rejectedValue": null,
      "message": "Quantity is required"
    },
    {
      "field": "notes",
      "rejectedValue": "This note is too long...",
      "message": "Notes cannot exceed 500 characters"
    }
  ]
}
```

---

## 🔧 Custom Validator 패턴 (선택적)

### 패턴: 커스텀 어노테이션 + Validator

**1. Custom Annotation 정의**

```java
package com.company.adapter.in.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 이메일 형식 검증 어노테이션
 *
 * @author Development Team
 * @since 1.0.0
 */
@Documented
@Constraint(validatedBy = EmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

    String message() default "Invalid email format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

**2. Validator 구현**

```java
package com.company.adapter.in.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 이메일 형식 검증 Validator
 *
 * @author Development Team
 * @since 1.0.0
 */
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;  // @NotNull과 별도로 처리
        }

        return EMAIL_PATTERN.matcher(value).matches();
    }
}
```

**3. Request DTO에서 사용**

```java
public record CreateCustomerRequest(

    @NotBlank(message = "Email is required")
    @ValidEmail(message = "Invalid email format")  // ✅ Custom Validator
    String email,

    @NotBlank(message = "Name is required")
    String name

) {}
```

**검증 실패 응답**:
```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2025-01-17T10:30:00",
  "path": "/api/v1/customers",
  "errors": [
    {
      "field": "email",
      "rejectedValue": "invalid-email",
      "message": "Invalid email format"
    }
  ]
}
```

---

## 📊 Bean Validation 어노테이션 레퍼런스

### Null 검증
| 어노테이션 | 설명 | 예시 |
|-----------|------|------|
| `@NotNull` | null 불가 | `@NotNull Long id` |
| `@Null` | null만 허용 | `@Null String ignored` |

### 문자열 검증
| 어노테이션 | 설명 | 예시 |
|-----------|------|------|
| `@NotEmpty` | null, 빈 문자열("") 불가 | `@NotEmpty String name` |
| `@NotBlank` | null, 빈 문자열, 공백만 불가 | `@NotBlank String email` |
| `@Size(min, max)` | 문자열 길이 제한 | `@Size(min=3, max=50) String name` |
| `@Pattern(regexp)` | 정규식 매칭 | `@Pattern(regexp="^[0-9]{3}-[0-9]{4}") String phone` |
| `@Email` | 이메일 형식 | `@Email String email` |

### 숫자 검증
| 어노테이션 | 설명 | 예시 |
|-----------|------|------|
| `@Positive` | 양수만 허용 | `@Positive Long id` |
| `@PositiveOrZero` | 0 또는 양수 | `@PositiveOrZero Integer quantity` |
| `@Negative` | 음수만 허용 | `@Negative BigDecimal discount` |
| `@NegativeOrZero` | 0 또는 음수 | `@NegativeOrZero Integer adjustment` |
| `@Min(value)` | 최소값 제한 | `@Min(1) Integer quantity` |
| `@Max(value)` | 최대값 제한 | `@Max(1000) Integer quantity` |
| `@DecimalMin(value)` | BigDecimal 최소값 | `@DecimalMin("0.01") BigDecimal price` |
| `@DecimalMax(value)` | BigDecimal 최대값 | `@DecimalMax("9999.99") BigDecimal price` |

### 컬렉션 검증
| 어노테이션 | 설명 | 예시 |
|-----------|------|------|
| `@NotEmpty` | null, 빈 컬렉션 불가 | `@NotEmpty List<Item> items` |
| `@Size(min, max)` | 컬렉션 크기 제한 | `@Size(min=1, max=100) List<Item> items` |

### 중첩 객체 검증
| 어노테이션 | 설명 | 예시 |
|-----------|------|------|
| `@Valid` | 중첩 객체 검증 전파 | `@Valid OrderItemRequest item` |

---

## 📋 Validation Exception 처리 체크리스트

### Request DTO 설계
- [ ] Bean Validation 어노테이션을 사용하는가?
- [ ] 검증 메시지가 명확하고 구체적인가?
- [ ] 중첩 객체에 `@Valid`를 적용했는가?
- [ ] Compact Constructor로 추가 검증을 수행하는가?

### Controller
- [ ] `@Valid` 어노테이션을 사용하는가?
- [ ] 수동 검증 로직이 없는가?
- [ ] Controller가 비즈니스 로직에만 집중하는가?

### GlobalExceptionHandler
- [ ] `MethodArgumentNotValidException` 핸들러가 있는가?
- [ ] FieldError 목록을 추출하는가?
- [ ] ErrorResponse DTO 표준을 사용하는가?
- [ ] 400 Bad Request 상태 코드를 반환하는가?

### ErrorResponse
- [ ] `code`를 "VALIDATION_FAILED"로 설정하는가?
- [ ] `errors` 필드에 FieldError 목록을 포함하는가?
- [ ] `timestamp`와 `path`를 포함하는가?

### 응답 형식
- [ ] 모든 검증 에러가 동일한 형식인가?
- [ ] 필드명, 거부값, 메시지가 모두 포함되는가?
- [ ] 중첩 객체 검증 실패 시 경로가 명확한가? (예: `items[0].quantity`)

### 로깅
- [ ] 검증 실패 시 로그를 남기는가?
- [ ] WARN 레벨로 로깅하는가?
- [ ] 디버깅을 위한 상세 로그가 있는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
