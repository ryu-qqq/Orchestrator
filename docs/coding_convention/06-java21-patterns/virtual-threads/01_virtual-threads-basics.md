# Virtual Threads Basics - Java 21 Virtual Threads 개념 및 Spring Boot 통합

**목적**: Java 21 Virtual Threads를 활용하여 높은 동시성을 가진 경량 스레드 기반 애플리케이션 구축

**관련 문서**:
- [Async Processing](./02_async-processing.md)
- [Performance Tuning](./03_performance-tuning.md)

**필수 버전**: Java 21+, Spring Boot 3.2+

---

## 📌 핵심 원칙

### Virtual Threads란?

1. **경량 스레드**: OS 스레드가 아닌 JVM이 관리하는 경량 스레드
2. **높은 동시성**: 수백만 개의 Virtual Thread 생성 가능
3. **블로킹 I/O 최적화**: I/O 대기 시 자동으로 다른 작업 실행
4. **간단한 코드**: 기존 동기 코드 스타일 유지

### Platform Thread vs Virtual Thread

| 구분 | Platform Thread | Virtual Thread |
|------|----------------|----------------|
| 생성 비용 | 높음 (~2MB 메모리) | 낮음 (~1KB 메모리) |
| 최대 개수 | 수천 개 (OS 제한) | 수백만 개 |
| 스케줄링 | OS 커널 | JVM |
| I/O 블로킹 | 스레드 낭비 | 자동 양보 |
| 사용 사례 | CPU 집약적 작업 | I/O 집약적 작업 |

---

## ❌ 기존 Platform Thread 문제점

### 문제 1: Thread Pool 크기 제한

```java
// ❌ Platform Thread - Thread Pool 제한
@Configuration
public class ThreadPoolConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);  // 제한된 스레드 수
        executor.setMaxPoolSize(100);  // 최대 100개
        executor.setQueueCapacity(500);
        return executor;
    }
}

// 문제: 100개 이상의 동시 요청 처리 불가
// 101번째 요청은 큐에서 대기 → 응답 지연
```

### 문제 2: I/O 대기 시 스레드 낭비

```java
// ❌ Platform Thread - I/O 대기 시 스레드 블로킹
@Service
public class OrderService {
    public OrderResponse getOrder(Long orderId) {
        // DB 조회 (50ms 대기) → 스레드 블로킹
        Order order = orderRepository.findById(orderId).orElseThrow();

        // 외부 API 호출 (200ms 대기) → 스레드 블로킹
        PaymentInfo payment = paymentClient.getPaymentInfo(order.getPaymentId());

        // 총 250ms 동안 스레드가 I/O만 대기하며 낭비됨!
        return OrderResponse.from(order, payment);
    }
}

// 문제: 1000 RPS → 최소 250개의 스레드 필요
// Platform Thread는 100개 제한 → 900개 요청은 큐 대기
```

---

## ✅ Virtual Threads 해결 방법

### 패턴 1: Spring Boot 3.2+ 자동 활성화

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true  # ✅ Virtual Threads 활성화 (Spring Boot 3.2+)
```

**효과**:
- Tomcat의 모든 요청 처리 스레드가 Virtual Thread로 변경
- `@Async` 메서드도 자동으로 Virtual Thread 사용
- 추가 코드 변경 없이 즉시 적용

---

### 패턴 2: 명시적 Virtual Thread 생성

```java
// ✅ Virtual Thread 명시적 생성
public class VirtualThreadExample {

    /**
     * Virtual Thread 직접 생성
     */
    public void createVirtualThread() {
        Thread vThread = Thread.ofVirtual().start(() -> {
            System.out.println("Running on Virtual Thread: " + Thread.currentThread());
        });
    }

    /**
     * ExecutorService로 Virtual Thread Pool
     */
    public void useVirtualThreadExecutor() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 1,000,000개의 Virtual Thread 생성 가능!
            for (int i = 0; i < 1_000_000; i++) {
                executor.submit(() -> {
                    // I/O 작업 시뮬레이션
                    Thread.sleep(Duration.ofSeconds(1));
                    return "Task completed";
                });
            }
        }
    }
}
```

**핵심 차이점**:
- Platform Thread: `Executors.newFixedThreadPool(100)` → 100개 제한
- Virtual Thread: `Executors.newVirtualThreadPerTaskExecutor()` → 무제한 (메모리만 허용)

---

### 패턴 3: Spring Boot @Async with Virtual Threads

```java
package com.company.application.in.web;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Virtual Thread 기반 비동기 처리
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class NotificationService {

    /**
     * ✅ @Async + Virtual Threads (Spring Boot 3.2+)
     *
     * spring.threads.virtual.enabled=true 설정으로
     * 이 메서드는 자동으로 Virtual Thread에서 실행됨
     */
    @Async
    public CompletableFuture<Void> sendEmailNotification(OrderId orderId, Email email) {
        // I/O 블로킹 작업 (SMTP 전송)
        emailClient.send(email, "Order Created: " + orderId);

        // Virtual Thread는 I/O 대기 시 자동으로 다른 작업 실행
        // Platform Thread처럼 스레드가 블로킹되지 않음!

        return CompletableFuture.completedFuture(null);
    }

    /**
     * ✅ 여러 비동기 작업 병렬 실행
     */
    @Async
    public CompletableFuture<Void> sendAllNotifications(OrderId orderId) {
        CompletableFuture<Void> emailFuture = sendEmailNotification(orderId, email);
        CompletableFuture<Void> smsFuture = sendSmsNotification(orderId, phone);
        CompletableFuture<Void> pushFuture = sendPushNotification(orderId, deviceId);

        // 3개 작업 모두 Virtual Thread에서 병렬 실행
        return CompletableFuture.allOf(emailFuture, smsFuture, pushFuture);
    }
}
```

**Before (Platform Thread)**:
```
100개 동시 요청 → 100개 Platform Thread 생성 → Thread Pool 고갈
101번째 요청 → 큐 대기 → 응답 지연
```

**After (Virtual Thread)**:
```
100,000개 동시 요청 → 100,000개 Virtual Thread 생성 → 정상 처리
CPU 코어 수만큼만 Platform Thread 사용 (자동 매핑)
```

---

## 🎯 실전 예제: I/O 집약적 REST API

### ✅ Example 1: 외부 API 병렬 호출

```java
package com.company.application.in.web;

import org.springframework.web.bind.annotation.*;
import java.util.concurrent.*;

/**
 * Virtual Thread 기반 REST Controller
 *
 * @author development-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;

    /**
     * ✅ Virtual Thread에서 자동 실행 (Spring Boot 3.2+)
     *
     * 요청 처리 스레드가 Virtual Thread이므로
     * 블로킹 I/O가 발생해도 다른 요청 처리 가능
     */
    @GetMapping("/{orderId}/details")
    public OrderDetailResponse getOrderDetails(@PathVariable Long orderId) {
        // ✅ 3개 외부 API 병렬 호출 (Virtual Thread 활용)
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            Future<Order> orderFuture = executor.submit(() ->
                orderService.getOrder(orderId));

            Future<Payment> paymentFuture = executor.submit(() ->
                paymentService.getPayment(orderId));

            Future<Shipping> shippingFuture = executor.submit(() ->
                shippingService.getShipping(orderId));

            // 3개 작업 완료 대기 (Virtual Thread는 블로킹되어도 다른 작업 실행)
            Order order = orderFuture.get();
            Payment payment = paymentFuture.get();
            Shipping shipping = shippingFuture.get();

            return OrderDetailResponse.from(order, payment, shipping);
        } catch (Exception e) {
            throw new OrderProcessingException(e);
        }
    }
}
```

**성능 개선**:
- Before (순차 호출): 50ms (Order) + 100ms (Payment) + 80ms (Shipping) = **230ms**
- After (병렬 호출): max(50ms, 100ms, 80ms) = **100ms** (2.3배 빠름)

---

### ✅ Example 2: StructuredTaskScope (Java 21)

```java
import java.util.concurrent.StructuredTaskScope;

/**
 * Structured Concurrency로 안전한 병렬 처리
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OrderAggregationService {

    /**
     * ✅ StructuredTaskScope - 안전한 병렬 작업
     *
     * - 부모 스코프가 종료되면 모든 자식 작업도 자동 취소
     * - 리소스 누수 방지
     * - 명시적인 생명주기 관리
     */
    public OrderDetailResponse getOrderDetailsStructured(Long orderId)
            throws InterruptedException, ExecutionException {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // ✅ 3개 작업 병렬 실행 (Virtual Thread 자동 사용)
            Future<Order> orderFuture = scope.fork(() ->
                orderService.getOrder(orderId));

            Future<Payment> paymentFuture = scope.fork(() ->
                paymentService.getPayment(orderId));

            Future<Shipping> shippingFuture = scope.fork(() ->
                shippingService.getShipping(orderId));

            // ✅ 모든 작업 완료 대기 (하나라도 실패하면 나머지 자동 취소)
            scope.join();
            scope.throwIfFailed();

            return OrderDetailResponse.from(
                orderFuture.resultNow(),
                paymentFuture.resultNow(),
                shippingFuture.resultNow()
            );
        }
    }
}
```

**StructuredTaskScope 장점**:
- ✅ 자동 리소스 정리 (try-with-resources)
- ✅ 부분 실패 시 나머지 작업 자동 취소
- ✅ 명시적인 에러 전파 (`throwIfFailed()`)
- ✅ Virtual Thread 자동 사용

---

## 🔧 Virtual Threads 설정

### Spring Boot 3.2+ 설정

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true  # ✅ Virtual Threads 활성화

# Tomcat 설정 (기본값 유지 - Virtual Threads 사용)
server:
  tomcat:
    threads:
      max: 200  # Virtual Thread 사용 시 의미 없음 (제한 없음)
      min-spare: 10
```

### Spring Boot 3.1 이하 (수동 설정)

```java
package com.company.application.config;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;

/**
 * Virtual Thread 수동 설정 (Spring Boot 3.1 이하)
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
public class VirtualThreadConfig {

    /**
     * Tomcat에서 Virtual Thread 사용
     */
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }

    /**
     * @Async에서 Virtual Thread 사용
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

---

## ⚠️ Virtual Threads 제약사항

### 1. CPU 집약적 작업에는 부적합

```java
// ❌ Virtual Thread - CPU 집약적 작업
public void cpuIntensiveTask() {
    Thread.ofVirtual().start(() -> {
        // CPU 집약적 계산 (암호화, 이미지 처리 등)
        for (int i = 0; i < 1_000_000_000; i++) {
            Math.sqrt(i);
        }
    });
}

// ✅ Platform Thread - CPU 집약적 작업
public void cpuIntensiveTaskCorrect() {
    ForkJoinPool.commonPool().submit(() -> {
        // ForkJoinPool이 CPU 집약적 작업에 더 적합
        for (int i = 0; i < 1_000_000_000; i++) {
            Math.sqrt(i);
        }
    });
}
```

### 2. Pinning 문제 (synchronized 블록)

```java
// ⚠️ Pinning - Virtual Thread가 Platform Thread에 고정됨
public void problematicSynchronized() {
    Thread.ofVirtual().start(() -> {
        synchronized (lock) {  // ⚠️ Pinning 발생!
            // I/O 작업 시 Virtual Thread의 이점 상실
            Thread.sleep(Duration.ofSeconds(1));
        }
    });
}

// ✅ ReentrantLock 사용 (Pinning 없음)
public void improvedLocking() {
    ReentrantLock lock = new ReentrantLock();
    Thread.ofVirtual().start(() -> {
        lock.lock();
        try {
            // I/O 작업 시에도 Virtual Thread 정상 동작
            Thread.sleep(Duration.ofSeconds(1));
        } finally {
            lock.unlock();
        }
    });
}
```

### 3. ThreadLocal 사용 주의

```java
// ⚠️ ThreadLocal - Virtual Thread에서 메모리 사용량 증가
private static final ThreadLocal<UserContext> userContext = new ThreadLocal<>();

// 문제: 100만 개 Virtual Thread → 100만 개 UserContext 인스턴스
// 해결: ScopedValue 사용 (Java 21+)
private static final ScopedValue<UserContext> userContext = ScopedValue.newInstance();
```

---

## 📋 Virtual Threads 체크리스트

### 활성화
- [ ] Spring Boot 3.2+ 사용
- [ ] `spring.threads.virtual.enabled=true` 설정
- [ ] Java 21+ 런타임 환경

### 사용 적합성
- [ ] I/O 집약적 작업 (DB, 외부 API, 파일 I/O)
- [ ] 높은 동시성 요구 (1000+ RPS)
- [ ] 블로킹 I/O 코드 스타일

### 제약사항 확인
- [ ] `synchronized` 대신 `ReentrantLock` 사용
- [ ] CPU 집약적 작업은 ForkJoinPool 사용
- [ ] ThreadLocal 대신 ScopedValue 고려

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
