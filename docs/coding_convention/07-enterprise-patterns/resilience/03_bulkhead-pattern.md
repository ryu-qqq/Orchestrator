# Bulkhead Pattern - 장애 격리 및 리소스 분리

**목적**: Thread Pool 격리로 서비스 간 장애 전파 차단

**관련 문서**:
- [Circuit Breaker](./01_circuit-breaker.md)
- [Virtual Threads](../../06-java21-patterns/virtual-threads/01_virtual-threads-basics.md)

**필수 버전**: Spring Boot 3.0+, Resilience4j 2.0+

---

## 📌 핵심 원칙

### Bulkhead Pattern이란?

1. **리소스 격리**: 서비스별 독립적인 Thread Pool
2. **장애 격리**: 한 서비스 장애가 다른 서비스에 영향 없음
3. **2가지 방식**: Semaphore vs Thread Pool

### Bulkhead가 필요한 이유

- ✅ **장애 격리**: Payment API 다운 → Notification API는 정상 동작
- ✅ **공정 분배**: 느린 API가 전체 스레드 점유 방지
- ✅ **SLA 보장**: 중요 API에 우선 리소스 할당

---

## ❌ Bulkhead 없는 문제점

### 문제: 공통 Thread Pool 사용 시 장애 전파

```java
// ❌ Before - 공통 Thread Pool (Tomcat 기본 200개)
@Service
public class OrderService {

    /**
     * ❌ 문제 시나리오:
     *
     * 1. Payment API 느려짐 (응답 30초)
     * 2. 100 req/sec → 100개 스레드 모두 Payment API 대기
     * 3. Notification API 요청 도착 → 사용 가능한 스레드 없음
     * 4. Notification API도 응답 불가 (연쇄 장애)
     */
    public OrderResponse createOrder(CreateOrderCommand command) {
        // 1. Payment API 호출 (느림 - 30초)
        PaymentResponse payment = paymentClient.charge(command.amount());

        // 2. Notification API 호출 (빠름 - 100ms)
        notificationClient.sendEmail(command.customerId());

        return OrderResponse.from(order);
    }
}
```

**문제 시나리오**:
```
Tomcat Thread Pool (200개):
- Payment API 요청 100개 → 100개 스레드 점유 (30초 대기)
- Notification API 요청 100개 → 100개 스레드 점유 (30초 대기)
- 새로운 요청 도착 → 사용 가능한 스레드 없음 → 503 Service Unavailable

결과: Payment API 장애 → 전체 서비스 다운
```

---

## ✅ Bulkhead Pattern - Semaphore 방식

### 패턴: 동시 호출 수 제한

```yaml
# application.yml
resilience4j:
  bulkhead:
    configs:
      default:
        # ✅ Semaphore 방식: 동시 호출 수 제한
        max-concurrent-calls: 10
        # ✅ 대기 시간 (0ms = 즉시 실패)
        max-wait-duration: 0ms

    instances:
      paymentService:
        base-config: default
        max-concurrent-calls: 20  # Payment API는 20개까지

      notificationService:
        base-config: default
        max-concurrent-calls: 50  # Notification API는 50개까지
```

```java
package com.company.application.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;

/**
 * Order Service - Semaphore Bulkhead
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OrderService {

    /**
     * ✅ @Bulkhead - Semaphore 방식
     *
     * - 최대 20개 동시 호출
     * - 21번째 요청은 즉시 실패 (BulkheadFullException)
     */
    @Bulkhead(name = "paymentService", fallbackMethod = "chargePaymentFallback")
    public PaymentResponse chargePayment(Money amount) {
        // ✅ 최대 20개까지만 동시 실행
        return paymentClient.charge(amount);
    }

    /**
     * ✅ Fallback - Bulkhead 포화 시
     */
    private PaymentResponse chargePaymentFallback(Money amount, Exception e) {
        log.warn("Bulkhead full. Payment request rejected: {}", amount);

        throw new TooManyRequestsException("Payment service is busy. Please try again later.");
    }

    /**
     * ✅ Notification은 별도 Bulkhead
     */
    @Bulkhead(name = "notificationService")
    public void sendNotification(CustomerId customerId) {
        // ✅ 최대 50개까지 동시 실행 (Payment와 독립적)
        notificationClient.sendEmail(customerId);
    }
}
```

**Semaphore Bulkhead 동작**:
```
Payment API Bulkhead (max-concurrent-calls: 20):
- 요청 1~20: 허용 (동시 실행)
- 요청 21: 거부 (BulkheadFullException → Fallback)

Notification API Bulkhead (max-concurrent-calls: 50):
- 요청 1~50: 허용 (Payment와 독립적)
- Payment API 다운 여부와 무관하게 정상 동작 ✅
```

---

## ✅ Bulkhead Pattern - Thread Pool 방식

### 패턴: 독립적인 Thread Pool 격리

```yaml
# application.yml
resilience4j:
  thread-pool-bulkhead:
    configs:
      default:
        # ✅ Thread Pool 크기
        max-thread-pool-size: 10
        # ✅ Core Thread 수
        core-thread-pool-size: 5
        # ✅ Queue 크기
        queue-capacity: 20
        # ✅ Thread 유휴 시간
        keep-alive-duration: 20ms

    instances:
      paymentService:
        max-thread-pool-size: 20
        core-thread-pool-size: 10
        queue-capacity: 50

      notificationService:
        max-thread-pool-size: 50
        core-thread-pool-size: 20
        queue-capacity: 100
```

```java
/**
 * Order Service - Thread Pool Bulkhead
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OrderService {

    /**
     * ✅ Thread Pool Bulkhead - 비동기 실행
     *
     * - Payment 전용 Thread Pool (20개)
     * - CompletableFuture 반환
     */
    @Bulkhead(name = "paymentService", type = Bulkhead.Type.THREADPOOL,
              fallbackMethod = "chargePaymentAsyncFallback")
    public CompletableFuture<PaymentResponse> chargePaymentAsync(Money amount) {
        return CompletableFuture.supplyAsync(() -> {
            // ✅ Payment 전용 Thread Pool에서 실행
            return paymentClient.charge(amount);
        });
    }

    /**
     * ✅ Notification 전용 Thread Pool (50개)
     */
    @Bulkhead(name = "notificationService", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<Void> sendNotificationAsync(CustomerId customerId) {
        return CompletableFuture.runAsync(() -> {
            // ✅ Notification 전용 Thread Pool에서 실행
            notificationClient.sendEmail(customerId);
        });
    }

    /**
     * ✅ 두 작업 병렬 실행 (독립적인 Thread Pool)
     */
    public OrderResponse createOrder(CreateOrderCommand command) {
        CompletableFuture<PaymentResponse> paymentFuture = chargePaymentAsync(command.amount());
        CompletableFuture<Void> notificationFuture = sendNotificationAsync(command.customerId());

        // ✅ 두 작업 모두 완료 대기
        CompletableFuture.allOf(paymentFuture, notificationFuture).join();

        PaymentResponse payment = paymentFuture.join();

        return OrderResponse.from(order, payment);
    }
}
```

**Thread Pool Bulkhead 동작**:
```
Tomcat Thread Pool (200개):
  ↓ 요청 수신
[Payment Thread Pool (20개)]
  - Payment API 전용
  - 느려져도 20개만 영향

[Notification Thread Pool (50개)]
  - Notification API 전용
  - Payment와 독립적 ✅

[Order Thread Pool (나머지 130개)]
  - 일반 요청 처리
```

---

## 🎯 실전 예제: Virtual Threads와 통합

### ✅ Example: Virtual Threads + Bulkhead

```java
/**
 * Virtual Threads + Semaphore Bulkhead
 *
 * - Virtual Threads로 동시성 향상
 * - Semaphore로 외부 API 호출 수 제한
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
public class VirtualThreadBulkheadConfig {

    /**
     * ✅ Virtual Thread Executor
     */
    @Bean
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

@Service
public class OrderService {

    private final Executor virtualThreadExecutor;

    /**
     * ✅ Virtual Threads + Bulkhead 조합
     *
     * - Virtual Threads: 수천 개 동시 실행 가능
     * - Bulkhead: 외부 API 호출만 20개로 제한
     */
    @Bulkhead(name = "paymentService")  // ✅ 외부 API 호출 제한
    public CompletableFuture<PaymentResponse> processPaymentAsync(Money amount) {
        return CompletableFuture.supplyAsync(() -> {
            // ✅ Virtual Thread에서 실행 (가벼움)
            return paymentClient.charge(amount);
        }, virtualThreadExecutor);
    }
}
```

---

## 📋 Bulkhead Pattern 체크리스트

### 설계
- [ ] Semaphore vs Thread Pool 선택
- [ ] 서비스별 Thread Pool 크기 결정
- [ ] Queue 크기 설정 (Rejection 임계값)

### 구현
- [ ] `@Bulkhead` 모든 외부 API 호출에 적용
- [ ] 서비스별 독립적인 Bulkhead 설정
- [ ] Fallback 메서드 구현

### 모니터링
- [ ] Thread Pool 사용률
- [ ] Queue 대기 시간
- [ ] Rejection 빈도

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
