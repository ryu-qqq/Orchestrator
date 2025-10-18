# Async Processing with Virtual Threads - @Async + CompletableFuture 최적화

**목적**: Virtual Threads를 활용하여 비동기 처리 성능을 극대화하고 코드 복잡도 감소

**관련 문서**:
- [Virtual Threads Basics](./01_virtual-threads-basics.md)
- [Performance Tuning](./03_performance-tuning.md)

**필수 버전**: Java 21+, Spring Boot 3.2+

---

## 📌 핵심 원칙

### Virtual Threads + @Async 장점

1. **코드 간결성**: Reactive Stack 없이 동기 코드 스타일 유지
2. **높은 동시성**: 수백만 개의 비동기 작업 동시 실행
3. **자동 최적화**: I/O 대기 시 자동으로 다른 작업 실행
4. **리소스 효율성**: Platform Thread 대비 99% 메모리 절감

---

## ❌ 기존 Platform Thread @Async 문제점

### 문제 1: Thread Pool 고갈

```java
// ❌ Platform Thread - Thread Pool 제한
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);   // 최소 50개
        executor.setMaxPoolSize(200);   // 최대 200개
        executor.setQueueCapacity(500); // 큐 500개
        executor.initialize();
        return executor;
    }
}

// 문제: 200개 이상의 동시 @Async 호출 시 큐 대기 발생
```

### 문제 2: CompletableFuture 중첩 복잡도

```java
// ❌ Platform Thread - 복잡한 CompletableFuture 체이닝
@Service
public class OrderService {

    public CompletableFuture<OrderResponse> createOrder(OrderRequest request) {
        return validateOrderAsync(request)
            .thenCompose(valid -> saveOrderAsync(valid))
            .thenCompose(order -> processPaymentAsync(order))
            .thenCompose(payment -> updateInventoryAsync(payment))
            .thenApply(result -> OrderResponse.from(result))
            .exceptionally(ex -> handleError(ex));
    }
    // 코드 가독성 저하, 디버깅 어려움
}
```

---

## ✅ Virtual Threads 해결 방법

### 패턴 1: Spring Boot 3.2+ 자동 @Async

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true  # ✅ @Async 자동으로 Virtual Thread 사용
```

```java
package com.company.application.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Virtual Thread 기반 비동기 서비스
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class NotificationService {

    /**
     * ✅ @Async + Virtual Thread (Spring Boot 3.2+)
     *
     * 설정 파일에서 virtual.enabled=true만 하면
     * 이 메서드는 자동으로 Virtual Thread에서 실행됨
     */
    @Async
    public CompletableFuture<Void> sendEmailAsync(Email email, String subject, String body) {
        // I/O 블로킹 작업 (SMTP 전송)
        emailClient.send(email, subject, body);

        // Virtual Thread는 I/O 대기 중 다른 작업 실행
        return CompletableFuture.completedFuture(null);
    }

    /**
     * ✅ 여러 알림 병렬 전송
     */
    @Async
    public CompletableFuture<Void> sendAllNotifications(OrderId orderId, CustomerInfo customer) {
        CompletableFuture<Void> emailFuture = sendEmailAsync(
            customer.email(), "Order Created", "Your order " + orderId + " is confirmed");

        CompletableFuture<Void> smsFuture = sendSmsAsync(
            customer.phone(), "Order confirmed: " + orderId);

        CompletableFuture<Void> pushFuture = sendPushNotificationAsync(
            customer.deviceId(), "Your order is being processed");

        // 3개 작업 모두 Virtual Thread에서 병렬 실행
        return CompletableFuture.allOf(emailFuture, smsFuture, pushFuture);
    }
}
```

**성능 개선**:
- Platform Thread: 200개 제한 → 201번째 요청부터 큐 대기
- Virtual Thread: 수백만 개 동시 실행 → 큐 대기 없음

---

### 패턴 2: 동기 스타일 코드 (StructuredTaskScope)

```java
package com.company.application.service;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.Future;

/**
 * Structured Concurrency로 간결한 비동기 처리
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OrderProcessingService {

    /**
     * ✅ Before: 복잡한 CompletableFuture 체이닝
     */
    // public CompletableFuture<OrderResponse> createOrderAsync(OrderRequest request) {
    //     return validateAsync(request)
    //         .thenCompose(this::saveOrderAsync)
    //         .thenCompose(this::processPaymentAsync)
    //         .thenApply(OrderResponse::from)
    //         .exceptionally(this::handleError);
    // }

    /**
     * ✅ After: 동기 스타일 코드 + Virtual Threads
     *
     * - 가독성 향상 (순차 코드처럼 작성)
     * - 병렬 실행 (Virtual Thread 자동 활용)
     * - 에러 처리 간단 (try-catch)
     */
    public OrderResponse createOrder(OrderRequest request)
            throws InterruptedException, ExecutionException {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // ✅ 3개 작업 병렬 실행 (Virtual Thread)
            Future<Boolean> validationFuture = scope.fork(() ->
                validateOrder(request));

            Future<Inventory> inventoryFuture = scope.fork(() ->
                checkInventory(request.items()));

            Future<Pricing> pricingFuture = scope.fork(() ->
                calculatePricing(request.items()));

            // ✅ 모든 작업 완료 대기
            scope.join();           // 모두 완료될 때까지 대기
            scope.throwIfFailed();  // 하나라도 실패하면 예외 발생

            // ✅ 결과 조합 (동기 코드처럼 간단)
            boolean isValid = validationFuture.resultNow();
            Inventory inventory = inventoryFuture.resultNow();
            Pricing pricing = pricingFuture.resultNow();

            if (!isValid) {
                throw new InvalidOrderException();
            }

            // ✅ Order 저장 및 Payment 처리
            Order order = saveOrder(request, pricing);
            Payment payment = processPayment(order, pricing);

            return OrderResponse.from(order, payment);

        } catch (Exception e) {
            throw new OrderProcessingException(e);
        }
    }
}
```

**Before vs After**:
| 항목 | CompletableFuture (Before) | StructuredTaskScope (After) |
|------|----------------------------|----------------------------|
| 코드 스타일 | 비동기 체이닝 | 동기 스타일 |
| 가독성 | 낮음 (콜백 지옥) | 높음 (순차 코드) |
| 에러 처리 | `exceptionally()` | `try-catch` |
| 디버깅 | 어려움 | 쉬움 (스택 트레이스 명확) |
| 성능 | 동일 | 동일 (Virtual Thread) |

---

### 패턴 3: Fan-Out/Fan-In 패턴

```java
/**
 * Fan-Out/Fan-In 패턴 - 대량 병렬 처리 후 결과 집계
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class BatchProcessingService {

    /**
     * ✅ 1,000개의 주문 병렬 처리 (Virtual Thread)
     *
     * Platform Thread로는 불가능 (Thread Pool 고갈)
     */
    public BatchResult processBatchOrders(List<OrderRequest> requests)
            throws InterruptedException {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // ✅ Fan-Out: 1,000개 작업 병렬 실행
            List<Future<OrderResult>> futures = requests.stream()
                .map(request -> scope.fork(() -> processOrder(request)))
                .toList();

            // ✅ 모든 작업 완료 대기
            scope.join();
            scope.throwIfFailed();

            // ✅ Fan-In: 결과 집계
            List<OrderResult> results = futures.stream()
                .map(Future::resultNow)
                .toList();

            return BatchResult.from(results);
        }
    }

    /**
     * ✅ 개별 주문 처리 (I/O 집약적)
     */
    private OrderResult processOrder(OrderRequest request) {
        // DB 조회, 외부 API 호출 등 I/O 작업
        Order order = orderRepository.save(Order.create(request));
        Payment payment = paymentClient.processPayment(order);
        emailService.sendConfirmation(order.getCustomerId());

        return OrderResult.from(order, payment);
    }
}
```

**성능**:
- Platform Thread (200개): 1,000개 주문 처리 시간 = 5 라운드 × 평균 처리 시간
- Virtual Thread: 1,000개 주문 동시 처리 → 1 라운드 × 평균 처리 시간 (5배 빠름)

---

## 🎯 실전 예제: 외부 API 통합

### ✅ Example 1: 여러 외부 API 병렬 호출

```java
package com.company.application.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.*;

/**
 * 외부 API 병렬 호출 서비스
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class ProductAggregationService {

    private final ProductInfoClient productInfoClient;
    private final PricingClient pricingClient;
    private final InventoryClient inventoryClient;
    private final ReviewClient reviewClient;

    /**
     * ✅ 4개 외부 API 병렬 호출 (Virtual Thread)
     *
     * - 순차 호출: 50ms + 100ms + 80ms + 120ms = 350ms
     * - 병렬 호출: max(50ms, 100ms, 80ms, 120ms) = 120ms (2.9배 빠름)
     */
    public ProductDetailResponse getProductDetails(ProductId productId)
            throws InterruptedException, ExecutionException {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // ✅ 4개 외부 API 병렬 호출 (Virtual Thread)
            Future<ProductInfo> infoFuture = scope.fork(() ->
                productInfoClient.getProductInfo(productId)); // 50ms

            Future<Pricing> pricingFuture = scope.fork(() ->
                pricingClient.getPricing(productId)); // 100ms

            Future<Inventory> inventoryFuture = scope.fork(() ->
                inventoryClient.getInventory(productId)); // 80ms

            Future<List<Review>> reviewsFuture = scope.fork(() ->
                reviewClient.getReviews(productId)); // 120ms

            // ✅ 모든 API 응답 대기 (최대 120ms)
            scope.join();
            scope.throwIfFailed();

            // ✅ 결과 조합
            return ProductDetailResponse.from(
                infoFuture.resultNow(),
                pricingFuture.resultNow(),
                inventoryFuture.resultNow(),
                reviewsFuture.resultNow()
            );
        }
    }
}
```

---

### ✅ Example 2: Timeout 처리

```java
/**
 * Timeout 처리 - StructuredTaskScope.ShutdownOnSuccess
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class PaymentService {

    /**
     * ✅ 여러 결제 게이트웨이 중 가장 빠른 응답 사용
     *
     * - Timeout: 5초
     * - 가장 빠른 게이트웨이 응답만 사용
     * - 나머지 요청 자동 취소
     */
    public PaymentResult processPaymentWithFallback(PaymentRequest request)
            throws InterruptedException, TimeoutException, ExecutionException {

        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<PaymentResult>()) {

            // ✅ 3개 결제 게이트웨이 동시 호출
            scope.fork(() -> stripeGateway.processPayment(request));
            scope.fork(() -> paypalGateway.processPayment(request));
            scope.fork(() -> tossGateway.processPayment(request));

            // ✅ 가장 빠른 응답 대기 (최대 5초)
            scope.joinUntil(Instant.now().plusSeconds(5));

            // ✅ 성공한 결과 반환 (나머지 자동 취소)
            return scope.result();

        } catch (TimeoutException e) {
            throw new PaymentTimeoutException("All payment gateways timed out", e);
        }
    }
}
```

---

## 🔧 CompletableFuture 최적화

### 패턴 1: Virtual Thread Executor

```java
package com.company.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Virtual Thread Executor 설정
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
public class VirtualThreadExecutorConfig {

    /**
     * ✅ CompletableFuture용 Virtual Thread Executor
     */
    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

```java
/**
 * CompletableFuture + Virtual Thread Executor
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class AsyncOrderService {

    @Qualifier("virtualThreadExecutor")
    private final Executor executor;

    /**
     * ✅ CompletableFuture에 Virtual Thread Executor 명시
     */
    public CompletableFuture<OrderResponse> createOrderAsync(OrderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            // Virtual Thread에서 실행됨
            Order order = orderRepository.save(Order.create(request));
            return OrderResponse.from(order);
        }, executor); // ✅ Virtual Thread Executor 지정
    }
}
```

---

### 패턴 2: 에러 처리 전략

```java
/**
 * 비동기 에러 처리 전략
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class ResilientAsyncService {

    /**
     * ✅ Retry + Fallback (Virtual Thread)
     */
    @Async
    public CompletableFuture<PaymentResult> processPaymentWithRetry(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            int maxRetries = 3;
            int attempt = 0;

            while (attempt < maxRetries) {
                try {
                    return paymentGateway.processPayment(request);
                } catch (TemporaryPaymentException e) {
                    attempt++;
                    if (attempt >= maxRetries) {
                        // Fallback: 대체 게이트웨이 사용
                        return fallbackGateway.processPayment(request);
                    }
                    // 지수 백오프 대기
                    Thread.sleep(Duration.ofMillis(100 * (1 << attempt)));
                }
            }
            throw new PaymentFailedException();
        });
    }
}
```

---

## 📋 @Async + Virtual Threads 체크리스트

### 설정
- [ ] `spring.threads.virtual.enabled=true`
- [ ] Spring Boot 3.2+ 사용
- [ ] Java 21+ 런타임

### 사용 패턴
- [ ] I/O 집약적 작업에만 @Async 사용
- [ ] StructuredTaskScope로 병렬 처리
- [ ] Timeout 설정 (`joinUntil()`)

### 에러 처리
- [ ] `ShutdownOnFailure` 사용 (하나라도 실패 시 모두 취소)
- [ ] `ShutdownOnSuccess` 사용 (가장 빠른 응답만 사용)
- [ ] try-with-resources로 리소스 정리

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
