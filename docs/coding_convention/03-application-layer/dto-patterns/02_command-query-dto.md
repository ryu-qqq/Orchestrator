# Command/Query DTO - CQRS 패턴 DTO

**목적**: UseCase 계층의 Command/Query 객체 설계

**관련 문서**:
- [Request/Response DTO](./01_request-response-dto.md)
- [UseCase Design](../usecase-design/01_command-usecase.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### Command/Query 분리란?

1. **Command**: 상태 변경 (쓰기), 반환값 최소화
2. **Query**: 상태 조회 (읽기), 부작용 없음
3. **DTO 분리**: Command DTO ≠ Query DTO

---

## ❌ Command/Query 혼재

### 문제: 쓰기와 읽기가 혼재

```java
// ❌ Before - Command와 Query 혼재
@Service
public class OrderService {

    /**
     * ❌ 문제점:
     * - 메서드 이름: updateOrder (쓰기인지 읽기인지 불명확)
     * - 반환값: OrderResponse (쓰기 작업인데 전체 데이터 반환)
     * - 역할 혼재: 수정 + 조회
     */
    @Transactional
    public OrderResponse updateOrder(UpdateOrderRequest request) {
        Order order = orderRepository.findById(request.orderId()).orElseThrow();

        order.update(request.status(), request.notes());

        orderRepository.save(order);

        // ⚠️ 쓰기 작업인데 전체 데이터 조회 (불필요)
        return OrderResponse.from(order);
    }
}
```

---

## ✅ Command DTO 패턴

### 패턴: 명령 의도 표현

```java
package com.company.application.port.in;

/**
 * Create Order Command
 *
 * @author development-team
 * @since 1.0.0
 */
public record CreateOrderCommand(
    CustomerId customerId,
    List<OrderItemCommand> items,
    String notes
) {

    /**
     * ✅ Compact Constructor - 비즈니스 규칙 검증
     */
    public CreateOrderCommand {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }

        // ✅ 불변 리스트로 변환
        items = List.copyOf(items);
    }

    public record OrderItemCommand(
        ProductId productId,
        int quantity
    ) {
        public OrderItemCommand {
            if (productId == null) {
                throw new IllegalArgumentException("Product ID is required");
            }

            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
        }
    }
}

/**
 * Create Order UseCase
 */
public interface CreateOrderUseCase {

    /**
     * ✅ Command 실행 → 최소한의 반환 (ID만)
     */
    OrderId createOrder(CreateOrderCommand command);
}
```

```java
/**
 * Update Order Command
 */
public record UpdateOrderStatusCommand(
    OrderId orderId,
    OrderStatus newStatus,
    String reason
) {

    public UpdateOrderStatusCommand {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID is required");
        }

        if (newStatus == null) {
            throw new IllegalArgumentException("Status is required");
        }
    }
}

/**
 * Update Order UseCase
 */
public interface UpdateOrderStatusUseCase {

    /**
     * ✅ Command 실행 → 반환값 없음 (void)
     */
    void updateOrderStatus(UpdateOrderStatusCommand command);
}
```

---

## ✅ Query DTO 패턴

### 패턴: 조회 조건 표현

```java
package com.company.application.port.in;

/**
 * Get Order Query
 *
 * @author development-team
 * @since 1.0.0
 */
public record GetOrderQuery(
    OrderId orderId
) {

    public GetOrderQuery {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID is required");
        }
    }
}

/**
 * Get Order UseCase
 */
public interface GetOrderUseCase {

    /**
     * ✅ Query 실행 → 조회 결과 반환
     */
    OrderResponse getOrder(GetOrderQuery query);
}
```

```java
/**
 * Search Orders Query
 */
public record SearchOrdersQuery(
    CustomerId customerId,
    OrderStatus status,
    LocalDate startDate,
    LocalDate endDate,
    Pageable pageable
) {

    /**
     * ✅ Builder 패턴 (Optional 파라미터)
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CustomerId customerId;
        private OrderStatus status;
        private LocalDate startDate;
        private LocalDate endDate;
        private Pageable pageable = Pageable.unpaged();

        public Builder customerId(CustomerId customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = status;
            return this;
        }

        public Builder dateRange(LocalDate start, LocalDate end) {
            this.startDate = start;
            this.endDate = end;
            return this;
        }

        public Builder pageable(Pageable pageable) {
            this.pageable = pageable;
            return this;
        }

        public SearchOrdersQuery build() {
            return new SearchOrdersQuery(customerId, status, startDate, endDate, pageable);
        }
    }
}

/**
 * Search Orders UseCase
 */
public interface SearchOrdersUseCase {

    /**
     * ✅ Query 실행 → 목록 반환
     */
    Page<OrderSummaryResponse> searchOrders(SearchOrdersQuery query);
}
```

---

## ✅ Command Handler 패턴

### 패턴: UseCase 구현체

```java
package com.company.application.service;

/**
 * Create Order Service (Command Handler)
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ✅ Command 실행 - ID만 반환
     */
    @Override
    public OrderId createOrder(CreateOrderCommand command) {
        // 1. Aggregate 생성
        Order order = Order.create(command.customerId());

        // 2. LineItem 추가
        for (OrderItemCommand item : command.items()) {
            Product product = productRepository.findById(item.productId()).orElseThrow();

            order.addLineItem(
                item.productId(),
                item.quantity(),
                product.getPrice()
            );
        }

        // 3. 저장
        Order savedOrder = orderRepository.save(order);

        // 4. Domain Event 발행
        eventPublisher.publishEvent(OrderCreated.from(savedOrder));

        // ✅ ID만 반환 (최소한의 정보)
        return savedOrder.getId();
    }
}
```

---

## ✅ Query Handler 패턴

### 패턴: 조회 최적화

```java
/**
 * Get Order Service (Query Handler)
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
@Transactional(readOnly = true)
public class GetOrderService implements GetOrderUseCase {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    /**
     * ✅ Query 실행 - DTO 반환
     */
    @Override
    public OrderResponse getOrder(GetOrderQuery query) {
        // 1. Aggregate 조회
        Order order = orderRepository.findById(query.orderId())
            .orElseThrow(() -> new OrderNotFoundException(query.orderId()));

        // 2. 관련 Aggregate 조회 (N+1 방지)
        Customer customer = customerRepository.findById(order.getCustomerId()).orElseThrow();

        List<ProductId> productIds = order.getLineItems().stream()
            .map(OrderLineItem::getProductId)
            .toList();

        Map<ProductId, Product> products = productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        // 3. DTO 변환
        return OrderResponse.from(order, customer, products);
    }
}
```

---

## 🎯 실전 예제: CQRS with Event Sourcing

### ✅ Example: Command/Query 완전 분리

```java
/**
 * Command Model (Write)
 */
@Service
@Transactional
public class OrderCommandService implements CreateOrderUseCase, UpdateOrderStatusUseCase {

    private final OrderRepository orderRepository;
    private final EventStore eventStore;

    @Override
    public OrderId createOrder(CreateOrderCommand command) {
        Order order = Order.create(command);

        orderRepository.save(order);

        // ✅ Event Sourcing: Event 저장
        eventStore.save(OrderCreatedEvent.from(order));

        return order.getId();
    }

    @Override
    public void updateOrderStatus(UpdateOrderStatusCommand command) {
        Order order = orderRepository.findById(command.orderId()).orElseThrow();

        order.updateStatus(command.newStatus(), command.reason());

        orderRepository.save(order);

        // ✅ Event Sourcing
        eventStore.save(OrderStatusUpdatedEvent.from(order));
    }
}

/**
 * Query Model (Read)
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService implements GetOrderUseCase, SearchOrdersUseCase {

    private final OrderReadRepository orderReadRepository;  // ✅ 읽기 전용 Repository

    @Override
    public OrderResponse getOrder(GetOrderQuery query) {
        // ✅ Projection (읽기 최적화 모델)
        OrderProjection projection = orderReadRepository.findProjectionById(query.orderId())
            .orElseThrow(() -> new OrderNotFoundException(query.orderId()));

        return OrderResponse.from(projection);
    }

    @Override
    public Page<OrderSummaryResponse> searchOrders(SearchOrdersQuery query) {
        // ✅ QueryDSL로 동적 쿼리
        Page<OrderProjection> projections = orderReadRepository.search(query);

        return projections.map(OrderSummaryResponse::from);
    }
}
```

---

## 📋 Command/Query DTO 체크리스트

### Command DTO
- [ ] 명령 의도가 명확한가? (Create, Update, Delete)
- [ ] 비즈니스 규칙 검증이 포함되어 있는가?
- [ ] 반환값이 최소화되어 있는가? (ID 또는 void)

### Query DTO
- [ ] 조회 조건이 명확한가?
- [ ] `@Transactional(readOnly = true)` 사용하는가?
- [ ] N+1 문제 방지하는가?

### 분리
- [ ] Command와 Query가 명확히 분리되어 있는가?
- [ ] Command는 부작용 있고, Query는 부작용 없는가?
- [ ] 각각 별도 UseCase 인터페이스로 정의되어 있는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
