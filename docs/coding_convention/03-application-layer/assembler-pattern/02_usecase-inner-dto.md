# UseCase 내부 Command/Response 패턴

**목적**: UseCase 인터페이스에 Command/Response를 내부 Record로 정의

**위치**: `application/[context]/port/in/`

**관련 문서**:
- [Assembler Responsibility](./01_assembler-responsibility.md)
- [Command UseCase](../usecase-design/01_command-usecase.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### UseCase 내부 DTO란?

1. **캡슐화**: Command/Response를 UseCase 인터페이스 내부에 Record로 정의
2. **응집도**: UseCase와 관련된 DTO를 한 곳에 모음
3. **명확성**: `XxxUseCase.Command`, `XxxUseCase.Response`로 명확한 소속 표현

---

## ❌ Anti-Pattern: DTO 분산

```java
// ❌ Before - DTO가 별도 파일로 분산
// CreateOrderCommand.java
public record CreateOrderCommand(
    Long customerId,
    List<OrderItem> items
) {}

// CreateOrderResponse.java
public record CreateOrderResponse(
    Long orderId,
    String status
) {}

// CreateOrderUseCase.java
public interface CreateOrderUseCase {
    CreateOrderResponse createOrder(CreateOrderCommand command);
}
```

**문제점**:
- 파일 3개로 분산 (UseCase, Command, Response)
- Command/Response의 소속이 불명확
- 패키지 구조 복잡도 증가

---

## ✅ UseCase 내부 Record 패턴

### 패턴: Command/Response를 내부 클래스로

```java
package com.company.application.order.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Create Order UseCase
 *
 * @author development-team
 * @since 1.0.0
 */
public interface CreateOrderUseCase {

    /**
     * ✅ Command 실행
     */
    Response createOrder(Command command);

    /**
     * ✅ Command - 내부 Record
     */
    record Command(
        Long customerId,
        List<OrderItem> items,
        String notes
    ) {
        /**
         * Compact Constructor - 검증
         */
        public Command {
            if (customerId == null || customerId <= 0) {
                throw new IllegalArgumentException("Invalid customer ID");
            }

            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Order items cannot be empty");
            }

            // 불변 리스트로 방어적 복사
            items = List.copyOf(items);
        }

        /**
         * ✅ Nested Record - OrderItem
         */
        public record OrderItem(
            Long productId,
            Integer quantity,
            Long unitPrice
        ) {
            public OrderItem {
                if (productId == null || productId <= 0) {
                    throw new IllegalArgumentException("Invalid product ID");
                }

                if (quantity == null || quantity <= 0) {
                    throw new IllegalArgumentException("Quantity must be positive");
                }

                if (unitPrice == null || unitPrice < 0) {
                    throw new IllegalArgumentException("Unit price cannot be negative");
                }
            }
        }
    }

    /**
     * ✅ Response - 내부 Record
     */
    record Response(
        Long orderId,
        String status,
        Long totalAmount,
        Instant createdAt
    ) {}
}
```

---

## ✅ Query UseCase 예제

```java
package com.company.application.order.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Get Order UseCase
 *
 * @author development-team
 * @since 1.0.0
 */
public interface GetOrderUseCase {

    /**
     * ✅ Query 실행
     */
    Response getOrder(Query query);

    /**
     * ✅ Query - 내부 Record (조회 조건)
     */
    record Query(
        Long orderId
    ) {
        public Query {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("Invalid order ID");
            }
        }
    }

    /**
     * ✅ Response - 내부 Record (조회 결과)
     */
    record Response(
        Long orderId,
        CustomerInfo customer,
        List<LineItem> items,
        Long totalAmount,
        String status,
        Instant createdAt
    ) {
        /**
         * ✅ Nested Record - CustomerInfo
         */
        public record CustomerInfo(
            Long customerId,
            String customerName,
            String email
        ) {}

        /**
         * ✅ Nested Record - LineItem
         */
        public record LineItem(
            Long productId,
            String productName,
            Integer quantity,
            Long unitPrice,
            Long subtotal
        ) {}
    }
}
```

---

## ✅ Adapter에서 UseCase 호출

```java
package com.company.adapter.in.web;

import com.company.adapter.in.web.dto.OrderApiRequest;
import com.company.adapter.in.web.dto.OrderApiResponse;
import com.company.adapter.in.web.mapper.OrderApiMapper;
import com.company.application.order.port.in.CreateOrderUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Order Controller
 *
 * @author development-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    public OrderController(
        CreateOrderUseCase createOrderUseCase,
        OrderApiMapper orderApiMapper
    ) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderApiMapper = orderApiMapper;
    }

    /**
     * ✅ 전체 흐름:
     * 1. API Request → Mapper → UseCase.Command
     * 2. UseCase 실행 → UseCase.Response
     * 3. UseCase.Response → Mapper → API Response
     */
    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
        @Valid @RequestBody OrderApiRequest request
    ) {
        // ✅ 1. Adapter Mapper: API Request → UseCase.Command
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // ✅ 2. UseCase 실행
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // ✅ 3. Adapter Mapper: UseCase.Response → API Response
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity.ok(apiResponse);
    }
}
```

---

## ✅ Adapter Mapper 예제

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
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderApiMapper {

    /**
     * ✅ API Request → UseCase.Command
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
     * ✅ UseCase.Response → API Response
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

## 🎯 복잡한 UseCase 예제

### 예제: 검색 조건이 많은 Query

```java
/**
 * Search Orders UseCase
 *
 * @author development-team
 * @since 1.0.0
 */
public interface SearchOrdersUseCase {

    /**
     * ✅ Query 실행
     */
    Response searchOrders(Query query);

    /**
     * ✅ Query - 복잡한 검색 조건
     */
    record Query(
        Long customerId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long minAmount,
        Long maxAmount,
        int page,
        int size,
        String sortBy,
        String sortDirection
    ) {
        /**
         * ✅ Builder 패턴 (Optional 파라미터)
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long customerId;
            private String status;
            private LocalDate startDate;
            private LocalDate endDate;
            private Long minAmount;
            private Long maxAmount;
            private int page = 0;
            private int size = 20;
            private String sortBy = "createdAt";
            private String sortDirection = "DESC";

            public Builder customerId(Long customerId) {
                this.customerId = customerId;
                return this;
            }

            public Builder status(String status) {
                this.status = status;
                return this;
            }

            public Builder dateRange(LocalDate start, LocalDate end) {
                this.startDate = start;
                this.endDate = end;
                return this;
            }

            public Builder amountRange(Long min, Long max) {
                this.minAmount = min;
                this.maxAmount = max;
                return this;
            }

            public Builder pagination(int page, int size) {
                this.page = page;
                this.size = size;
                return this;
            }

            public Builder sort(String by, String direction) {
                this.sortBy = by;
                this.sortDirection = direction;
                return this;
            }

            public Query build() {
                return new Query(
                    customerId, status, startDate, endDate,
                    minAmount, maxAmount, page, size, sortBy, sortDirection
                );
            }
        }
    }

    /**
     * ✅ Response - 페이징 결과
     */
    record Response(
        List<OrderSummary> orders,
        int totalPages,
        long totalElements,
        int currentPage,
        int pageSize
    ) {
        public record OrderSummary(
            Long orderId,
            Long customerId,
            String status,
            Long totalAmount,
            Instant createdAt
        ) {}
    }
}
```

---

## 📋 UseCase 내부 DTO 체크리스트

### 구조
- [ ] Command/Response를 UseCase 인터페이스 내부에 정의
- [ ] Java Record 사용
- [ ] Nested Record로 복잡한 구조 표현

### 명명
- [ ] `XxxUseCase.Command` (명령)
- [ ] `XxxUseCase.Query` (조회 조건)
- [ ] `XxxUseCase.Response` (응답)

### 검증
- [ ] Compact Constructor로 필수 검증
- [ ] 비즈니스 규칙은 Domain Layer로
- [ ] 방어적 복사 (`List.copyOf()`)

### 사용
- [ ] Adapter Mapper: API DTO ↔ UseCase DTO
- [ ] Application Assembler: UseCase DTO ↔ Domain
- [ ] Service: UseCase 구현체에서 Command 받고 Response 반환

---

## 🔄 전체 흐름 정리

```
[Adapter - Controller]
OrderApiRequest (API DTO)
    ↓
[Adapter - Mapper]
    ↓ toCommand()
CreateOrderUseCase.Command (Application DTO)
    ↓
[Application - Service]
    ↓
[Application - Assembler]
    ↓ toDomain()
Order (Domain)
    ↓
[Domain - Business Logic]
    ↓
Order (Domain Result)
    ↓
[Application - Assembler]
    ↓ toResponse()
CreateOrderUseCase.Response (Application DTO)
    ↓
[Adapter - Mapper]
    ↓ toApiResponse()
OrderApiResponse (API DTO)
    ↓
[Adapter - Controller]
```

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
