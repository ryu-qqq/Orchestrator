# Assembler - Command/Domain 변환 패턴

**목적**: Application Layer의 Command → Domain, Domain → Response 변환 책임

**위치**: `application/[context]/assembler/`

**관련 문서**:
- [UseCase Design](../usecase-design/01_command-usecase.md)
- [Mapper vs Assembler](./02_mapper-vs-assembler.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### Assembler란?

1. **Command → Domain**: UseCase Command를 Domain 객체로 변환
2. **Domain → Response**: Domain 처리 결과를 UseCase Response로 변환
3. **Application Layer**: Application Layer에 위치 (Adapter Mapper와 구분)

---

## 🔄 전체 데이터 흐름

```
[Adapter Layer - Controller]
API Request (OrderRequest)
    ↓
[Adapter Layer - Mapper]
    ↓ toCommand()
UseCase Command (CreateOrderUseCase.Command)
    ↓
[Application Layer - UseCase → Service]
    ↓
[Application Layer - Assembler]
    ↓ toDomain()
Domain Object (Order)
    ↓
[Domain Layer - Business Logic]
    ↓
Domain Result (Order)
    ↓
[Application Layer - Assembler]
    ↓ toResponse()
UseCase Response (CreateOrderUseCase.Response)
    ↓
[Adapter Layer - Mapper]
    ↓ toApiResponse()
API Response (OrderApiResponse)
    ↓
[Adapter Layer - Controller]
```

---

## ❌ Anti-Pattern: Assembler 없이 직접 변환

```java
// ❌ Before - Service에서 직접 변환
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    @Override
    public Response createOrder(Command command) {
        // ❌ Service에서 직접 Domain 생성
        Order order = Order.create(
            CustomerId.of(command.customerId()),
            command.items().stream()
                .map(item -> OrderLineItem.create(
                    ProductId.of(item.productId()),
                    Quantity.of(item.quantity())
                ))
                .toList()
        );

        Order savedOrder = saveOrderPort.save(order);

        // ❌ Service에서 직접 Response 생성
        return new Response(
            savedOrder.getId().value(),
            savedOrder.getStatus().name(),
            savedOrder.getTotalAmount().value()
        );
    }
}
```

**문제점**:
- Service가 변환 로직까지 담당 (SRP 위반)
- 변환 로직 재사용 불가
- 테스트 복잡도 증가

---

## ✅ Assembler 패턴

### 패턴: 도메인별 단일 Assembler

```java
package com.company.application.order.assembler;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.domain.order.*;
import org.springframework.stereotype.Component;

/**
 * Order Assembler
 * - Command → Domain 변환
 * - Domain → Response 변환
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderAssembler {

    /**
     * ✅ Command → Domain 변환
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
     * ✅ OrderItemCommand → OrderLineItem 변환
     */
    private OrderLineItem toLineItem(CreateOrderUseCase.Command.OrderItem item) {
        return OrderLineItem.create(
            ProductId.of(item.productId()),
            Quantity.of(item.quantity()),
            Money.of(item.unitPrice())
        );
    }

    /**
     * ✅ Domain → Response 변환
     */
    public CreateOrderUseCase.Response toResponse(Order order) {
        return new CreateOrderUseCase.Response(
            order.getId().value(),
            order.getStatus().name(),
            order.getTotalAmount().value(),
            order.getCreatedAt()
        );
    }

    /**
     * ✅ Query 응답 변환 (조회용)
     */
    public GetOrderUseCase.Response toGetResponse(Order order) {
        return new GetOrderUseCase.Response(
            order.getId().value(),
            order.getCustomerId().value(),
            order.getStatus().name(),
            order.getLineItems().stream()
                .map(this::toLineItemResponse)
                .toList(),
            order.getTotalAmount().value(),
            order.getCreatedAt()
        );
    }

    private GetOrderUseCase.Response.LineItem toLineItemResponse(OrderLineItem item) {
        return new GetOrderUseCase.Response.LineItem(
            item.getProductId().value(),
            item.getQuantity().value(),
            item.getUnitPrice().value(),
            item.getSubtotal().value()
        );
    }
}
```

---

## ✅ Service에서 Assembler 사용

```java
package com.company.application.order.service.command;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.application.order.port.out.SaveOrderPort;
import com.company.application.order.assembler.OrderAssembler;
import com.company.domain.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create Order Service
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

---

## 🎯 복잡한 변환 로직 예제

### 예제: 여러 Aggregate 조합

```java
@Component
public class OrderAssembler {

    private final LoadCustomerPort loadCustomerPort;
    private final LoadProductPort loadProductPort;

    /**
     * ✅ 복잡한 변환: 여러 Aggregate 조회 필요
     */
    public GetOrderUseCase.Response toDetailedResponse(Order order) {
        // 1. Customer 조회
        Customer customer = loadCustomerPort.load(order.getCustomerId())
            .orElseThrow(() -> new CustomerNotFoundException(order.getCustomerId()));

        // 2. Product 조회 (Batch)
        List<ProductId> productIds = order.getLineItems().stream()
            .map(OrderLineItem::getProductId)
            .toList();

        Map<ProductId, Product> products = loadProductPort.loadAll(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        // 3. Response 조립
        return new GetOrderUseCase.Response(
            order.getId().value(),
            new GetOrderUseCase.Response.CustomerInfo(
                customer.getId().value(),
                customer.getName(),
                customer.getEmail()
            ),
            order.getLineItems().stream()
                .map(item -> toEnrichedLineItem(item, products.get(item.getProductId())))
                .toList(),
            order.getTotalAmount().value(),
            order.getStatus().name()
        );
    }

    private GetOrderUseCase.Response.LineItem toEnrichedLineItem(
        OrderLineItem item,
        Product product
    ) {
        return new GetOrderUseCase.Response.LineItem(
            item.getProductId().value(),
            product.getName(),  // ✅ Product 정보 추가
            item.getQuantity().value(),
            item.getUnitPrice().value(),
            item.getSubtotal().value()
        );
    }
}
```

---

## 📋 Assembler 체크리스트

### 구조
- [ ] `application/[context]/assembler/` 위치
- [ ] 도메인별 단일 Assembler (`OrderAssembler`, `PaymentAssembler`)
- [ ] Command/Query 분리 없이 공통 사용

### 책임
- [ ] Command → Domain 변환 (`toDomain`)
- [ ] Domain → Response 변환 (`toResponse`)
- [ ] Value Object 변환 로직 포함
- [ ] 복잡한 조립 로직 캡슐화

### 금지 사항
- [ ] Adapter Layer DTO 직접 사용 금지
- [ ] 비즈니스 로직 포함 금지 (Domain Layer로)
- [ ] 트랜잭션 처리 금지 (Service Layer로)

---

## 🚫 Anti-Pattern 정리

### ❌ Assembler에 비즈니스 로직

```java
// ❌ Bad - Assembler에 비즈니스 규칙
@Component
public class OrderAssembler {
    public Order toDomain(CreateOrderUseCase.Command command) {
        Order order = Order.create(customerId);

        // ❌ 할인 계산 로직 (Domain으로 이동)
        if (command.totalAmount() > 100000) {
            order.applyDiscount(0.1);
        }

        return order;
    }
}
```

**올바른 방법**: 비즈니스 로직은 Domain Layer로

```java
// ✅ Good - Domain에서 처리
public class Order {
    public void applyVolumeDiscount() {
        if (this.totalAmount.isGreaterThan(Money.of(100000))) {
            this.applyDiscount(DiscountRate.of(0.1));
        }
    }
}
```

---

### ❌ Assembler에서 Port 호출 (외부 의존)

```java
// ❌ Bad - Assembler에서 Repository 호출
@Component
public class OrderAssembler {
    private final ProductRepository productRepository;  // ❌

    public Order toDomain(CreateOrderUseCase.Command command) {
        // ❌ Assembler에서 외부 의존 호출
        Product product = productRepository.findById(command.productId()).orElseThrow();

        return Order.create(customerId, product);
    }
}
```

**올바른 방법**: Service에서 처리

```java
// ✅ Good - Service에서 Port 호출
@Service
public class CreateOrderService implements CreateOrderUseCase {

    @Override
    public Response createOrder(Command command) {
        // ✅ Service에서 Product 조회
        Product product = loadProductPort.load(command.productId()).orElseThrow();

        // ✅ Assembler는 변환만
        Order order = orderAssembler.toDomain(command, product);

        return orderAssembler.toResponse(saveOrderPort.save(order));
    }
}
```

---

## 🔄 Mapper vs Assembler 비교

| 구분 | Mapper (Adapter) | Assembler (Application) |
|------|------------------|-------------------------|
| **위치** | `adapter/in/web/mapper/` | `application/[context]/assembler/` |
| **변환** | Request ↔ Command<br>Response ↔ API Response | Command → Domain<br>Domain → Response |
| **의존성** | Adapter DTO | UseCase Command/Response, Domain |
| **복잡도** | 단순 매핑 | Value Object 변환, 조립 로직 |
| **예제** | `OrderApiMapper` | `OrderAssembler` |

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
