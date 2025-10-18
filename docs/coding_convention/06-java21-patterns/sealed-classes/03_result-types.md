# Result Types with Sealed Classes - 타입 안전한 성공/실패 처리

**목적**: Sealed Classes로 Railway-Oriented Programming 패턴 구현 및 예외 없는 에러 처리

**관련 문서**:
- [Domain Modeling](./01_domain-modeling.md)
- [Exception Handling Guide](../../../EXCEPTION_HANDLING_GUIDE.md)

**필수 버전**: Java 17+ (Sealed Classes), Java 21+ (Pattern Matching)

---

## 📌 핵심 원칙

### Result Type 패턴

1. **명시적 에러 처리**: 예외 대신 Result 타입 반환
2. **타입 안전성**: Success/Failure를 Sealed로 제한
3. **Railway-Oriented**: 성공/실패 체이닝
4. **함수형 스타일**: map, flatMap, fold 메서드

---

## ✅ Result Type 패턴

### 패턴 1: 기본 Result Type

```java
package com.company.common.result;

/**
 * Result Type - Sealed Interface
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface Result<T, E>
    permits Success, Failure {

    /**
     * 성공 시 값 변환
     */
    <U> Result<U, E> map(Function<T, U> mapper);

    /**
     * 성공 시 다른 Result 반환
     */
    <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper);

    /**
     * 최종 값 추출 (패턴 매칭)
     */
    <R> R fold(Function<T, R> onSuccess, Function<E, R> onFailure);

    /**
     * 성공 여부
     */
    boolean isSuccess();

    /**
     * 실패 여부
     */
    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * 정적 팩토리 메서드
     */
    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }
}

/**
 * Success (성공)
 */
public record Success<T, E>(T value) implements Result<T, E> {

    @Override
    public <U> Result<U, E> map(Function<T, U> mapper) {
        return new Success<>(mapper.apply(value));
    }

    @Override
    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        return mapper.apply(value);
    }

    @Override
    public <R> R fold(Function<T, R> onSuccess, Function<E, R> onFailure) {
        return onSuccess.apply(value);
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}

/**
 * Failure (실패)
 */
public record Failure<T, E>(E error) implements Result<T, E> {

    @Override
    public <U> Result<U, E> map(Function<T, U> mapper) {
        return new Failure<>(error);
    }

    @Override
    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        return new Failure<>(error);
    }

    @Override
    public <R> R fold(Function<T, R> onSuccess, Function<E, R> onFailure) {
        return onFailure.apply(error);
    }

    @Override
    public boolean isSuccess() {
        return false;
    }
}
```

---

### 패턴 2: 도메인 에러 모델링

```java
/**
 * Order Error - Sealed Hierarchy
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface OrderError
    permits ValidationError, BusinessRuleError, InfrastructureError {}

public record ValidationError(String field, String message) implements OrderError {}

public sealed interface BusinessRuleError extends OrderError
    permits InsufficientStock, PaymentFailed, OrderNotFound {}

public record InsufficientStock(ProductId productId, int requested, int available)
    implements BusinessRuleError {}

public record PaymentFailed(String reason) implements BusinessRuleError {}

public record OrderNotFound(OrderId orderId) implements BusinessRuleError {}

public record InfrastructureError(String message, Throwable cause) implements OrderError {}
```

---

### 패턴 3: UseCase with Result Type

```java
package com.company.application.service;

/**
 * CreateOrderUseCase - Result Type 반환
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class CreateOrderService implements CreateOrderUseCase {

    /**
     * ✅ 예외 대신 Result 반환
     *
     * - 명시적 에러 처리
     * - 컴파일 타임 검증
     * - 함수형 체이닝
     */
    @Override
    public Result<OrderId, OrderError> createOrder(CreateOrderCommand command) {
        return validateCommand(command)
            .flatMap(this::checkInventory)
            .flatMap(this::reserveInventory)
            .flatMap(this::createOrder)
            .flatMap(this::processPayment)
            .map(Order::getId);
    }

    /**
     * 명령 검증
     */
    private Result<CreateOrderCommand, OrderError> validateCommand(CreateOrderCommand command) {
        if (command.items().isEmpty()) {
            return Result.failure(new ValidationError("items", "Items cannot be empty"));
        }
        return Result.success(command);
    }

    /**
     * 재고 확인
     */
    private Result<CreateOrderCommand, OrderError> checkInventory(CreateOrderCommand command) {
        for (OrderLineItem item : command.items()) {
            int available = inventoryRepository.getAvailableStock(item.productId());
            if (available < item.quantity()) {
                return Result.failure(
                    new InsufficientStock(item.productId(), item.quantity(), available)
                );
            }
        }
        return Result.success(command);
    }

    /**
     * 재고 예약
     */
    private Result<CreateOrderCommand, OrderError> reserveInventory(CreateOrderCommand command) {
        try {
            inventoryService.reserveStock(command.items());
            return Result.success(command);
        } catch (Exception e) {
            return Result.failure(new InfrastructureError("Inventory reservation failed", e));
        }
    }

    /**
     * 주문 생성
     */
    private Result<Order, OrderError> createOrder(CreateOrderCommand command) {
        try {
            Order order = Order.create(command.customerId(), command.items());
            orderRepository.save(order);
            return Result.success(order);
        } catch (Exception e) {
            return Result.failure(new InfrastructureError("Order creation failed", e));
        }
    }

    /**
     * 결제 처리
     */
    private Result<Order, OrderError> processPayment(Order order) {
        try {
            paymentService.processPayment(order.getId(), order.getTotalAmount());
            return Result.success(order);
        } catch (PaymentException e) {
            return Result.failure(new PaymentFailed(e.getMessage()));
        }
    }
}
```

---

### 패턴 4: Controller with Pattern Matching

```java
/**
 * OrderController - Result Type 처리
 *
 * @author development-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    /**
     * ✅ Pattern Matching으로 Result 처리
     *
     * - Success → 201 Created
     * - Failure → 적절한 HTTP Status
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = OrderMapper.toCommand(request);
        Result<OrderId, OrderError> result = createOrderUseCase.createOrder(command);

        return switch (result) {
            case Success(var orderId) ->
                ResponseEntity.status(HttpStatus.CREATED)
                    .body(new OrderResponse(orderId.value()));

            case Failure(var error) -> switch (error) {
                case ValidationError(var field, var message) ->
                    ResponseEntity.badRequest()
                        .body(ErrorResponse.of("VALIDATION_ERROR", message));

                case InsufficientStock(var productId, var requested, var available) ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ErrorResponse.of("INSUFFICIENT_STOCK",
                            "Product " + productId + ": requested " + requested + ", available " + available));

                case PaymentFailed(var reason) ->
                    ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(ErrorResponse.of("PAYMENT_FAILED", reason));

                case OrderNotFound(var orderId) ->
                    ResponseEntity.notFound().build();

                case InfrastructureError(var message, var cause) ->
                    ResponseEntity.internalServerError()
                        .body(ErrorResponse.of("INTERNAL_ERROR", message));
            };
        };
    }
}
```

**Before (예외 기반)**:
```java
// ❌ Before - 예외 처리
@PostMapping
public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
    try {
        OrderId orderId = createOrderUseCase.createOrder(command);
        return ResponseEntity.created(...).body(new OrderResponse(orderId.value()));
    } catch (ValidationException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(e));
    } catch (InsufficientStockException e) {
        return ResponseEntity.status(409).body(ErrorResponse.of(e));
    } catch (PaymentFailedException e) {
        return ResponseEntity.status(402).body(ErrorResponse.of(e));
    } catch (Exception e) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of(e));
    }
}
```

**After (Result Type + Pattern Matching)**:
- ✅ 명시적 에러 처리 (모든 에러 타입 컴파일 타임 검증)
- ✅ 코드 간결성 (40% 감소)
- ✅ Railway-Oriented (함수형 체이닝)

---

## 🎯 실전 예제: Retry with Result

### ✅ Example: Resilient Service

```java
/**
 * Resilient Service with Result Type
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class ResilientPaymentService {

    /**
     * ✅ Retry 로직 with Result Type
     */
    public Result<PaymentResult, PaymentError> processPaymentWithRetry(
            PaymentRequest request, int maxRetries) {

        Result<PaymentResult, PaymentError> result = processPayment(request);

        int attempts = 1;
        while (result.isFailure() && attempts < maxRetries) {
            result = switch (result) {
                case Failure(PaymentError error) when error instanceof TransientError -> {
                    // 재시도 가능한 에러
                    Thread.sleep(Duration.ofMillis(100 * attempts));
                    yield processPayment(request);
                }
                case Failure(PaymentError error) -> {
                    // 재시도 불가능한 에러
                    yield result;
                }
                case Success(var value) -> result;
            };
            attempts++;
        }

        return result;
    }

    private Result<PaymentResult, PaymentError> processPayment(PaymentRequest request) {
        try {
            PaymentResult paymentResult = paymentGateway.process(request);
            return Result.success(paymentResult);
        } catch (TransientException e) {
            return Result.failure(new TransientError(e.getMessage()));
        } catch (PermanentException e) {
            return Result.failure(new PermanentError(e.getMessage()));
        }
    }
}
```

---

## 📋 Result Type 체크리스트

### 설계
- [ ] 예외 대신 Result 타입 반환
- [ ] Sealed로 Success/Failure 제한
- [ ] 도메인 에러를 Sealed Hierarchy로 모델링

### 구현
- [ ] map, flatMap, fold 메서드 제공
- [ ] Pattern Matching으로 처리
- [ ] Railway-Oriented 체이닝

### 사용
- [ ] UseCase 반환 타입으로 사용
- [ ] Controller에서 Pattern Matching
- [ ] 에러별 적절한 HTTP Status

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
