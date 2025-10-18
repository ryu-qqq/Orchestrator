# Mapper Responsibility - Mapper vs Assembler 역할 구분

> **목적**: Adapter Layer의 Mapper와 Application Layer의 Assembler 역할 명확히 구분
>
> **위치**: `adapter/in/web/mapper/` (Mapper), `application/[context]/assembler/` (Assembler)
>
> **관련 문서**:
> - `01_api-to-usecase-mapper.md` (Mapper 패턴)
> - `03-application-layer/assembler-pattern/01_assembler-responsibility.md` (Assembler 역할)
> - `03-application-layer/package-guide/01_application_package_guide.md` (전체 구조)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. 두 가지 변환 계층

**Mapper (Adapter Layer)**와 **Assembler (Application Layer)**는 서로 다른 책임을 가진 별도의 변환 계층입니다.

```
API Request DTO
    ↓ [Mapper - Adapter Layer]
UseCase Command
    ↓ [Assembler - Application Layer]
Domain Object
```

**왜 두 개의 변환 계층이 필요한가?**
- **계층 분리**: 각 계층이 자신의 DTO에만 의존
- **독립적 변경**: API 변경이 Domain에 영향 없음
- **책임 분리**: 단순 매핑 vs 복잡한 조립 로직 분리

---

## 🔄 전체 데이터 변환 흐름

```
[HTTP Request]
    ↓
API Request DTO (CreateOrderRequest)
    ↓
┌─────────────────────────────────────────┐
│ MAPPER (Adapter Layer)                  │
│ - API DTO → UseCase DTO                 │
│ - 단순 필드 매핑                          │
│ - 타입 변환 (String, Number)             │
└─────────────────────────────────────────┘
    ↓
UseCase Command (CreateOrderUseCase.Command)
    ↓
┌─────────────────────────────────────────┐
│ ASSEMBLER (Application Layer)           │
│ - UseCase DTO → Domain                  │
│ - Value Object 생성                      │
│ - Aggregate 조립                         │
└─────────────────────────────────────────┘
    ↓
Domain Object (Order)
    ↓
[Domain Logic Execution]
    ↓
Domain Result (Order)
    ↓
┌─────────────────────────────────────────┐
│ ASSEMBLER (Application Layer)           │
│ - Domain → UseCase DTO                  │
│ - Response 조립                          │
└─────────────────────────────────────────┘
    ↓
UseCase Response (CreateOrderUseCase.Response)
    ↓
┌─────────────────────────────────────────┐
│ MAPPER (Adapter Layer)                  │
│ - UseCase DTO → API DTO                 │
│ - BigDecimal → String 변환              │
│ - 날짜 포맷 변환                          │
└─────────────────────────────────────────┘
    ↓
API Response DTO (OrderApiResponse)
    ↓
[HTTP Response]
```

---

## 📊 Mapper vs Assembler 비교

### 전체 비교표

| 구분 | Mapper (Adapter) | Assembler (Application) |
|------|------------------|-------------------------|
| **위치** | `adapter/in/web/mapper/` | `application/[context]/assembler/` |
| **계층** | Adapter Layer | Application Layer |
| **변환** | API DTO ↔ UseCase DTO | UseCase DTO ↔ Domain |
| **의존성** | API DTO, UseCase DTO | UseCase DTO, Domain |
| **복잡도** | 단순 매핑 | Value Object 변환, 조립 로직 |
| **책임** | 필드 매핑, 타입 변환 | Domain 객체 생성/조립 |
| **예제** | `OrderApiMapper` | `OrderAssembler` |
| **Bean** | `@Component` | `@Component` |
| **Stateless** | ✅ Yes | ✅ Yes |

### 변환 방향

```
Mapper:
  API Request → UseCase Command
  UseCase Response → API Response

Assembler:
  UseCase Command → Domain
  Domain → UseCase Response
```

---

## ❌ Anti-Pattern: 역할 혼동

### 문제 1: Mapper가 Domain 다루기

```java
// ❌ Bad: Mapper가 Domain 변환 (Assembler 책임)
package com.company.adapter.in.web.mapper;

import com.company.domain.order.*;  // ❌ Adapter가 Domain 직접 의존

@Component
public class OrderApiMapper {

    /**
     * ❌ Mapper가 Domain 객체 생성
     */
    public Order toDomain(CreateOrderRequest request) {
        return Order.create(
            CustomerId.of(request.customerId()),
            request.items().stream()
                .map(item -> OrderLineItem.create(
                    ProductId.of(item.productId()),
                    Quantity.of(item.quantity())
                ))
                .toList()
        );
    }
}
```

**문제점**:
- 🔴 Adapter Layer가 Domain에 직접 의존
- 🔴 Assembler의 책임 침범
- 🔴 계층 경계 위반

### 문제 2: Assembler가 API DTO 다루기

```java
// ❌ Bad: Assembler가 API DTO 변환 (Mapper 책임)
package com.company.application.order.assembler;

import com.company.adapter.in.web.dto.*;  // ❌ Application이 Adapter 의존

@Component
public class OrderAssembler {

    /**
     * ❌ Assembler가 API DTO 변환
     */
    public CreateOrderUseCase.Command toCommand(CreateOrderRequest request) {
        return new CreateOrderUseCase.Command(
            request.customerId(),
            mapItems(request.items())
        );
    }
}
```

**문제점**:
- 🔴 Application Layer가 Adapter에 의존 (역방향 의존)
- 🔴 헥사고날 아키텍처 위반
- 🔴 Mapper의 책임 침범

### 문제 3: 하나의 변환 계층만 사용

```java
// ❌ Bad: Mapper가 모든 변환 담당
@Component
public class OrderApiMapper {

    /**
     * ❌ API Request → Domain 직접 변환
     */
    public Order toDomain(CreateOrderRequest request) {
        // Mapper + Assembler 역할을 모두 수행
        return Order.create(...);
    }
}
```

**문제점**:
- 🔴 계층 간 결합도 증가
- 🔴 API 변경 시 Domain 영향
- 🔴 재사용 불가능한 변환 로직

---

## ✅ Best Practice: 명확한 역할 분리

### 패턴: Mapper + Assembler 협력

#### 1. Mapper (Adapter Layer)

```java
package com.company.adapter.in.web.mapper;

import com.company.adapter.in.web.dto.*;
import com.company.application.port.in.CreateOrderUseCase;
import org.springframework.stereotype.Component;

/**
 * Order API Mapper
 *
 * <p>책임:
 * <ul>
 *   <li>API Request → UseCase Command 변환</li>
 *   <li>UseCase Response → API Response 변환</li>
 *   <li>타입 변환 (BigDecimal → String, 날짜 포맷)</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 */
@Component
public class OrderApiMapper {

    /**
     * ✅ API Request → UseCase Command 변환
     *
     * <p>단순 필드 매핑만 수행, Domain은 다루지 않음
     */
    public CreateOrderUseCase.Command toCommand(CreateOrderRequest request) {
        List<CreateOrderUseCase.Command.OrderItem> items = request.items().stream()
            .map(item -> new CreateOrderUseCase.Command.OrderItem(
                item.productId(),
                item.quantity()
            ))
            .toList();

        return new CreateOrderUseCase.Command(
            request.customerId(),
            items,
            request.notes()
        );
    }

    /**
     * ✅ UseCase Response → API Response 변환
     *
     * <p>타입 변환 (BigDecimal → String) 포함
     */
    public OrderApiResponse toApiResponse(CreateOrderUseCase.Response response) {
        return new OrderApiResponse(
            response.orderId(),
            response.status(),
            response.totalAmount().toString(),  // ✅ 타입 변환
            response.createdAt()
        );
    }
}
```

#### 2. Assembler (Application Layer)

```java
package com.company.application.order.assembler;

import com.company.application.port.in.CreateOrderUseCase;
import com.company.domain.order.*;
import org.springframework.stereotype.Component;

/**
 * Order Assembler
 *
 * <p>책임:
 * <ul>
 *   <li>UseCase Command → Domain 변환</li>
 *   <li>Domain → UseCase Response 변환</li>
 *   <li>Value Object 생성 및 Aggregate 조립</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 */
@Component
public class OrderAssembler {

    /**
     * ✅ UseCase Command → Domain 변환
     *
     * <p>Value Object 생성 및 Aggregate 조립
     */
    public Order toDomain(CreateOrderUseCase.Command command) {
        // 1. Value Object 변환
        CustomerId customerId = CustomerId.of(command.customerId());

        // 2. OrderLineItem 변환
        List<OrderLineItem> lineItems = command.items().stream()
            .map(this::toLineItem)
            .toList();

        // 3. Domain Aggregate 생성
        return Order.create(customerId, lineItems);
    }

    /**
     * ✅ Command.OrderItem → OrderLineItem 변환
     */
    private OrderLineItem toLineItem(CreateOrderUseCase.Command.OrderItem item) {
        return OrderLineItem.create(
            ProductId.of(item.productId()),
            Quantity.of(item.quantity())
        );
    }

    /**
     * ✅ Domain → UseCase Response 변환
     */
    public CreateOrderUseCase.Response toResponse(Order order) {
        return new CreateOrderUseCase.Response(
            order.getId().value(),
            order.getStatus().name(),
            order.getTotalAmount().value(),
            order.getCreatedAt()
        );
    }
}
```

#### 3. Service (Application Layer) - 협력

```java
package com.company.application.order.service.command;

import com.company.application.port.in.CreateOrderUseCase;
import com.company.application.port.out.SaveOrderPort;
import com.company.application.order.assembler.OrderAssembler;
import com.company.domain.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create Order Service
 *
 * <p>Mapper와 Assembler를 활용한 완전한 변환 흐름:
 * <pre>
 * API Request → Mapper → UseCase Command → Assembler → Domain
 * Domain → Assembler → UseCase Response → Mapper → API Response
 * </pre>
 *
 * @author Development Team
 * @since 1.0.0
 */
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderAssembler orderAssembler;  // ✅ Assembler 주입
    private final SaveOrderPort saveOrderPort;

    public CreateOrderService(
            OrderAssembler orderAssembler,
            SaveOrderPort saveOrderPort) {
        this.orderAssembler = orderAssembler;
        this.saveOrderPort = saveOrderPort;
    }

    @Override
    public Response createOrder(Command command) {
        // ✅ 1. Assembler로 Command → Domain 변환
        Order order = orderAssembler.toDomain(command);

        // ✅ 2. Domain 저장
        Order savedOrder = saveOrderPort.save(order);

        // ✅ 3. Assembler로 Domain → Response 변환
        return orderAssembler.toResponse(savedOrder);
    }
}
```

#### 4. Controller (Adapter Layer) - 완전한 흐름

```java
package com.company.adapter.in.web;

import com.company.adapter.in.web.dto.*;
import com.company.adapter.in.web.mapper.OrderApiMapper;
import com.company.application.port.in.CreateOrderUseCase;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Order Controller
 *
 * <p>Mapper를 통한 완전한 변환 흐름:
 * <pre>
 * HTTP Request → API Request DTO
 *              ↓ Mapper
 *          UseCase Command
 *              ↓ UseCase (Assembler 내부 사용)
 *         UseCase Response
 *              ↓ Mapper
 *       API Response DTO → HTTP Response
 * </pre>
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;  // ✅ Mapper 주입

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            OrderApiMapper orderApiMapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderApiMapper = orderApiMapper;
    }

    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        // ✅ 1. Mapper: API Request → UseCase Command
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // ✅ 2. UseCase 실행 (내부에서 Assembler 사용)
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // ✅ 3. Mapper: UseCase Response → API Response
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", "/api/v1/orders/" + apiResponse.orderId())
            .body(apiResponse);
    }
}
```

---

## 🎯 실전 예제: 복잡한 조회 시나리오

### 시나리오: 주문 상세 조회 (여러 Aggregate 조합)

#### 1. API DTO (Adapter Layer)

```java
/**
 * Order Detail API Response
 */
public record OrderDetailApiResponse(
    Long orderId,
    String status,
    String totalAmount,
    CustomerInfo customer,  // 고객 정보
    List<OrderItemApiResponse> items,
    LocalDateTime createdAt
) {
    public record CustomerInfo(
        Long customerId,
        String customerName,
        String email
    ) {}

    public record OrderItemApiResponse(
        Long productId,
        String productName,
        Integer quantity,
        String unitPrice,
        String subtotal
    ) {}
}
```

#### 2. UseCase Response (Application Layer)

```java
/**
 * Get Order UseCase
 */
public interface GetOrderQuery {

    record Query(Long orderId) {}

    record Response(
        Long orderId,
        String status,
        BigDecimal totalAmount,
        Long customerId,
        String customerName,
        String customerEmail,
        List<OrderItem> items,
        LocalDateTime createdAt
    ) {
        public record OrderItem(
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
        ) {}
    }

    Response getOrder(Query query);
}
```

#### 3. Mapper (Adapter Layer) - UseCase DTO → API DTO

```java
@Component
public class OrderApiMapper {

    /**
     * ✅ Mapper: UseCase Response → API Response 변환
     *
     * <p>책임:
     * <ul>
     *   <li>중첩 DTO 구조 변환</li>
     *   <li>BigDecimal → String 타입 변환</li>
     * </ul>
     */
    public OrderDetailApiResponse toDetailApiResponse(GetOrderQuery.Response response) {

        // Customer 정보 변환
        OrderDetailApiResponse.CustomerInfo customerInfo =
            new OrderDetailApiResponse.CustomerInfo(
                response.customerId(),
                response.customerName(),
                response.customerEmail()
            );

        // OrderItem List 변환
        List<OrderDetailApiResponse.OrderItemApiResponse> items = response.items().stream()
            .map(item -> new OrderDetailApiResponse.OrderItemApiResponse(
                item.productId(),
                item.productName(),
                item.quantity(),
                item.unitPrice().toString(),    // ✅ BigDecimal → String
                item.subtotal().toString()      // ✅ BigDecimal → String
            ))
            .toList();

        return new OrderDetailApiResponse(
            response.orderId(),
            response.status(),
            response.totalAmount().toString(),  // ✅ BigDecimal → String
            customerInfo,
            items,
            response.createdAt()
        );
    }
}
```

#### 4. Assembler (Application Layer) - Domain → UseCase DTO

```java
@Component
public class OrderAssembler {

    private final LoadCustomerPort loadCustomerPort;
    private final LoadProductPort loadProductPort;

    /**
     * ✅ Assembler: Domain → UseCase Response 변환
     *
     * <p>책임:
     * <ul>
     *   <li>여러 Aggregate 조회 (Order, Customer, Product)</li>
     *   <li>Domain 객체 조립</li>
     *   <li>Value Object → Primitive 변환</li>
     * </ul>
     */
    public GetOrderQuery.Response toDetailedResponse(Order order) {

        // 1. Customer 조회
        Customer customer = loadCustomerPort.load(order.getCustomerId())
            .orElseThrow(() -> new CustomerNotFoundException(order.getCustomerId()));

        // 2. Product Batch 조회
        List<ProductId> productIds = order.getLineItems().stream()
            .map(OrderLineItem::getProductId)
            .toList();

        Map<ProductId, Product> products = loadProductPort.loadAll(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        // 3. Response 조립
        List<GetOrderQuery.Response.OrderItem> items = order.getLineItems().stream()
            .map(item -> toEnrichedLineItem(item, products.get(item.getProductId())))
            .toList();

        return new GetOrderQuery.Response(
            order.getId().value(),
            order.getStatus().name(),
            order.getTotalAmount().value(),
            customer.getId().value(),
            customer.getName(),
            customer.getEmail(),
            items,
            order.getCreatedAt()
        );
    }

    /**
     * OrderLineItem + Product → Response.OrderItem 변환
     */
    private GetOrderQuery.Response.OrderItem toEnrichedLineItem(
            OrderLineItem item,
            Product product) {

        return new GetOrderQuery.Response.OrderItem(
            item.getProductId().value(),
            product.getName(),              // ✅ Product 정보 추가
            item.getQuantity().value(),
            item.getUnitPrice().value(),
            item.getSubtotal().value()
        );
    }
}
```

---

## 🚫 책임 범위 정리

### Mapper 책임 (Adapter Layer)

✅ **해야 할 일**:
- API DTO ↔ UseCase DTO 변환
- 타입 변환 (BigDecimal → String, LocalDateTime → String)
- 중첩 DTO 매핑
- Null Safety 처리
- JSON 직렬화 최적화

❌ **하지 말아야 할 일**:
- Domain 객체 생성/조립
- Value Object 변환
- 비즈니스 로직
- 외부 의존성 호출 (Port, Repository)
- Validation (Controller에서)

### Assembler 책임 (Application Layer)

✅ **해야 할 일**:
- UseCase DTO ↔ Domain 변환
- Value Object 생성 (`CustomerId.of()`, `Money.of()`)
- Aggregate 조립
- 여러 Aggregate 조합 (필요 시 Port 사용)
- Domain → DTO Projection

❌ **하지 말아야 할 일**:
- API DTO 다루기
- 비즈니스 로직 (Domain으로 이동)
- 트랜잭션 처리 (Service에서)
- JSON 직렬화 최적화

---

## 📋 역할 구분 체크리스트

### Mapper 검증
- [ ] `adapter/in/web/mapper/` 패키지에 위치하는가?
- [ ] API DTO만 다루는가? (Domain 직접 의존 금지)
- [ ] 단순 필드 매핑만 수행하는가?
- [ ] 타입 변환만 수행하는가?
- [ ] Stateless인가?

### Assembler 검증
- [ ] `application/[context]/assembler/` 패키지에 위치하는가?
- [ ] Domain과 UseCase DTO만 다루는가? (API DTO 의존 금지)
- [ ] Value Object 생성 로직이 있는가?
- [ ] Aggregate 조립 로직이 있는가?
- [ ] Stateless인가?

### 계층 의존성 검증
- [ ] Adapter → Application 의존 (정방향)
- [ ] Application → Domain 의존 (정방향)
- [ ] Application ← Adapter 의존 금지 (역방향 금지)
- [ ] Domain ← Application 의존 금지 (역방향 금지)

### 협력 검증
- [ ] Controller가 Mapper를 사용하는가?
- [ ] Service가 Assembler를 사용하는가?
- [ ] Controller가 Assembler를 직접 호출하지 않는가?
- [ ] Service가 Mapper를 직접 호출하지 않는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
