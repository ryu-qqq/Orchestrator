# DTO Validation - DTO 검증 전략

**목적**: Bean Validation + Domain 검증 계층화

**관련 문서**:
- [Request/Response DTO](./01_request-response-dto.md)
- [Command/Query DTO](./02_command-query-dto.md)

**필수 버전**: Java 21+, Spring Boot 3.0+, Bean Validation 3.0+

---

## 📌 핵심 원칙

### 검증 계층

1. **DTO 검증**: Bean Validation (형식, 필수 여부)
2. **Command 검증**: 비즈니스 규칙 (Compact Constructor)
3. **Domain 검증**: Aggregate 불변식

---

## ❌ 검증 누락

### 문제: 검증 없이 Entity 생성

```java
// ❌ Before - 검증 없음
@RestController
public class OrderController {

    /**
     * ❌ 문제점:
     * - Request DTO 검증 없음
     * - null 값으로 Entity 생성 가능
     * - 비즈니스 규칙 위반 가능
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        // ⚠️ request.customerId() == null 가능
        // ⚠️ request.items() == empty 가능

        CreateOrderCommand command = new CreateOrderCommand(
            CustomerId.of(request.customerId()),  // ⚠️ NPE 발생 가능
            request.items(),
            request.notes()
        );

        OrderId orderId = createOrderUseCase.createOrder(command);

        return ResponseEntity.ok(OrderResponse.from(orderId));
    }
}
```

---

## ✅ DTO 검증 (1단계)

### 패턴: Bean Validation

```java
package com.company.adapter.in.web.dto;

import jakarta.validation.constraints.*;

/**
 * Create Order Request DTO
 *
 * @author development-team
 * @since 1.0.0
 */
public record CreateOrderRequest(
    @NotNull(message = "Customer ID is required")
    @Positive(message = "Customer ID must be positive")
    Long customerId,

    @NotNull(message = "Order items are required")
    @NotEmpty(message = "Order items cannot be empty")
    @Size(max = 100, message = "Cannot order more than 100 items")
    @Valid  // ✅ Nested DTO 검증
    List<OrderItemRequest> items,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes
) {

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

```java
/**
 * Controller - @Valid 적용
 */
@RestController
public class OrderController {

    /**
     * ✅ @Valid로 Bean Validation 자동 실행
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {  // ✅ @Valid

        CreateOrderCommand command = orderMapper.toCommand(request);

        OrderId orderId = createOrderUseCase.createOrder(command);

        return ResponseEntity.ok(orderMapper.toResponse(orderId));
    }
}
```

**Bean Validation 동작**:
```
1. @Valid 어노테이션 감지
2. CreateOrderRequest 필드 검증
   - customerId: @NotNull, @Positive
   - items: @NotNull, @NotEmpty, @Size, @Valid
3. Nested DTO 검증 (OrderItemRequest)
4. 검증 실패 시 → MethodArgumentNotValidException
5. 성공 시 → Controller 메서드 실행
```

---

## ✅ Command 검증 (2단계)

### 패턴: Compact Constructor 검증

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
        // ✅ Null 검증
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }

        // ✅ 비즈니스 규칙: 중복 상품 금지
        long uniqueProducts = items.stream()
            .map(OrderItemCommand::productId)
            .distinct()
            .count();

        if (uniqueProducts != items.size()) {
            throw new DuplicateProductException("Duplicate products in order");
        }

        // ✅ 비즈니스 규칙: 최소 주문 금액 검증 (예시)
        // Money totalAmount = items.stream()
        //     .map(item -> item.price().multiply(item.quantity()))
        //     .reduce(Money.ZERO, Money::add);
        //
        // if (totalAmount.isLessThan(Money.of(10000))) {
        //     throw new MinimumOrderAmountException(totalAmount);
        // }

        // ✅ 불변 리스트로 방어적 복사
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
```

---

## ✅ Domain 검증 (3단계)

### 패턴: Aggregate 불변식 검증

```java
package com.company.domain.order.model;

/**
 * Order Aggregate Root
 *
 * @author development-team
 * @since 1.0.0
 */
@Entity
public class Order {

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineItem> lineItems = new ArrayList<>();

    @Embedded
    private Money totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    /**
     * ✅ Domain 검증 - Aggregate 불변식
     */
    public void addLineItem(ProductId productId, int quantity, Money unitPrice) {
        // ✅ 불변식 1: DRAFT 상태에서만 수정 가능
        if (this.status != OrderStatus.DRAFT) {
            throw new OrderAlreadyConfirmedException(this.id);
        }

        // ✅ 불변식 2: 중복 상품 금지 (Entity 레벨)
        boolean exists = lineItems.stream()
            .anyMatch(item -> item.getProductId().equals(productId));

        if (exists) {
            throw new DuplicateProductException(productId);
        }

        // ✅ 불변식 3: 최대 100개 상품
        if (lineItems.size() >= 100) {
            throw new MaxOrderItemsExceededException(this.id);
        }

        OrderLineItem lineItem = OrderLineItem.create(productId, quantity, unitPrice);
        lineItems.add(lineItem);

        // ✅ 불변식 4: 총액 = 상품 합계
        this.totalAmount = calculateTotal();
    }

    /**
     * ✅ Domain 검증 - 상태 전환 규칙
     */
    public void confirm() {
        // ✅ 불변식: 최소 1개 상품 필수
        if (lineItems.isEmpty()) {
            throw new EmptyOrderException(this.id);
        }

        // ✅ 불변식: DRAFT → CONFIRMED만 허용
        if (this.status != OrderStatus.DRAFT) {
            throw new InvalidOrderStateException(this.id, this.status, OrderStatus.CONFIRMED);
        }

        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }
}
```

---

## ✅ Custom Validator 패턴

### 패턴: 복잡한 검증 로직 분리

```java
/**
 * Custom Annotation - Email Domain 검증
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailDomainValidator.class)
public @interface ValidEmailDomain {

    String message() default "Invalid email domain";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String[] allowedDomains() default {};
}

/**
 * Custom Validator
 */
public class EmailDomainValidator implements ConstraintValidator<ValidEmailDomain, String> {

    private List<String> allowedDomains;

    @Override
    public void initialize(ValidEmailDomain annotation) {
        this.allowedDomains = Arrays.asList(annotation.allowedDomains());
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null) {
            return true;  // @NotNull로 검증
        }

        String domain = email.substring(email.indexOf('@') + 1);

        return allowedDomains.contains(domain);
    }
}

/**
 * DTO에서 사용
 */
public record RegisterUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @ValidEmailDomain(allowedDomains = {"company.com", "example.com"})
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 20, message = "Password must be 8-20 characters")
    String password
) {}
```

---

## 🎯 실전 예제: 계층별 검증

### ✅ Example: 3단계 검증 흐름

```java
/**
 * 1단계: DTO 검증 (Bean Validation)
 */
@PostMapping("/orders")
public ResponseEntity<OrderResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest request) {  // ✅ DTO 검증

    // 2단계: Command 검증 (Compact Constructor)
    CreateOrderCommand command = orderMapper.toCommand(request);  // ✅ Command 검증

    // 3단계: Domain 검증 (Aggregate)
    OrderId orderId = createOrderUseCase.createOrder(command);  // ✅ Domain 검증

    return ResponseEntity.ok(orderMapper.toResponse(orderId));
}

/**
 * UseCase 구현체
 */
@Service
public class CreateOrderService implements CreateOrderUseCase {

    @Override
    public OrderId createOrder(CreateOrderCommand command) {
        Order order = Order.create(command.customerId());

        // 3단계: Domain 검증 발생
        for (OrderItemCommand item : command.items()) {
            Product product = productRepository.findById(item.productId()).orElseThrow();

            order.addLineItem(  // ✅ Aggregate 불변식 검증
                item.productId(),
                item.quantity(),
                product.getPrice()
            );
        }

        return orderRepository.save(order).getId();
    }
}
```

---

## 📋 DTO Validation 체크리스트

### DTO 검증 (1단계)
- [ ] `@Valid` 어노테이션 적용되어 있는가?
- [ ] 필수 필드에 `@NotNull`, `@NotBlank` 사용하는가?
- [ ] Nested DTO에 `@Valid` 적용되어 있는가?

### Command 검증 (2단계)
- [ ] Compact Constructor로 비즈니스 규칙 검증하는가?
- [ ] 방어적 복사 (`List.copyOf()`) 사용하는가?
- [ ] 적절한 예외 던지는가?

### Domain 검증 (3단계)
- [ ] Aggregate 불변식이 보호되는가?
- [ ] 상태 전환 규칙이 검증되는가?
- [ ] Domain Exception 사용하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
