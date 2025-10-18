# Request/Response DTO - API 계층 DTO 패턴

**목적**: API 계층 (Adapter Layer)의 Request/Response DTO 설계

**위치**: `adapter/in/web/dto/` (Adapter Layer)

**관련 문서**:
- [UseCase 내부 DTO](../assembler-pattern/02_usecase-inner-dto.md)
- [Assembler Responsibility](../assembler-pattern/01_assembler-responsibility.md)
- [DTO Validation](./03_dto-validation.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## ⚠️ 중요: Adapter Layer DTO

이 문서는 **Adapter Layer (Controller)**의 API Request/Response DTO를 다룹니다.
- **API DTO** (이 문서): `OrderApiRequest`, `OrderApiResponse`
- **UseCase DTO** (Application Layer): `CreateOrderUseCase.Command`, `CreateOrderUseCase.Response`

**변환 흐름**:
```
API Request → Mapper → UseCase.Command → Assembler → Domain
Domain → Assembler → UseCase.Response → Mapper → API Response
```

---

## 📌 핵심 원칙

### Request/Response DTO란?

1. **API 계층**: Controller에서 사용
2. **불변 객체**: Java Record 사용
3. **변환 책임**: Mapper가 Command/Query로 변환

---

## ❌ DTO 안티패턴

### 문제 1: Entity 직접 노출

```java
// ❌ Before - Entity를 API Response로 직접 사용
@RestController
@RequestMapping("/orders")
public class OrderController {

    /**
     * ❌ 문제점:
     * - Entity 내부 구조가 API에 노출됨
     * - Lazy Loading으로 인한 N+1 문제
     * - 순환 참조 위험 (Jackson 에러)
     * - API 변경 시 Entity 변경 필요 (강결합)
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElseThrow();

        // ⚠️ Lazy Loading 발생 시 JSON 직렬화 에러
        return ResponseEntity.ok(order);
    }
}
```

**문제 시나리오**:
```json
{
  "id": 123,
  "customer": {
    "id": 456,
    "orders": [  // ⚠️ 순환 참조!
      { "id": 123, "customer": { ... } }
    ]
  },
  "lineItems": null  // ⚠️ Lazy Loading 에러
}
```

---

## ✅ API Request DTO 패턴

### 패턴: Record + Validation (Adapter Layer)

```java
package com.company.adapter.in.web.dto;

import jakarta.validation.constraints.*;

/**
 * Create Order API Request DTO (Adapter Layer)
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderApiRequest(
    @NotNull(message = "Customer ID is required")
    Long customerId,

    @NotEmpty(message = "Order items cannot be empty")
    @Valid
    List<OrderItemRequest> items,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes
) {

    /**
     * ✅ Compact Constructor - 추가 검증
     */
    public CreateOrderRequest {
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException("Invalid customer ID: " + customerId);
        }
    }

    /**
     * ✅ Nested DTO - OrderItem
     */
    public record OrderItemRequest(
        @NotNull(message = "Product ID is required")
        Long productId,

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1000, message = "Quantity cannot exceed 1000")
        Integer quantity
    ) {}
}
```

```java
/**
 * Controller - API Request DTO 사용
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    /**
     * ✅ 전체 흐름:
     * 1. API Request → Mapper → UseCase.Command
     * 2. UseCase 실행 → UseCase.Response
     * 3. UseCase.Response → Mapper → API Response
     */
    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody OrderApiRequest request) {

        // ✅ 1. Adapter Mapper: API Request → UseCase.Command
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // ✅ 2. UseCase 실행
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // ✅ 3. Adapter Mapper: UseCase.Response → API Response
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }
}
```

---

## ✅ Response DTO 패턴

### 패턴: from() 정적 팩토리 메서드

```java
package com.company.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/**
 * Order Response DTO
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderResponse(
    Long orderId,
    Long customerId,
    String customerName,
    List<OrderLineItemResponse> items,
    String totalAmount,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant createdAt
) {

    /**
     * ✅ from() - Entity에서 DTO 변환
     */
    public static OrderResponse from(Order order, Customer customer) {
        return new OrderResponse(
            order.getId().value(),
            customer.getId().value(),
            customer.getName(),
            order.getLineItems().stream()
                .map(OrderLineItemResponse::from)
                .toList(),
            order.getTotalAmount().toString(),
            order.getStatus().name(),
            order.getCreatedAt()
        );
    }

    /**
     * ✅ Nested DTO - OrderLineItem
     */
    public record OrderLineItemResponse(
        Long productId,
        String productName,
        Integer quantity,
        String unitPrice,
        String subtotal
    ) {
        public static OrderLineItemResponse from(OrderLineItem lineItem, Product product) {
            return new OrderLineItemResponse(
                lineItem.getProductId().value(),
                product.getName(),
                lineItem.getQuantity(),
                lineItem.getUnitPrice().toString(),
                lineItem.getSubtotal().toString()
            );
        }
    }
}
```

---

## ✅ Adapter Mapper 패턴

### 패턴: API DTO ↔ UseCase DTO 변환 (Adapter Layer)

```java
package com.company.adapter.in.web.mapper;

import com.company.adapter.in.web.dto.OrderApiRequest;
import com.company.adapter.in.web.dto.OrderApiResponse;
import com.company.application.order.port.in.CreateOrderUseCase;
import org.springframework.stereotype.Component;

/**
 * Order API Mapper (Adapter Layer)
 * - API Request → UseCase.Command
 * - UseCase.Response → API Response
 *
 * ⚠️ 주의: Application Layer의 Assembler와 구분
 * - Mapper (Adapter): API DTO ↔ UseCase DTO
 * - Assembler (Application): UseCase DTO ↔ Domain
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderApiMapper {

    /**
     * ✅ API Request → UseCase.Command 변환
     */
    public CreateOrderUseCase.Command toCommand(OrderApiRequest request) {
        List<CreateOrderUseCase.Command.OrderItem> items = request.items().stream()
            .map(item -> new CreateOrderUseCase.Command.OrderItem(
                item.productId(),
                item.quantity(),
                item.unitPrice()
            ))
            .toList();

        return new CreateOrderUseCase.Command(
            request.customerId(),
            items,
            request.notes()
        );
    }

    /**
     * ✅ UseCase.Response → API Response 변환
     */
    public OrderApiResponse toApiResponse(CreateOrderUseCase.Response response) {
        return new OrderApiResponse(
            response.orderId(),
            response.status(),
            response.totalAmount(),
            response.createdAt()
        );
    }
}
```

---

## ✅ Pagination Response 패턴

### 패턴: Page<T> 래핑

```java
/**
 * Paginated Response DTO
 */
public record PageResponse<T>(
    List<T> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {

    /**
     * ✅ from() - Spring Page → PageResponse
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }
}

/**
 * Controller - Pagination
 */
@RestController
public class OrderController {

    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> getOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<Order> orders = orderRepository.findAll(pageable);

        // ✅ Page<Entity> → Page<DTO> → PageResponse<DTO>
        Page<OrderSummaryResponse> dtoPage = orders.map(OrderSummaryResponse::from);

        return ResponseEntity.ok(PageResponse.from(dtoPage));
    }
}
```

---

## 🎯 실전 예제: Error Response

### ✅ Example: 표준화된 에러 응답

```java
/**
 * Error Response DTO
 */
public record ErrorResponse(
    String code,
    String message,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant timestamp,
    List<FieldError> fieldErrors
) {

    /**
     * ✅ from() - Exception → ErrorResponse
     */
    public static ErrorResponse from(BusinessException ex) {
        return new ErrorResponse(
            ex.getErrorCode().name(),
            ex.getMessage(),
            Instant.now(),
            null
        );
    }

    /**
     * ✅ from() - MethodArgumentNotValidException
     */
    public static ErrorResponse from(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(
                error.getField(),
                error.getDefaultMessage()
            ))
            .toList();

        return new ErrorResponse(
            "VALIDATION_FAILED",
            "Validation failed",
            Instant.now(),
            fieldErrors
        );
    }

    public record FieldError(
        String field,
        String message
    ) {}
}
```

---

## 📋 Request/Response DTO 체크리스트

### Request DTO
- [ ] Java Record 사용하는가?
- [ ] Bean Validation (`@Valid`, `@NotNull`) 적용되어 있는가?
- [ ] Compact Constructor로 추가 검증하는가?

### Response DTO
- [ ] from() 정적 팩토리 메서드 사용하는가?
- [ ] Entity 내부 구조 노출 방지하는가?
- [ ] Jackson 어노테이션 최소화하는가?

### Mapper
- [ ] Request → Command 변환 로직 분리되어 있는가?
- [ ] Entity → Response 변환 로직 분리되어 있는가?
- [ ] N+1 문제 방지하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
