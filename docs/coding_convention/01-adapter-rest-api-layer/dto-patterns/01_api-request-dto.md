# API Request DTO - API 요청 DTO 설계 패턴

> **목적**: REST API Adapter Layer의 Request DTO 설계 원칙 및 구현 패턴
>
> **위치**: `adapter/in/web/dto/`
>
> **관련 문서**:
> - `controller-design/02_request-validation.md` (요청 검증 전략)
> - `controller-design/03_response-handling.md` (응답 처리)
> - `02_api-response-dto.md` (Response DTO)
> - `03-application-layer/dto-patterns/01_request-response-dto.md` (전체 흐름)
> - `06-java21-patterns/record-patterns/01_dto-with-records.md` (Record 패턴)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. API Request DTO의 역할

**Controller 계층에서 HTTP 요청을 표현하는 불변 객체**

```
HTTP Request → API Request DTO → Mapper → UseCase Command → Domain
```

**핵심 특성**:
- **불변성**: Java Record로 Thread-Safe 보장
- **검증**: Bean Validation으로 계층 분리
- **변환**: Mapper를 통해 UseCase Command로 변환
- **독립성**: Domain 로직과 완전히 분리

### 2. API Request DTO vs UseCase Command

| 구분 | API Request DTO | UseCase Command |
|------|----------------|-----------------|
| **위치** | `adapter/in/web/dto/` | `application/port/in/` |
| **역할** | HTTP 요청 표현 | 비즈니스 작업 표현 |
| **검증** | Bean Validation (형식) | 비즈니스 규칙 검증 |
| **변환** | Mapper가 Command로 변환 | Assembler가 Domain으로 변환 |
| **네이밍** | `~Request` | `~Command`, `~Query` |

---

## ❌ Anti-Pattern: 잘못된 Request DTO 설계

### 문제 1: 가변 DTO (Mutable Class)

```java
// ❌ Bad: Mutable Class with Setters
public class CreateOrderRequest {
    private Long customerId;
    private List<OrderItemDto> items;

    // Getter/Setter
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;  // ❌ 가변
    }
}
```

**문제점**:
- 🔴 Thread-Safe하지 않음
- 🔴 요청 데이터가 중간에 변경될 위험
- 🔴 불변성 보장 불가

### 문제 2: Lombok DTO

```java
// ❌ Bad: Lombok DTO (Zero-tolerance)
import lombok.Data;

@Data  // ❌ Lombok 절대 금지!
public class CreateOrderRequest {
    private Long customerId;
    private List<OrderItemDto> items;
}
```

**문제점**:
- 🔴 프로젝트 표준 위반 (Zero-tolerance)
- 🔴 가변 객체 생성
- 🔴 숨겨진 동작으로 디버깅 어려움

### 문제 3: 검증 없는 DTO

```java
// ❌ Bad: No Validation
public record CreateOrderRequest(
    Long customerId,        // ❌ null 가능
    List<OrderItemDto> items  // ❌ 빈 리스트 가능
) {}
```

**문제 시나리오**:
```json
// ❌ Invalid Request (검증 없으면 통과)
{
  "customerId": null,
  "items": []
}
```

### 문제 4: Domain 로직 포함

```java
// ❌ Bad: Business Logic in DTO
public record CreateOrderRequest(
    Long customerId,
    List<OrderItemDto> items
) {
    // ❌ DTO에 비즈니스 로직 금지!
    public BigDecimal calculateTotalPrice() {
        return items.stream()
            .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

**문제점**:
- 🔴 Adapter Layer에 비즈니스 로직
- 🔴 Domain 책임 침범
- 🔴 재사용 불가능한 로직

---

## ✅ Best Practice: Java Record + Bean Validation

### 패턴 1: 기본 Request DTO

```java
package com.company.adapter.in.web.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Create Order API Request DTO
 *
 * <p>HTTP POST /api/v1/orders 요청의 Request Body를 표현합니다.
 *
 * <p>검증 규칙:
 * <ul>
 *   <li>customerId: 필수, 양수</li>
 *   <li>items: 필수, 1개 이상</li>
 *   <li>notes: 선택적, 최대 500자</li>
 * </ul>
 *
 * @param customerId 고객 ID (필수)
 * @param items 주문 항목 리스트 (필수, 1개 이상)
 * @param notes 주문 메모 (선택적)
 *
 * @author Development Team
 * @since 1.0.0
 */
public record CreateOrderRequest(
    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    Long customerId,

    @NotEmpty(message = "Order items cannot be empty")
    @Valid  // ✅ 중첩 DTO 검증
    List<OrderItemRequest> items,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes
) {
    /**
     * Compact Constructor - 추가 검증 및 정규화
     */
    public CreateOrderRequest {
        // ✅ null 방어: notes가 null이면 빈 문자열로 변환
        if (notes == null) {
            notes = "";
        }

        // ✅ 추가 비즈니스 규칙 검증 (형식적 검증만)
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException("Invalid customer ID: " + customerId);
        }
    }

    /**
     * 중첩 DTO: Order Item Request
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

**핵심 요소**:
- ✅ **Java Record**: 불변성 보장
- ✅ **Bean Validation**: `@NotNull`, `@Positive`, `@NotEmpty`, `@Valid`
- ✅ **Compact Constructor**: 추가 검증 및 null 방어
- ✅ **중첩 DTO**: `OrderItemRequest` 내부 정의
- ✅ **Javadoc**: 각 필드 설명 포함

---

### 패턴 2: Controller에서 사용

```java
package com.company.adapter.in.web;

import com.company.adapter.in.web.dto.CreateOrderRequest;
import com.company.adapter.in.web.dto.OrderApiResponse;
import com.company.adapter.in.web.mapper.OrderApiMapper;
import com.company.application.port.in.CreateOrderUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Order REST API Controller
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            OrderApiMapper orderApiMapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderApiMapper = orderApiMapper;
    }

    /**
     * 주문 생성
     *
     * <p>변환 흐름:
     * <pre>
     * CreateOrderRequest → Mapper → CreateOrderUseCase.Command
     *                            ↓
     *                  UseCase 실행
     *                            ↓
     * CreateOrderUseCase.Response → Mapper → OrderApiResponse
     * </pre>
     *
     * @param request 주문 생성 요청 DTO
     * @return 생성된 주문 정보
     */
    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {  // ✅ @Valid로 자동 검증

        // ✅ 1. API Request DTO → UseCase Command 변환
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // ✅ 2. UseCase 실행
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // ✅ 3. UseCase Response → API Response DTO 변환
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", "/api/v1/orders/" + apiResponse.orderId())
            .body(apiResponse);
    }
}
```

**검증 실패 시 자동 응답** (GlobalExceptionHandler):
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

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
            fieldErrors
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
}
```

**검증 실패 응답 예시**:
```json
// Request
POST /api/v1/orders
{
  "customerId": null,
  "items": []
}

// Response: 400 Bad Request
{
  "code": "VALIDATION_FAILED",
  "message": "Validation failed",
  "timestamp": "2025-10-17T10:30:00",
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

---

## ✅ Mapper 패턴: API DTO → UseCase Command

### 패턴: OrderApiMapper (Adapter Layer)

```java
package com.company.adapter.in.web.mapper;

import com.company.adapter.in.web.dto.CreateOrderRequest;
import com.company.adapter.in.web.dto.CreateOrderRequest.OrderItemRequest;
import com.company.application.port.in.CreateOrderUseCase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Order API Mapper
 *
 * <p>Adapter Layer에서 API DTO를 UseCase DTO로 변환합니다.
 *
 * <p>변환 방향:
 * <ul>
 *   <li>API Request → UseCase Command (toCommand)</li>
 *   <li>UseCase Response → API Response (toApiResponse)</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 */
@Component
public class OrderApiMapper {

    /**
     * API Request → UseCase Command 변환
     *
     * @param request API Request DTO
     * @return UseCase Command
     */
    public CreateOrderUseCase.Command toCommand(CreateOrderRequest request) {
        List<CreateOrderUseCase.Command.OrderItem> items = request.items().stream()
            .map(this::toCommandItem)
            .toList();

        return new CreateOrderUseCase.Command(
            request.customerId(),
            items,
            request.notes()
        );
    }

    /**
     * OrderItemRequest → Command.OrderItem 변환
     */
    private CreateOrderUseCase.Command.OrderItem toCommandItem(OrderItemRequest item) {
        return new CreateOrderUseCase.Command.OrderItem(
            item.productId(),
            item.quantity()
        );
    }
}
```

---

## 🎯 실전 예제: 다양한 Request DTO 패턴

### 시나리오 1: Update Request (부분 업데이트)

```java
/**
 * Update Order API Request DTO
 *
 * <p>PATCH /api/v1/orders/{orderId} 요청의 Request Body를 표현합니다.
 *
 * <p>특징: 모든 필드가 선택적 (null 허용)
 *
 * @author Development Team
 * @since 1.0.0
 */
public record UpdateOrderRequest(
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes,

    @Pattern(regexp = "PENDING|CONFIRMED|SHIPPED|DELIVERED|CANCELLED",
             message = "Invalid status value")
    String status
) {
    /**
     * Compact Constructor - 최소 하나의 필드는 있어야 함
     */
    public UpdateOrderRequest {
        if (notes == null && status == null) {
            throw new IllegalArgumentException(
                "At least one field must be provided for update"
            );
        }
    }
}
```

**Controller 사용**:
```java
@PatchMapping("/{orderId}")
public ResponseEntity<OrderApiResponse> updateOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody UpdateOrderRequest request) {

    UpdateOrderUseCase.Command command = orderApiMapper.toUpdateCommand(orderId, request);
    UpdateOrderUseCase.Response response = updateOrderUseCase.updateOrder(command);
    OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

    return ResponseEntity.ok(apiResponse);
}
```

---

### 시나리오 2: Query Parameters (검색 조건)

```java
/**
 * Order Search Query Parameters
 *
 * <p>GET /api/v1/orders?customerId=1&status=PENDING 요청의 Query Parameters를 표현합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
public record OrderSearchRequest(
    Long customerId,

    @Pattern(regexp = "PENDING|CONFIRMED|SHIPPED|DELIVERED|CANCELLED",
             message = "Invalid status value")
    String status,

    @PastOrPresent(message = "Start date must be in the past or present")
    LocalDate startDate,

    @Future(message = "End date must be in the future")
    LocalDate endDate
) {
    /**
     * Compact Constructor - 날짜 범위 검증
     */
    public OrderSearchRequest {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
    }
}
```

**Controller 사용** (Query Parameters):
```java
@GetMapping
public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
        @Valid OrderSearchRequest searchRequest,  // ✅ @ModelAttribute 자동 적용
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

    GetOrdersQuery.Query query = orderApiMapper.toQuery(searchRequest, pageable);
    GetOrdersQuery.Response response = getOrdersQuery.getOrders(query);
    PageResponse<OrderSummaryApiResponse> apiResponse = orderApiMapper.toPageResponse(response);

    return ResponseEntity.ok(apiResponse);
}
```

---

### 시나리오 3: 파일 업로드 포함

```java
/**
 * Create Product with Image Request
 *
 * <p>Multipart Form Data 요청을 표현합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
public record CreateProductRequest(
    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    String name,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    BigDecimal price,

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    String description
) {}
```

**Controller** (Multipart Form Data):
```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ProductApiResponse> createProduct(
        @Valid @RequestPart("product") CreateProductRequest request,  // ✅ JSON part
        @RequestPart(value = "image", required = false) MultipartFile image) {  // ✅ File part

    // Image 검증
    if (image != null && !isValidImage(image)) {
        throw new InvalidImageException("Invalid image file");
    }

    CreateProductUseCase.Command command = productApiMapper.toCommand(request, image);
    CreateProductUseCase.Response response = createProductUseCase.createProduct(command);
    ProductApiResponse apiResponse = productApiMapper.toApiResponse(response);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .header("Location", "/api/v1/products/" + apiResponse.productId())
        .body(apiResponse);
}

private boolean isValidImage(MultipartFile file) {
    return file.getSize() <= 5_000_000  // 5MB
        && (file.getContentType().equals("image/jpeg")
            || file.getContentType().equals("image/png"));
}
```

---

### 시나리오 4: Batch Operations

```java
/**
 * Batch Delete Orders Request
 *
 * <p>여러 주문을 한 번에 취소하는 요청을 표현합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
public record BatchCancelOrdersRequest(
    @NotEmpty(message = "Order IDs cannot be empty")
    @Size(max = 100, message = "Cannot cancel more than 100 orders at once")
    List<@NotNull @Positive Long> orderIds,

    @NotBlank(message = "Cancellation reason is required")
    @Size(max = 200, message = "Reason cannot exceed 200 characters")
    String reason
) {
    /**
     * Compact Constructor - 중복 제거
     */
    public BatchCancelOrdersRequest {
        if (orderIds != null) {
            orderIds = orderIds.stream()
                .distinct()  // ✅ 중복 제거
                .toList();
        }
    }
}
```

**Controller**:
```java
@PostMapping("/batch/cancel")
public ResponseEntity<BatchOperationApiResponse> batchCancelOrders(
        @Valid @RequestBody BatchCancelOrdersRequest request) {

    CancelOrdersUseCase.Command command = orderApiMapper.toBatchCommand(request);
    CancelOrdersUseCase.Response response = cancelOrdersUseCase.cancelOrders(command);
    BatchOperationApiResponse apiResponse = orderApiMapper.toBatchResponse(response);

    return ResponseEntity.ok(apiResponse);
}
```

---

## 🔧 고급 패턴

### 패턴 1: Polymorphic Request DTO

```java
/**
 * Abstract Payment Request
 *
 * @author Development Team
 * @since 1.0.0
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "paymentType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreditCardPaymentRequest.class, name = "CREDIT_CARD"),
    @JsonSubTypes.Type(value = BankTransferPaymentRequest.class, name = "BANK_TRANSFER")
})
public sealed interface PaymentRequest
    permits CreditCardPaymentRequest, BankTransferPaymentRequest {

    String paymentType();
}

/**
 * Credit Card Payment Request
 */
public record CreditCardPaymentRequest(
    @NotBlank String cardNumber,
    @NotBlank String cvv,
    @NotBlank String expiryDate
) implements PaymentRequest {
    @Override
    public String paymentType() {
        return "CREDIT_CARD";
    }
}

/**
 * Bank Transfer Payment Request
 */
public record BankTransferPaymentRequest(
    @NotBlank String bankCode,
    @NotBlank String accountNumber
) implements PaymentRequest {
    @Override
    public String paymentType() {
        return "BANK_TRANSFER";
    }
}
```

**사용 예시**:
```json
// Credit Card Payment
{
  "paymentType": "CREDIT_CARD",
  "cardNumber": "1234-5678-9012-3456",
  "cvv": "123",
  "expiryDate": "12/25"
}

// Bank Transfer Payment
{
  "paymentType": "BANK_TRANSFER",
  "bankCode": "004",
  "accountNumber": "123-456-789"
}
```

---

### 패턴 2: Custom Validation Annotation

```java
/**
 * Phone Number Validation Annotation
 *
 * @author Development Team
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
public @interface PhoneNumber {
    String message() default "Invalid phone number format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

/**
 * Phone Number Validator
 */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{3}-\\d{4}-\\d{4}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;  // @NotNull로 별도 검증
        }
        return PHONE_PATTERN.matcher(value).matches();
    }
}
```

**Request DTO에 적용**:
```java
public record CreateCustomerRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Phone number is required")
    @PhoneNumber  // ✅ Custom Validation
    String phoneNumber,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email
) {}
```

---

## 📊 Jackson 직렬화 최적화

### 패턴: Jackson 어노테이션 최소화

```java
/**
 * Create Order Request with Jackson Annotations
 *
 * @author Development Team
 * @since 1.0.0
 */
public record CreateOrderRequest(
    @JsonProperty("customer_id")  // ✅ 외부 API와 필드명 매핑
    @NotNull Long customerId,

    @JsonAlias({"orderItems", "order_items"})  // ✅ 여러 필드명 허용
    @NotEmpty @Valid List<OrderItemRequest> items,

    @JsonInclude(JsonInclude.Include.NON_EMPTY)  // ✅ 빈 문자열 제외
    String notes
) {}
```

**요청 예시** (다양한 필드명 허용):
```json
// 케이스 1: camelCase
{
  "customer_id": 1,
  "orderItems": [...]
}

// 케이스 2: snake_case
{
  "customer_id": 1,
  "order_items": [...]
}
```

---

## 📋 API Request DTO 설계 체크리스트

### 기본 구조
- [ ] Java Record 사용하는가? (Lombok 금지)
- [ ] 불변성이 보장되는가?
- [ ] Javadoc이 모든 필드에 작성되어 있는가?
- [ ] `@author`, `@since` 태그가 있는가?

### Bean Validation
- [ ] `@NotNull`, `@NotBlank`, `@NotEmpty` 적용되어 있는가?
- [ ] `@Valid`로 중첩 DTO 검증하는가?
- [ ] `@Min`, `@Max`, `@Size` 등 범위 검증이 적절한가?
- [ ] Custom Validation이 필요한 경우 별도 Annotation으로 분리했는가?

### Compact Constructor
- [ ] 추가 검증 로직이 있는가?
- [ ] null 방어 코드가 있는가?
- [ ] 정규화 로직 (중복 제거, trim 등)이 있는가?

### 중첩 DTO
- [ ] 중첩 DTO가 내부 Record로 정의되어 있는가?
- [ ] `@Valid` 어노테이션이 적용되어 있는가?

### Jackson 통합
- [ ] Jackson 어노테이션이 최소화되어 있는가?
- [ ] `@JsonProperty`는 정말 필요한 경우만 사용하는가?
- [ ] `@JsonInclude`로 null/empty 필드 제외하는가?

### Mapper 통합
- [ ] OrderApiMapper가 Request → Command 변환을 담당하는가?
- [ ] Mapper가 `@Component`로 등록되어 있는가?

### 계층 분리
- [ ] DTO에 비즈니스 로직이 없는가?
- [ ] Domain 객체를 직접 참조하지 않는가?
- [ ] UseCase Command와 명확히 분리되어 있는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
