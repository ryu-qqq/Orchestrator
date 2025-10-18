# Exception Hierarchy with Sealed Classes - 도메인 예외 계층 구조

**목적**: Sealed Classes로 도메인 예외 계층을 타입 안전하게 모델링하고 Result Type과 통합

**관련 문서**:
- [Result Types](./03_result-types.md)
- [Domain Modeling](./01_domain-modeling.md)
- [Exception Handling Guide](../../../EXCEPTION_HANDLING_GUIDE.md)
- [Command UseCase](../../../03-application-layer/usecase-design/01_command-usecase.md)

**필수 버전**: Java 17+ (Sealed Classes), Java 21+ (Pattern Matching)

---

## 📌 핵심 원칙

### 도메인 예외 계층

1. **Sealed Hierarchy**: 허용된 예외만 정의 (컴파일 타임 검증)
2. **Result Type 통합**: 예외를 Result로 변환하여 반환
3. **레이어별 분리**: Domain Exception vs Application Exception
4. **타입 안전한 처리**: Pattern Matching으로 예외 처리

---

## ❌ 안티패턴 - 런타임 예외 남발

### 문제점: 계층 없는 예외 처리

```java
// ❌ Before - 계층 없는 예외 (타입 안전성 부족)
@Service
public class OrderService {

    /**
     * ❌ 문제점:
     * - RuntimeException 남발
     * - 예외 타입 예측 불가
     * - 컴파일 타임 검증 불가
     */
    public void createOrder(CreateOrderCommand command) {
        if (command.items().isEmpty()) {
            throw new RuntimeException("Items cannot be empty");  // ❌
        }

        if (!inventoryService.isAvailable(command.items())) {
            throw new RuntimeException("Insufficient stock");  // ❌
        }

        if (paymentService.processPayment(command) == null) {
            throw new RuntimeException("Payment failed");  // ❌
        }

        orderRepository.save(order);
    }
}

/**
 * ❌ Controller에서 예외 타입 예측 불가
 */
@RestController
public class OrderController {

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            orderService.createOrder(command);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // ❌ 어떤 예외인지 알 수 없음
            // 400? 404? 409? 500?
            return ResponseEntity.status(500).body(null);
        }
    }
}
```

**문제점**:
- ❌ 예외 타입 예측 불가 (모두 `RuntimeException`)
- ❌ 컴파일 타임 검증 불가
- ❌ HTTP Status Code 결정 어려움
- ❌ 예외 처리 누락 가능

---

## ✅ 권장 패턴 - Sealed Exception Hierarchy

### 패턴 1: Domain Exception Hierarchy

```java
package com.company.domain.order.exception;

/**
 * Order Exception - Sealed Hierarchy
 *
 * - 모든 주문 관련 예외의 루트
 * - Sealed로 허용된 예외만 정의
 * - 컴파일 타임 타입 검증
 *
 * @author development-team
 * @since 1.0.0
 */
public sealed interface OrderException
    permits ValidationException, BusinessRuleException, InfrastructureException {

    /**
     * ✅ 공통 메타데이터
     */
    String getErrorCode();
    String getMessage();
}

/**
 * ✅ Validation Exception - 입력 검증 실패
 */
public sealed interface ValidationException extends OrderException
    permits EmptyItemsException, InvalidQuantityException, InvalidCustomerException {

    @Override
    default String getErrorCode() {
        return "VALIDATION_ERROR";
    }
}

/**
 * ✅ Business Rule Exception - 비즈니스 규칙 위반
 */
public sealed interface BusinessRuleException extends OrderException
    permits InsufficientStockException, PaymentFailedException, OrderNotFoundException {

    @Override
    default String getErrorCode() {
        return "BUSINESS_RULE_ERROR";
    }
}

/**
 * ✅ Infrastructure Exception - 인프라 계층 오류
 */
public sealed interface InfrastructureException extends OrderException
    permits DatabaseException, ExternalApiException {

    @Override
    default String getErrorCode() {
        return "INFRASTRUCTURE_ERROR";
    }
}
```

---

### 패턴 2: Validation Exception (Record)

```java
/**
 * ✅ Empty Items Exception
 */
public record EmptyItemsException(String message) implements ValidationException {
    public EmptyItemsException() {
        this("Order items cannot be empty");
    }

    @Override
    public String getErrorCode() {
        return "EMPTY_ITEMS";
    }

    @Override
    public String getMessage() {
        return message;
    }
}

/**
 * ✅ Invalid Quantity Exception
 */
public record InvalidQuantityException(
    Long productId,
    Integer quantity,
    String message
) implements ValidationException {

    public InvalidQuantityException(Long productId, Integer quantity) {
        this(
            productId,
            quantity,
            "Invalid quantity for product " + productId + ": " + quantity
        );
    }

    @Override
    public String getErrorCode() {
        return "INVALID_QUANTITY";
    }

    @Override
    public String getMessage() {
        return message;
    }
}

/**
 * ✅ Invalid Customer Exception
 */
public record InvalidCustomerException(
    Long customerId,
    String message
) implements ValidationException {

    public InvalidCustomerException(Long customerId) {
        this(customerId, "Invalid customer ID: " + customerId);
    }

    @Override
    public String getErrorCode() {
        return "INVALID_CUSTOMER";
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

---

### 패턴 3: Business Rule Exception (Record)

```java
/**
 * ✅ Insufficient Stock Exception
 */
public record InsufficientStockException(
    Long productId,
    Integer requested,
    Integer available,
    String message
) implements BusinessRuleException {

    public InsufficientStockException(Long productId, Integer requested, Integer available) {
        this(
            productId,
            requested,
            available,
            "Insufficient stock for product " + productId +
                ": requested=" + requested + ", available=" + available
        );
    }

    @Override
    public String getErrorCode() {
        return "INSUFFICIENT_STOCK";
    }

    @Override
    public String getMessage() {
        return message;
    }
}

/**
 * ✅ Payment Failed Exception
 */
public record PaymentFailedException(
    String paymentId,
    String reason,
    String message
) implements BusinessRuleException {

    public PaymentFailedException(String reason) {
        this(null, reason, "Payment failed: " + reason);
    }

    @Override
    public String getErrorCode() {
        return "PAYMENT_FAILED";
    }

    @Override
    public String getMessage() {
        return message;
    }
}

/**
 * ✅ Order Not Found Exception
 */
public record OrderNotFoundException(
    Long orderId,
    String message
) implements BusinessRuleException {

    public OrderNotFoundException(Long orderId) {
        this(orderId, "Order not found: " + orderId);
    }

    @Override
    public String getErrorCode() {
        return "ORDER_NOT_FOUND";
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

---

### 패턴 4: Infrastructure Exception (Record)

```java
/**
 * ✅ Database Exception
 */
public record DatabaseException(
    String operation,
    Throwable cause,
    String message
) implements InfrastructureException {

    public DatabaseException(String operation, Throwable cause) {
        this(operation, cause, "Database error during " + operation);
    }

    @Override
    public String getErrorCode() {
        return "DATABASE_ERROR";
    }

    @Override
    public String getMessage() {
        return message;
    }
}

/**
 * ✅ External API Exception
 */
public record ExternalApiException(
    String apiName,
    String endpoint,
    Throwable cause,
    String message
) implements InfrastructureException {

    public ExternalApiException(String apiName, String endpoint, Throwable cause) {
        this(
            apiName,
            endpoint,
            cause,
            "External API error: " + apiName + " (" + endpoint + ")"
        );
    }

    @Override
    public String getErrorCode() {
        return "EXTERNAL_API_ERROR";
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

---

## 🎯 Result Type과 통합

### 패턴 1: Result Type with Sealed Exception

```java
/**
 * ✅ Result Type
 */
public sealed interface Result<T, E extends OrderException>
    permits Success, Failure {

    <U> Result<U, E> map(Function<T, U> mapper);
    <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper);
    <R> R fold(Function<T, R> onSuccess, Function<E, R> onFailure);

    boolean isSuccess();
    default boolean isFailure() { return !isSuccess(); }

    static <T, E extends OrderException> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E extends OrderException> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }
}

public record Success<T, E extends OrderException>(T value) implements Result<T, E> {
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

public record Failure<T, E extends OrderException>(E error) implements Result<T, E> {
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

### 패턴 2: UseCase with Result Type

```java
package com.company.application.order.service.command;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.domain.order.exception.OrderException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create Order Service - Result Type 반환
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    /**
     * ✅ Result<Response, OrderException> 반환
     *
     * - 예외를 던지지 않고 Result로 반환
     * - 타입 안전한 에러 처리
     */
    @Override
    public Result<Response, OrderException> createOrder(Command command) {
        return validateCommand(command)
            .flatMap(this::checkInventory)
            .flatMap(this::reserveInventory)
            .flatMap(this::createOrder)
            .flatMap(this::processPayment)
            .map(Order::getId)
            .map(orderId -> new Response(orderId, "PENDING", command.totalAmount(), Instant.now()));
    }

    /**
     * ✅ 검증 - Result 반환
     */
    private Result<Command, OrderException> validateCommand(Command command) {
        if (command.customerId() == null || command.customerId() <= 0) {
            return Result.failure(new InvalidCustomerException(command.customerId()));
        }

        if (command.items() == null || command.items().isEmpty()) {
            return Result.failure(new EmptyItemsException());
        }

        for (var item : command.items()) {
            if (item.quantity() <= 0) {
                return Result.failure(new InvalidQuantityException(item.productId(), item.quantity()));
            }
        }

        return Result.success(command);
    }

    /**
     * ✅ 재고 확인 - Result 반환
     */
    private Result<Command, OrderException> checkInventory(Command command) {
        for (var item : command.items()) {
            int available = inventoryRepository.getAvailableStock(item.productId());
            if (available < item.quantity()) {
                return Result.failure(new InsufficientStockException(
                    item.productId(), item.quantity(), available
                ));
            }
        }
        return Result.success(command);
    }

    /**
     * ✅ 재고 예약 - Result 반환
     */
    private Result<Command, OrderException> reserveInventory(Command command) {
        try {
            inventoryService.reserveStock(command.items());
            return Result.success(command);
        } catch (Exception e) {
            return Result.failure(new ExternalApiException("Inventory", "/reserve", e));
        }
    }

    /**
     * ✅ 주문 생성 - Result 반환
     */
    private Result<Order, OrderException> createOrder(Command command) {
        try {
            Order order = Order.create(command.customerId(), command.items());
            orderRepository.save(order);
            return Result.success(order);
        } catch (Exception e) {
            return Result.failure(new DatabaseException("save order", e));
        }
    }

    /**
     * ✅ 결제 처리 - Result 반환
     */
    private Result<Order, OrderException> processPayment(Order order) {
        try {
            paymentService.processPayment(order.getId(), order.getTotalAmount());
            return Result.success(order);
        } catch (PaymentException e) {
            return Result.failure(new PaymentFailedException(e.getReason()));
        }
    }
}
```

---

### 패턴 3: Controller with Pattern Matching

```java
package com.company.application.order.adapter.in.web;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.domain.order.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Order Controller - Pattern Matching
 *
 * @author development-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    /**
     * ✅ Pattern Matching으로 예외별 HTTP Status 매핑
     */
    @PostMapping
    public ResponseEntity<Object> createOrder(@RequestBody CreateOrderRequest request) {
        CreateOrderUseCase.Command command = toCommand(request);
        Result<CreateOrderUseCase.Response, OrderException> result =
            createOrderUseCase.createOrder(command);

        return switch (result) {
            case Success(var response) ->
                ResponseEntity.status(HttpStatus.CREATED).body(response);

            case Failure(var error) -> switch (error) {
                // ✅ Validation Errors → 400 Bad Request
                case ValidationException validationError -> switch (validationError) {
                    case EmptyItemsException e ->
                        ResponseEntity.badRequest()
                            .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));

                    case InvalidQuantityException e ->
                        ResponseEntity.badRequest()
                            .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));

                    case InvalidCustomerException e ->
                        ResponseEntity.badRequest()
                            .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));
                };

                // ✅ Business Rule Errors → 409 Conflict or 404
                case BusinessRuleException businessError -> switch (businessError) {
                    case InsufficientStockException e ->
                        ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ErrorResponse(e.getErrorCode(),
                                "Product " + e.productId() + ": requested=" + e.requested() + ", available=" + e.available()));

                    case PaymentFailedException e ->
                        ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                            .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));

                    case OrderNotFoundException e ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));
                };

                // ✅ Infrastructure Errors → 500 Internal Server Error
                case InfrastructureException infraError -> switch (infraError) {
                    case DatabaseException e ->
                        ResponseEntity.internalServerError()
                            .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));

                    case ExternalApiException e ->
                        ResponseEntity.internalServerError()
                            .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));
                };
            };
        };
    }
}

/**
 * ✅ Error Response DTO
 */
record ErrorResponse(String errorCode, String message) {}
```

**핵심 포인트**:
- ✅ Pattern Matching으로 **모든 예외 타입 처리** 강제
- ✅ 예외별 **적절한 HTTP Status** 매핑
- ✅ `ValidationException` → 400 Bad Request
- ✅ `BusinessRuleException` → 409 Conflict / 404 Not Found
- ✅ `InfrastructureException` → 500 Internal Server Error

---

## 📋 Exception Hierarchy 체크리스트

### 설계
- [ ] **Sealed Interface**로 허용된 예외만 정의했는가?
- [ ] **레이어별 분리**: Validation / Business Rule / Infrastructure
- [ ] **Record**로 불변 예외 정의했는가?
- [ ] 공통 메타데이터 (`errorCode`, `message`) 포함했는가?

### Result Type 통합
- [ ] UseCase에서 **Result<T, OrderException>** 반환하는가?
- [ ] 예외를 던지지 않고 **Result.failure()** 반환하는가?
- [ ] Railway-Oriented Programming 패턴 (`flatMap()`) 사용하는가?

### Controller 처리
- [ ] Pattern Matching으로 **모든 예외 타입** 처리하는가?
- [ ] 예외별 **적절한 HTTP Status** 매핑했는가?
- [ ] `ErrorResponse` DTO로 일관된 에러 응답 반환하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
