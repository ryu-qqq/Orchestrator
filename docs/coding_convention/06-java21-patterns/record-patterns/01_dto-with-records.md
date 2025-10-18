# DTO with Records - Java Record로 Request/Response DTO 설계

**목적**: Java 21 Record를 활용하여 불변 DTO를 간결하게 작성하고 Validation, Serialization 통합

**관련 문서**:
- [DTO Patterns](../../03-application-layer/dto-patterns/01_request-response-dto.md)
- [Value Objects with Records](./02_value-objects-with-records.md)

**필수 버전**: Java 21+, Spring Boot 3.3+

---

## 📌 핵심 원칙

### Record의 장점

1. **간결성**: Boilerplate 코드 제거 (Getter, Constructor, Equals, HashCode, ToString 자동 생성)
2. **불변성**: 모든 필드 `private final` (Thread-Safe)
3. **Validation**: Compact Constructor에서 검증
4. **Serialization**: Jackson 자동 지원

---

## ❌ 금지 패턴 (Lombok DTO)

```java
// ❌ Lombok - Zero-tolerance 정책
import lombok.Data;

@Data // ❌ Lombok 절대 금지!
public class OrderRequest {
    private Long customerId;
    private List<OrderLineItemDto> items;
}
```

**✅ Record로 대체**:
```java
// ✅ Java 21 Record
public record OrderRequest(
    Long customerId,
    List<OrderLineItemDto> items
) {}
```

---

## ✅ Record DTO 패턴

### 패턴 1: Request DTO with Validation

```java
package com.company.application.in.web.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Create Order Request DTO (Record)
 *
 * @param customerId 고객 ID (필수)
 * @param items 주문 항목 리스트 (1개 이상 필수)
 * @author development-team
 * @since 1.0.0
 */
public record CreateOrderRequest(
    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    Long customerId,

    @NotEmpty(message = "Order items must not be empty")
    @Valid
    List<OrderLineItemDto> items
) {
    /**
     * Compact Constructor (추가 검증)
     */
    public CreateOrderRequest {
        // items가 null이면 빈 리스트로 변환 (Optional)
        if (items == null) {
            items = List.of();
        }
    }
}
```

**핵심 기능**:
- ✅ Bean Validation (`@NotNull`, `@Positive`, `@NotEmpty`)
- ✅ Compact Constructor (추가 검증 또는 정규화)
- ✅ Javadoc으로 필드 설명

---

### 패턴 2: Response DTO with Factory Method

```java
package com.company.application.in.web.dto;

import com.company.domain.order.*;
import java.time.Instant;
import java.util.List;

/**
 * Order Response DTO (Record)
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderResponse(
    Long orderId,
    Long customerId,
    String status,
    List<OrderLineItemDto> items,
    Instant createdAt
) {
    /**
     * Domain → DTO 변환 (from 패턴)
     */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId().value(),
            order.getCustomerId().value(),
            order.getStatus().name(),
            order.getItems().stream()
                .map(OrderLineItemDto::from)
                .toList(),
            order.getCreatedAt()
        );
    }
}
```

**핵심 패턴**:
- ✅ `from()` 정적 팩토리 메서드
- ✅ Domain → DTO 단방향 변환
- ✅ Stream API + Record 조합

---

### 패턴 3: Nested Record DTO

```java
/**
 * Order Line Item DTO (Nested Record)
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderLineItemDto(
    @NotNull @Positive Long productId,
    @NotNull @Positive Integer quantity,
    @NotNull @DecimalMin("0.01") BigDecimal price
) {
    public static OrderLineItemDto from(OrderLineItem item) {
        return new OrderLineItemDto(
            item.getProductId().value(),
            item.getQuantity().value(),
            item.getPrice().amount()
        );
    }

    /**
     * DTO → Domain 변환 (toDomain 패턴)
     */
    public OrderLineItem toDomain() {
        return OrderLineItem.of(
            ProductId.of(productId),
            Quantity.of(quantity),
            Money.of(price)
        );
    }
}
```

---

## 🎯 실전 예제: Controller에서 Record DTO 사용

### ✅ Example 1: POST Request with Validation

```java
package com.company.application.in.web;

import com.company.application.in.web.dto.*;
import com.company.domain.port.in.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Order REST Controller (Record DTO 활용)
 *
 * @author development-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest request // ✅ Record DTO + @Valid
    ) {
        // DTO → Command 변환
        CreateOrderCommand command = new CreateOrderCommand(
            CustomerId.of(request.customerId()),
            request.items().stream()
                .map(OrderLineItemDto::toDomain)
                .toList()
        );

        // UseCase 실행
        OrderId orderId = createOrderUseCase.createOrder(command);

        // Response 생성
        OrderResponse response = new OrderResponse(
            orderId.value(),
            request.customerId(),
            "PENDING",
            request.items(),
            Instant.now()
        );

        return ResponseEntity
            .created(URI.create("/api/orders/" + orderId.value()))
            .body(response);
    }
}
```

**검증 결과**:
```json
// ❌ Invalid Request
{
  "customerId": null,
  "items": []
}

// Response 400 Bad Request
{
  "errorCode": "VALIDATION_FAILED",
  "fieldErrors": {
    "customerId": "Customer ID is required",
    "items": "Order items must not be empty"
  }
}
```

---

### ✅ Example 2: GET Response with Projection

```java
/**
 * Order Summary DTO (간략 정보용)
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderSummaryResponse(
    Long orderId,
    String status,
    BigDecimal totalPrice,
    Instant createdAt
) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
            order.getId().value(),
            order.getStatus().name(),
            order.getTotalPrice().amount(),
            order.getCreatedAt()
        );
    }
}

@GetMapping
public ResponseEntity<Page<OrderSummaryResponse>> listOrders(Pageable pageable) {
    Page<Order> orders = getOrdersQuery.getOrders(pageable);
    Page<OrderSummaryResponse> response = orders.map(OrderSummaryResponse::from);
    return ResponseEntity.ok(response);
}
```

---

## 🔧 고급 Record 패턴

### 패턴 1: Builder 패턴 (Record는 Builder 없음 - 대안)

```java
// ❌ Record는 Builder 패턴 불가 (불변)
// ✅ 대안: Named Parameters (Kotlin 스타일 모방)

/**
 * Order Filter Criteria (많은 Optional 파라미터)
 */
public record OrderFilterCriteria(
    Long customerId,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    Integer minAmount,
    Integer maxAmount
) {
    /**
     * Builder 대안: 정적 팩토리 메서드 체이닝
     */
    public static OrderFilterCriteria.Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long customerId;
        private String status;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer minAmount;
        private Integer maxAmount;

        public Builder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public OrderFilterCriteria build() {
            return new OrderFilterCriteria(
                customerId, status, startDate, endDate, minAmount, maxAmount
            );
        }
    }
}

// 사용 예시
OrderFilterCriteria criteria = OrderFilterCriteria.builder()
    .customerId(1L)
    .status("PENDING")
    .build();
```

---

### 패턴 2: Jackson Customization

```java
import com.fasterxml.jackson.annotation.*;
import java.time.Instant;

/**
 * Order Response with Jackson Annotations
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderResponse(
    @JsonProperty("id") // JSON 필드명 커스터마이징
    Long orderId,

    @JsonProperty("customer")
    Long customerId,

    @JsonInclude(JsonInclude.Include.NON_NULL) // null 필드 제외
    String status,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant createdAt
) {}
```

---

## 📋 Record DTO 체크리스트

### 기본 규칙
- [ ] Lombok 대신 Record 사용
- [ ] Bean Validation 애노테이션
- [ ] Compact Constructor (추가 검증)
- [ ] Javadoc 작성

### 변환 패턴
- [ ] `from()` 정적 팩토리 (Domain → DTO)
- [ ] `toXxx()` 인스턴스 메서드 (DTO → Domain)
- [ ] Stream API 활용

### Jackson 통합
- [ ] `@JsonProperty` (필드명 매핑)
- [ ] `@JsonInclude` (null 제외)
- [ ] `@JsonFormat` (날짜/시간)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
