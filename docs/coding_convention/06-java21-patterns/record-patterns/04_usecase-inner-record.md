# UseCase Inner Record - Command/Response 내부 Record 패턴

**목적**: UseCase 인터페이스 내부에 Command/Response를 Inner Record로 정의하여 응집도 향상

**위치**: `application/[context]/port/in/`

**관련 문서**:
- [Command UseCase](../../../03-application-layer/usecase-design/01_command-usecase.md)
- [Query UseCase](../../../03-application-layer/usecase-design/02_query-usecase.md)
- [DTO with Records](./01_dto-with-records.md)
- [Assembler Pattern](../../../03-application-layer/assembler-pattern/01_assembler-responsibility.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### UseCase Inner Record란?

1. **응집도**: Command/Response가 UseCase와 함께 존재
2. **캡슐화**: UseCase 전용 DTO로 외부 노출 최소화
3. **불변성**: Record의 불변성으로 안전한 데이터 전달
4. **명확성**: `CreateOrderUseCase.Command`처럼 명확한 네이밍

---

## ❌ Before - 외부 클래스 (안티패턴)

### 문제점: Command/Response 외부 클래스

```java
// ❌ Before - 별도 파일 (CreateOrderCommand.java)
package com.company.application.order.dto;

public record CreateOrderCommand(
    Long customerId,
    List<OrderItem> items,
    String notes
) {}

// ❌ Before - 별도 파일 (CreateOrderResponse.java)
public record CreateOrderResponse(
    Long orderId,
    String status,
    Long totalAmount,
    Instant createdAt
) {}

// ❌ Before - UseCase 인터페이스
package com.company.application.order.port.in;

public interface CreateOrderUseCase {
    CreateOrderResponse createOrder(CreateOrderCommand command);
}
```

**문제점**:
- ❌ Command/Response가 UseCase와 분리되어 응집도 저하
- ❌ 패키지 구조가 복잡해짐 (dto 패키지 추가 필요)
- ❌ 어떤 UseCase의 DTO인지 불명확
- ❌ 다른 UseCase에서 잘못 재사용 가능 (결합도 증가)

---

## ✅ After - UseCase Inner Record (권장 패턴)

### 패턴 1: Command UseCase with Inner Record

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
     * ✅ 주문 생성 실행
     */
    Response createOrder(Command command);

    /**
     * ✅ Command - 내부 Record
     *
     * - UseCase 전용 입력 DTO
     * - 불변 객체로 안전한 데이터 전달
     * - Compact Constructor에서 검증
     */
    record Command(
        Long customerId,
        List<OrderItem> items,
        String notes
    ) {
        /**
         * ✅ Compact Constructor - 검증 로직
         */
        public Command {
            if (customerId == null || customerId <= 0) {
                throw new IllegalArgumentException("Invalid customer ID");
            }

            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Order items cannot be empty");
            }

            // ✅ 방어적 복사 (불변성 보장)
            items = List.copyOf(items);
        }

        /**
         * ✅ 중첩 Record - OrderItem
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
            }
        }
    }

    /**
     * ✅ Response - 내부 Record
     *
     * - UseCase 전용 출력 DTO
     * - 최소한의 정보만 반환 (필요한 필드만)
     */
    record Response(
        Long orderId,
        String status,
        Long totalAmount,
        Instant createdAt
    ) {}
}
```

**핵심 포인트**:
- ✅ `Command`와 `Response`가 UseCase 내부에 Inner Record로 정의
- ✅ `CreateOrderUseCase.Command`로 명확한 참조
- ✅ Compact Constructor에서 입력 검증
- ✅ 중첩 Record (`OrderItem`)로 복잡한 구조 표현
- ✅ 방어적 복사 (`List.copyOf()`)로 불변성 보장

---

### 패턴 2: Query UseCase with Inner Record

```java
package com.company.application.order.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Get Order Detail UseCase
 *
 * @author development-team
 * @since 1.0.0
 */
public interface GetOrderDetailUseCase {

    /**
     * ✅ 주문 상세 조회
     */
    Response getOrderDetail(Query query);

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
     *
     * - 조회 전용 DTO
     * - 도메인 모델을 노출하지 않음
     * - API Response와 구분됨
     */
    record Response(
        Long orderId,
        CustomerInfo customer,
        List<OrderLineItem> items,
        OrderStatus status,
        Long totalAmount,
        Instant createdAt
    ) {
        /**
         * ✅ 중첩 Record - CustomerInfo
         */
        public record CustomerInfo(
            Long customerId,
            String customerName,
            String email
        ) {}

        /**
         * ✅ 중첩 Record - OrderLineItem
         */
        public record OrderLineItem(
            Long productId,
            String productName,
            Integer quantity,
            Long unitPrice,
            Long subtotal
        ) {}

        /**
         * ✅ Enum - OrderStatus
         */
        public enum OrderStatus {
            PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
        }
    }
}
```

---

### 패턴 3: Service 구현체에서 Inner Record 사용

```java
package com.company.application.order.service.command;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.application.order.port.out.SaveOrderPort;
import com.company.application.order.assembler.OrderAssembler;
import com.company.domain.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create Order Service (구현체)
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderAssembler orderAssembler;
    private final SaveOrderPort saveOrderPort;

    public CreateOrderService(
        OrderAssembler orderAssembler,
        SaveOrderPort saveOrderPort
    ) {
        this.orderAssembler = orderAssembler;
        this.saveOrderPort = saveOrderPort;
    }

    /**
     * ✅ Inner Record 사용
     *
     * - Command: CreateOrderUseCase.Command
     * - Response: CreateOrderUseCase.Response
     */
    @Override
    public Response createOrder(Command command) {
        // ✅ 1. Assembler: Command → Domain
        Order order = orderAssembler.toDomain(command);

        // ✅ 2. Port: Domain 저장
        Order savedOrder = saveOrderPort.save(order);

        // ✅ 3. Assembler: Domain → Response
        return orderAssembler.toResponse(savedOrder);
    }
}
```

---

### 패턴 4: Controller에서 REST DTO ↔ UseCase Record 변환

```java
package com.company.application.order.adapter.in.web;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.application.order.adapter.in.web.dto.CreateOrderRequest;
import com.company.application.order.adapter.in.web.dto.CreateOrderResponse;
import org.springframework.http.HttpStatus;
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

    public OrderController(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    /**
     * ✅ REST DTO → UseCase Inner Record → REST DTO
     *
     * 1. REST DTO (CreateOrderRequest) → UseCase.Command
     * 2. UseCase.Command → UseCase.Response
     * 3. UseCase.Response → REST DTO (CreateOrderResponse)
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
        @RequestBody CreateOrderRequest request
    ) {
        // ✅ 1. REST DTO → UseCase.Command
        CreateOrderUseCase.Command command = new CreateOrderUseCase.Command(
            request.customerId(),
            request.items().stream()
                .map(item -> new CreateOrderUseCase.Command.OrderItem(
                    item.productId(),
                    item.quantity(),
                    item.unitPrice()
                ))
                .toList(),
            request.notes()
        );

        // ✅ 2. UseCase 실행
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // ✅ 3. UseCase.Response → REST DTO
        CreateOrderResponse apiResponse = new CreateOrderResponse(
            response.orderId(),
            response.status(),
            response.totalAmount(),
            response.createdAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }
}
```

**핵심 포인트**:
- ✅ **REST DTO** (`CreateOrderRequest/Response`): 외부 API 스펙
- ✅ **UseCase Record** (`Command/Response`): Application Layer 내부 DTO
- ✅ Controller에서 변환 책임 (Mapper/Assembler는 Domain ↔ UseCase만 담당)

---

## 🎯 REST DTO vs UseCase Inner Record

| 항목 | REST DTO | UseCase Inner Record |
|------|----------|----------------------|
| **목적** | 외부 API 스펙 | Application Layer 내부 DTO |
| **위치** | `adapter/in/web/dto/` | `port/in/` (UseCase 내부) |
| **네이밍** | `CreateOrderRequest/Response` | `Command/Response` |
| **참조** | `CreateOrderRequest` | `CreateOrderUseCase.Command` |
| **변환** | Controller에서 UseCase Record로 변환 | Assembler에서 Domain으로 변환 |
| **변경 이유** | API 스펙 변경 | UseCase 로직 변경 |

**예시**:
```
REST API Layer:
  CreateOrderRequest (JSON) → Controller

Application Layer:
  CreateOrderUseCase.Command → Service → CreateOrderUseCase.Response

Domain Layer:
  Order (Aggregate) ← Assembler → CreateOrderUseCase.Command
```

---

## 🔧 고급 패턴

### 패턴 1: Validation with Java Bean Validation

```java
/**
 * ✅ Bean Validation 통합
 *
 * - Record는 불변이므로 Compact Constructor에서만 검증
 * - Bean Validation 어노테이션과 함께 사용 가능
 */
public interface CreateOrderUseCase {

    Response createOrder(@Valid Command command);

    record Command(
        @NotNull(message = "Customer ID is required")
        @Positive(message = "Customer ID must be positive")
        Long customerId,

        @NotEmpty(message = "Order items cannot be empty")
        @Size(max = 100, message = "Maximum 100 items allowed")
        List<@Valid OrderItem> items,

        @Size(max = 500, message = "Notes maximum 500 characters")
        String notes
    ) {
        public Command {
            items = List.copyOf(items);
        }

        public record OrderItem(
            @NotNull(message = "Product ID is required")
            @Positive(message = "Product ID must be positive")
            Long productId,

            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 999, message = "Quantity must be at most 999")
            Integer quantity,

            @NotNull(message = "Unit price is required")
            @PositiveOrZero(message = "Unit price must be positive or zero")
            Long unitPrice
        ) {}
    }

    record Response(/* ... */) {}
}
```

---

### 패턴 2: Multiple Commands in One UseCase

```java
/**
 * ✅ 여러 Command를 가진 UseCase
 *
 * - 관련된 여러 명령을 하나의 UseCase에 그룹화
 * - 각 Command는 독립적인 Inner Record
 */
public interface ManageOrderUseCase {

    /**
     * 주문 승인
     */
    void approveOrder(ApproveCommand command);

    /**
     * 주문 취소
     */
    void cancelOrder(CancelCommand command);

    /**
     * 주문 배송 시작
     */
    void shipOrder(ShipCommand command);

    /**
     * ✅ 승인 Command
     */
    record ApproveCommand(
        Long orderId,
        Long approvedBy,
        String approvalNotes
    ) {
        public ApproveCommand {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("Invalid order ID");
            }
        }
    }

    /**
     * ✅ 취소 Command
     */
    record CancelCommand(
        Long orderId,
        String reason
    ) {
        public CancelCommand {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("Invalid order ID");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Cancellation reason is required");
            }
        }
    }

    /**
     * ✅ 배송 시작 Command
     */
    record ShipCommand(
        Long orderId,
        String trackingNumber,
        String carrier
    ) {
        public ShipCommand {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("Invalid order ID");
            }
        }
    }
}
```

---

### 패턴 3: Assembler에서 Inner Record 변환

```java
package com.company.application.order.assembler;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.domain.order.Order;
import com.company.domain.order.OrderLineItem;
import org.springframework.stereotype.Component;

/**
 * Order Assembler
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderAssembler {

    /**
     * ✅ UseCase.Command → Domain
     */
    public Order toDomain(CreateOrderUseCase.Command command) {
        List<OrderLineItem> lineItems = command.items().stream()
            .map(item -> OrderLineItem.create(
                item.productId(),
                item.quantity(),
                item.unitPrice()
            ))
            .toList();

        return Order.create(
            command.customerId(),
            lineItems,
            command.notes()
        );
    }

    /**
     * ✅ Domain → UseCase.Response
     */
    public CreateOrderUseCase.Response toResponse(Order order) {
        return new CreateOrderUseCase.Response(
            order.getId(),
            order.getStatus().name(),
            order.getTotalAmount(),
            order.getCreatedAt()
        );
    }
}
```

---

## 📋 UseCase Inner Record 체크리스트

### 설계
- [ ] Command/Response를 **UseCase 내부에 Inner Record**로 정의했는가?
- [ ] REST DTO와 UseCase Record를 **명확히 구분**했는가?
- [ ] Compact Constructor에서 **입력 검증**을 수행하는가?
- [ ] 방어적 복사 (`List.copyOf()`)로 **불변성**을 보장하는가?

### 구현
- [ ] **Assembler**에서 Domain ↔ UseCase Record 변환을 담당하는가?
- [ ] **Controller**에서 REST DTO ↔ UseCase Record 변환을 수행하는가?
- [ ] Service 구현체에서 **UseCase.Command/Response** 타입을 사용하는가?

### 네이밍
- [ ] Command/Response가 **UseCase 내부**에 정의되어 있는가?
- [ ] 외부에서 `CreateOrderUseCase.Command`처럼 **명확하게 참조** 가능한가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
