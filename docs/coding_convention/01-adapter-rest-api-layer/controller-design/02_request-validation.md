# API Request Validation - 요청 검증 전략

> **목적**: REST API Request DTO의 Bean Validation 활용 및 검증 전략
>
> **위치**: `adapter/in/rest-api-*/src/main/java/com/company/adapter/in/rest/[boundedContext]/dto/request/`
>
> **관련 문서**:
> - `package-guide/01_rest_api_package_guide.md` (전체 구조)
> - `controller-design/01_restful-api-design.md` (RESTful API 설계)
> - `dto-patterns/01_api-request-dto.md` (Request DTO 패턴)
> - `exception-handling/01_global-exception-handler.md` (검증 실패 처리)
> - `03-application-layer/dto-patterns/03_dto-validation.md` (검증 계층)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+, Jakarta Bean Validation 3.0+

---

## 📌 핵심 원칙

### 1. 검증 계층 분리

**Adapter Layer (REST API)**: 형식 검증 (Bean Validation)
- HTTP Request 형식 검증
- 필수 여부, 타입, 길이, 범위 등
- Jakarta Bean Validation 어노테이션 활용

**Application Layer (UseCase)**: 비즈니스 규칙 검증
- UseCase Command의 Compact Constructor
- 도메인 규칙 (중복 체크, 최소 금액 등)

**Domain Layer (Aggregate)**: 불변식 검증
- Aggregate 상태 일관성
- 엔티티 생명주기 규칙

### 2. Fail Fast 원칙

```
HTTP Request → @Valid 검증 → 실패 시 즉시 400 Bad Request
             ↓ 성공
        Controller → Mapper → UseCase
```

### 3. 사용자 친화적 에러 메시지

- 검증 실패 시 명확한 에러 메시지 제공
- 필드별 에러 정보 포함
- 다국어 지원 가능하도록 메시지 키 활용

---

## ❌ Anti-Pattern: 검증 누락

### 잘못된 설계 (검증 없는 Controller)

```java
// ❌ Bad: 검증 어노테이션 없음
@RestController
@RequestMapping("/api/v1/orders")
public class OrderCommandController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    /**
     * ❌ 문제점:
     * - @Valid 어노테이션 누락
     * - null 값, 빈 리스트, 음수 등 검증 안됨
     * - NPE, IllegalArgumentException 발생 가능
     * - 불친절한 500 Internal Server Error 반환
     */
    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @RequestBody CreateOrderRequest request) {  // ❌ @Valid 누락

        // ⚠️ request.customerId() == null 가능
        // ⚠️ request.items() == null 또는 empty 가능
        // ⚠️ request.items().get(0).quantity() == -1 가능

        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(orderApiMapper.toApiResponse(response));
    }
}
```

### 문제점
- ❌ **검증 누락**: null, 빈 값, 잘못된 범위 등 검증하지 않음
- ❌ **늦은 실패**: UseCase나 Domain에서 에러 발생 (Fail Slow)
- ❌ **불친절한 에러**: 500 Internal Server Error 또는 일반적인 에러 메시지
- ❌ **보안 취약**: SQL Injection, XSS 등에 취약할 수 있음

---

## ✅ 권장 패턴: Bean Validation 활용

### 1. Controller에서 @Valid 적용

```java
/**
 * 주문 Command API Controller
 *
 * <p>Bean Validation을 활용한 요청 검증:
 * <ul>
 *   <li>@Valid: Request DTO 자동 검증</li>
 *   <li>검증 실패 시: MethodArgumentNotValidException → 400 Bad Request</li>
 *   <li>GlobalExceptionHandler에서 에러 응답 변환</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 * @see CreateOrderRequest
 * @see GlobalExceptionHandler
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
     * @param request 주문 생성 요청 (검증 자동 실행)
     * @return 201 Created + 생성된 주문 정보
     * @throws MethodArgumentNotValidException 검증 실패 시 (자동 처리)
     */
    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {  // ✅ @Valid 적용

        // ✅ 여기 도달 시점에 이미 검증 완료
        // - request.customerId() != null, > 0
        // - request.items() != null, not empty, size <= 100
        // - 각 item.quantity() >= 1, <= 1000

        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/api/v1/orders/" + response.orderId())
            .body(orderApiMapper.toApiResponse(response));
    }

    /**
     * 주문 상태 변경
     *
     * @param orderId 주문 ID (Path Variable 검증)
     * @param request 상태 변경 요청 (Body 검증)
     * @return 200 OK + 수정된 주문 정보
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderApiResponse> updateOrderStatus(
            @PathVariable @Positive(message = "Order ID must be positive") Long orderId,  // ✅ Path Variable 검증
            @Valid @RequestBody UpdateOrderStatusRequest request) {  // ✅ Body 검증

        UpdateOrderUseCase.Command command = orderApiMapper.toUpdateCommand(orderId, request);
        UpdateOrderUseCase.Response response = updateOrderUseCase.updateOrderStatus(command);

        return ResponseEntity.ok(orderApiMapper.toApiResponse(response));
    }
}
```

### 2. Request DTO에 Bean Validation 적용

```java
package com.company.adapter.in.rest.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * 주문 생성 요청 DTO
 *
 * <p>Bean Validation 검증:
 * <ul>
 *   <li>customerId: 필수, 양수</li>
 *   <li>items: 필수, 비어있지 않음, 최대 100개, 중첩 검증</li>
 *   <li>notes: 선택, 최대 500자</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 */
public record CreateOrderRequest(

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    Long customerId,

    @NotNull(message = "Order items are required")
    @NotEmpty(message = "Order items cannot be empty")
    @Size(max = 100, message = "Cannot order more than 100 items")
    @Valid  // ✅ 중첩 DTO 검증 (중요!)
    List<OrderItemRequest> items,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes

) {
    /**
     * 중첩 DTO: 주문 항목 요청
     */
    public record OrderItemRequest(

        @NotNull(message = "Product ID is required")
        @Positive(message = "Product ID must be positive")
        Long productId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1000, message = "Quantity cannot exceed 1000")
        Integer quantity,

        @NotNull(message = "Unit price is required")
        @PositiveOrZero(message = "Unit price must be zero or positive")
        Long unitPrice

    ) {}
}
```

### 3. Query Parameter 검증

```java
/**
 * 주문 Query API Controller
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated  // ✅ Query Parameter 검증에 필요
public class OrderQueryController {

    private final SearchOrdersQuery searchOrdersQuery;
    private final OrderApiMapper orderApiMapper;

    public OrderQueryController(
            SearchOrdersQuery searchOrdersQuery,
            OrderApiMapper orderApiMapper) {
        this.searchOrdersQuery = searchOrdersQuery;
        this.orderApiMapper = orderApiMapper;
    }

    /**
     * 주문 목록 조회
     *
     * @param customerId 고객 ID (optional)
     * @param status 주문 상태 (optional)
     * @param page 페이지 번호 (0 이상)
     * @param size 페이지 크기 (1-100)
     * @param sort 정렬 조건
     * @return 200 OK + 주문 목록
     */
    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
            @RequestParam(required = false)
            @Positive(message = "Customer ID must be positive")
            Long customerId,  // ✅ Query Parameter 검증

            @RequestParam(required = false)
            @Pattern(regexp = "DRAFT|CONFIRMED|SHIPPED|DELIVERED|CANCELLED",
                     message = "Invalid order status")
            String status,  // ✅ Enum 값 검증

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be zero or positive")
            int page,  // ✅ 페이지 번호 검증

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size cannot exceed 100")
            int size,  // ✅ 페이지 크기 검증

            @RequestParam(defaultValue = "createdAt,desc")
            String sort) {

        SearchOrdersQuery.Query query = SearchOrdersQuery.Query.builder()
            .customerId(customerId)
            .status(status)
            .page(page)
            .size(size)
            .sort(sort)
            .build();

        SearchOrdersQuery.Response response = searchOrdersQuery.searchOrders(query);
        PageResponse<OrderSummaryApiResponse> apiResponse = orderApiMapper.toPageApiResponse(response);

        return ResponseEntity.ok(apiResponse);
    }
}
```

---

## 🎯 Bean Validation 어노테이션 가이드

### 필수 값 검증

| 어노테이션 | 용도 | 예제 |
|-----------|------|------|
| **@NotNull** | null 불가 (모든 타입) | `@NotNull Long id` |
| **@NotEmpty** | null 불가 + 비어있지 않음 (Collection, String) | `@NotEmpty List<Item> items` |
| **@NotBlank** | null 불가 + 공백 아님 (String) | `@NotBlank String name` |

```java
public record CreateProductRequest(
    @NotNull(message = "Product name is required")
    @NotBlank(message = "Product name cannot be blank")
    String name,  // ✅ null도 안되고, 빈 문자열/공백도 안됨

    @NotNull(message = "Category is required")
    String category,  // ✅ null만 안됨, 빈 문자열은 가능

    @NotNull(message = "Tags are required")
    @NotEmpty(message = "At least one tag is required")
    List<String> tags  // ✅ null도 안되고, 빈 리스트도 안됨
) {}
```

### 숫자 범위 검증

| 어노테이션 | 용도 | 예제 |
|-----------|------|------|
| **@Positive** | 양수 (> 0) | `@Positive Long price` |
| **@PositiveOrZero** | 0 또는 양수 (>= 0) | `@PositiveOrZero Integer stock` |
| **@Negative** | 음수 (< 0) | `@Negative Integer adjustment` |
| **@NegativeOrZero** | 0 또는 음수 (<= 0) | `@NegativeOrZero Integer discount` |
| **@Min** | 최소값 | `@Min(1) Integer quantity` |
| **@Max** | 최대값 | `@Max(1000) Integer quantity` |
| **@DecimalMin** | 최소값 (BigDecimal) | `@DecimalMin("0.01") BigDecimal price` |
| **@DecimalMax** | 최대값 (BigDecimal) | `@DecimalMax("999999.99") BigDecimal price` |

```java
public record UpdateInventoryRequest(
    @NotNull(message = "Product ID is required")
    @Positive(message = "Product ID must be positive")
    Long productId,  // ✅ > 0

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 10000, message = "Quantity cannot exceed 10,000")
    Integer quantity,  // ✅ 1 <= quantity <= 10000

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    @DecimalMax(value = "9999999.99", message = "Price cannot exceed 9,999,999.99")
    BigDecimal price  // ✅ 0.01 <= price <= 9999999.99
) {}
```

### 문자열 검증

| 어노테이션 | 용도 | 예제 |
|-----------|------|------|
| **@Size** | 길이 범위 | `@Size(min=2, max=100) String name` |
| **@Email** | 이메일 형식 | `@Email String email` |
| **@Pattern** | 정규식 패턴 | `@Pattern(regexp="^01[0-9]{8,9}$") String phone` |

```java
public record RegisterUserRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$",
             message = "Username must contain only letters, numbers, and underscores")
    String username,  // ✅ 3-20자, 영문/숫자/언더스코어만

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,  // ✅ 이메일 형식

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 20, message = "Password must be 8-20 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$",
             message = "Password must contain letters, numbers, and special characters")
    String password,  // ✅ 8-20자, 영문+숫자+특수문자 포함

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^01[0-9]{8,9}$",
             message = "Invalid phone number format (010xxxxxxxx)")
    String phoneNumber  // ✅ 휴대폰 번호 형식
) {}
```

### Collection 검증

| 어노테이션 | 용도 | 예제 |
|-----------|------|------|
| **@Size** | Collection 크기 | `@Size(min=1, max=10) List<Item> items` |
| **@Valid** | 중첩 DTO 검증 | `@Valid List<OrderItemRequest> items` |

```java
public record CreateOrderRequest(
    @NotNull(message = "Items are required")
    @NotEmpty(message = "At least one item is required")
    @Size(max = 100, message = "Cannot order more than 100 items")
    @Valid  // ✅ 중첩 검증 (중요!)
    List<OrderItemRequest> items
) {
    public record OrderItemRequest(
        @NotNull @Positive Long productId,
        @NotNull @Min(1) @Max(1000) Integer quantity
    ) {}
}
```

### 날짜/시간 검증

| 어노테이션 | 용도 | 예제 |
|-----------|------|------|
| **@Past** | 과거 날짜 | `@Past LocalDate birthDate` |
| **@PastOrPresent** | 과거 또는 현재 | `@PastOrPresent LocalDate orderDate` |
| **@Future** | 미래 날짜 | `@Future LocalDate deliveryDate` |
| **@FutureOrPresent** | 미래 또는 현재 | `@FutureOrPresent LocalDate reservationDate` |

```java
public record ScheduleDeliveryRequest(
    @NotNull(message = "Delivery date is required")
    @Future(message = "Delivery date must be in the future")
    LocalDate deliveryDate,  // ✅ 미래 날짜만

    @NotNull(message = "Time slot is required")
    @Pattern(regexp = "^(09|10|11|12|13|14|15|16|17|18):00$",
             message = "Invalid time slot (09:00 - 18:00)")
    String timeSlot  // ✅ 09:00 ~ 18:00 정시
) {}
```

---

## 🔧 Custom Validator 패턴

### 복잡한 검증 로직 분리

```java
package com.company.adapter.in.rest.shared.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom Annotation - 영업일 검증
 *
 * <p>주말, 공휴일을 제외한 영업일만 허용
 *
 * @author Development Team
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BusinessDayValidator.class)
@Documented
public @interface BusinessDay {

    String message() default "Must be a business day (weekday, not holiday)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

```java
package com.company.adapter.in.rest.shared.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 영업일 검증 Validator
 *
 * @author Development Team
 * @since 1.0.0
 */
public class BusinessDayValidator implements ConstraintValidator<BusinessDay, LocalDate> {

    @Override
    public boolean isValid(LocalDate date, ConstraintValidatorContext context) {
        if (date == null) {
            return true;  // @NotNull로 별도 검증
        }

        // 주말 체크
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        // 공휴일 체크 (실제로는 외부 API나 DB 조회)
        // 여기서는 간단히 1월 1일만 체크
        if (date.getMonthValue() == 1 && date.getDayOfMonth() == 1) {
            return false;
        }

        return true;
    }
}
```

```java
/**
 * DTO에서 사용
 */
public record ScheduleDeliveryRequest(
    @NotNull(message = "Delivery date is required")
    @Future(message = "Delivery date must be in the future")
    @BusinessDay(message = "Delivery date must be a business day")  // ✅ Custom Validator
    LocalDate deliveryDate
) {}
```

### Cross-Field Validation (필드 간 검증)

```java
package com.company.adapter.in.rest.shared.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Cross-Field Validation - 시작일 < 종료일
 *
 * @author Development Team
 * @since 1.0.0
 */
@Target({ElementType.TYPE})  // ✅ 클래스 레벨에 적용
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
@Documented
public @interface ValidDateRange {

    String message() default "Start date must be before end date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String startField();
    String endField();
}
```

```java
package com.company.adapter.in.rest.shared.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;
import java.time.LocalDate;

/**
 * 날짜 범위 검증 Validator
 *
 * @author Development Team
 * @since 1.0.0
 */
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String startField;
    private String endField;

    @Override
    public void initialize(ValidDateRange annotation) {
        this.startField = annotation.startField();
        this.endField = annotation.endField();
    }

    @Override
    public boolean isValid(Object object, ConstraintValidatorContext context) {
        try {
            Field startFieldObj = object.getClass().getDeclaredField(startField);
            Field endFieldObj = object.getClass().getDeclaredField(endField);

            startFieldObj.setAccessible(true);
            endFieldObj.setAccessible(true);

            LocalDate startDate = (LocalDate) startFieldObj.get(object);
            LocalDate endDate = (LocalDate) endFieldObj.get(object);

            if (startDate == null || endDate == null) {
                return true;  // null은 @NotNull로 검증
            }

            return startDate.isBefore(endDate) || startDate.isEqual(endDate);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            return false;
        }
    }
}
```

```java
/**
 * DTO에서 사용 (클래스 레벨)
 */
@ValidDateRange(startField = "startDate", endField = "endDate",
                message = "Start date must be before or equal to end date")  // ✅ Cross-Field
public record SearchOrdersRequest(
    @NotNull(message = "Start date is required")
    @PastOrPresent(message = "Start date cannot be in the future")
    LocalDate startDate,

    @NotNull(message = "End date is required")
    @PastOrPresent(message = "End date cannot be in the future")
    LocalDate endDate
) {}
```

---

## 🚨 검증 실패 처리

### GlobalExceptionHandler 연계

```java
package com.company.adapter.in.rest.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;

/**
 * 글로벌 예외 처리 핸들러
 *
 * <p>Bean Validation 실패 시 자동으로 처리:
 * <ul>
 *   <li>MethodArgumentNotValidException: @RequestBody 검증 실패</li>
 *   <li>ConstraintViolationException: @RequestParam, @PathVariable 검증 실패</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * @RequestBody 검증 실패 처리
     *
     * @param ex MethodArgumentNotValidException
     * @return 400 Bad Request + 필드별 에러 정보
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ErrorResponse.FieldError(
                error.getField(),
                error.getRejectedValue(),
                error.getDefaultMessage()
            ))
            .toList();

        ErrorResponse errorResponse = new ErrorResponse(
            "VALIDATION_FAILED",
            "Request validation failed",
            Instant.now(),
            fieldErrors
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse);
    }

    /**
     * @RequestParam, @PathVariable 검증 실패 처리
     *
     * @param ex ConstraintViolationException
     * @return 400 Bad Request + 파라미터별 에러 정보
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations()
            .stream()
            .map(violation -> new ErrorResponse.FieldError(
                violation.getPropertyPath().toString(),
                violation.getInvalidValue(),
                violation.getMessage()
            ))
            .toList();

        ErrorResponse errorResponse = new ErrorResponse(
            "VALIDATION_FAILED",
            "Request validation failed",
            Instant.now(),
            fieldErrors
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse);
    }
}
```

### 에러 응답 예시

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2025-10-17T10:30:00Z",
  "fieldErrors": [
    {
      "field": "customerId",
      "rejectedValue": null,
      "message": "Customer ID is required"
    },
    {
      "field": "items",
      "rejectedValue": [],
      "message": "Order items cannot be empty"
    },
    {
      "field": "items[0].quantity",
      "rejectedValue": -1,
      "message": "Quantity must be at least 1"
    }
  ]
}
```

---

## 📋 실무 체크리스트

### Controller 검증
- [ ] `@Valid` 어노테이션을 `@RequestBody`에 적용했는가?
- [ ] `@Validated` 어노테이션을 클래스 레벨에 적용했는가? (Query Parameter 검증 시)
- [ ] `@PathVariable`, `@RequestParam`에 적절한 검증 어노테이션을 적용했는가?

### Request DTO 검증
- [ ] 필수 필드에 `@NotNull`, `@NotBlank`, `@NotEmpty`를 사용했는가?
- [ ] 숫자 범위를 `@Min`, `@Max`, `@Positive` 등으로 제한했는가?
- [ ] 문자열 길이를 `@Size`로 제한했는가?
- [ ] 이메일, 전화번호 등에 `@Email`, `@Pattern`을 사용했는가?
- [ ] 중첩 DTO에 `@Valid`를 적용했는가? (중요!)

### 에러 메시지
- [ ] 각 검증 어노테이션에 명확한 `message`를 지정했는가?
- [ ] 사용자가 이해하기 쉬운 메시지를 사용했는가?
- [ ] 메시지 키를 사용하여 다국어 지원이 가능한가? (선택)

### Custom Validator
- [ ] 복잡한 검증 로직을 Custom Validator로 분리했는가?
- [ ] Cross-Field Validation이 필요한 경우 구현했는가?
- [ ] Custom Validator가 재사용 가능하도록 설계되었는가?

### GlobalExceptionHandler
- [ ] `MethodArgumentNotValidException`을 처리하는가?
- [ ] `ConstraintViolationException`을 처리하는가?
- [ ] 에러 응답에 필드별 에러 정보를 포함하는가?
- [ ] 적절한 HTTP 상태 코드를 반환하는가? (400 Bad Request)

### 성능 고려
- [ ] 불필요한 검증을 중복으로 수행하지 않는가?
- [ ] Custom Validator가 외부 API나 DB 조회를 과도하게 하지 않는가?
- [ ] 검증 로직이 충분히 빠른가? (10ms 이내 권장)

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
