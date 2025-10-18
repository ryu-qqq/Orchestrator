# Retry and Timeout - 재시도 및 타임아웃 전략

**목적**: Exponential Backoff 기반 재시도 및 적절한 Timeout 설정

**관련 문서**:
- [Circuit Breaker](./01_circuit-breaker.md)
- [Bulkhead Pattern](./03_bulkhead-pattern.md)

**필수 버전**: Spring Boot 3.0+, Resilience4j 2.0+

---

## 📌 핵심 원칙

### Retry가 필요한 경우

1. **일시적 장애**: Network glitch, Timeout
2. **Transient Error**: 503 Service Unavailable
3. **Rate Limiting**: 429 Too Many Requests (잠시 후 재시도)

### Retry를 피해야 하는 경우

- ❌ **클라이언트 오류**: 400 Bad Request, 401 Unauthorized
- ❌ **영구적 장애**: 404 Not Found, 403 Forbidden
- ❌ **Idempotent 아님**: POST (중복 생성 위험)

---

## ✅ Retry 패턴

### 패턴: Exponential Backoff

```yaml
# application.yml
resilience4j:
  retry:
    configs:
      default:
        # ✅ 최대 3회 재시도
        max-attempts: 3
        # ✅ 초기 대기 시간 100ms
        wait-duration: 100ms
        # ✅ Exponential Backoff (2배씩 증가)
        exponential-backoff-multiplier: 2
        # ✅ 최대 대기 시간 2초
        exponential-max-wait-duration: 2s
        # ✅ 재시도 가능 예외
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException$ServiceUnavailable
          - java.util.concurrent.TimeoutException
        # ✅ 재시도 안함 예외
        ignore-exceptions:
          - org.springframework.web.client.HttpClientErrorException

    instances:
      paymentService:
        base-config: default
```

```java
package com.company.infrastructure.client;

import io.github.resilience4j.retry.annotation.Retry;

/**
 * Payment Client - Retry 적용
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class PaymentClient {

    private final RestTemplate restTemplate;

    /**
     * ✅ @Retry - Exponential Backoff
     *
     * - 1회 시도: 즉시
     * - 2회 시도: 100ms 후
     * - 3회 시도: 200ms 후 (100ms * 2)
     * - 4회 시도: 400ms 후 (200ms * 2)
     */
    @Retry(name = "paymentService", fallbackMethod = "chargePaymentFallback")
    public PaymentResponse charge(Money amount) {
        log.info("Attempting to charge payment: {}", amount);

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
            "https://payment-api.example.com/charges",
            new ChargeRequest(amount),
            PaymentResponse.class
        );

        return response.getBody();
    }

    /**
     * ✅ Fallback - 모든 재시도 실패 시
     */
    private PaymentResponse chargePaymentFallback(Money amount, Exception e) {
        log.error("All retry attempts failed for payment: {}", amount, e);

        throw new PaymentFailedException("Payment service unavailable", e);
    }
}
```

**Exponential Backoff 동작**:
```
시도 1: 즉시 → 실패 (503 Service Unavailable)
대기: 100ms
시도 2: 100ms 후 → 실패 (503)
대기: 200ms (100ms * 2)
시도 3: 300ms 후 → 실패 (503)
대기: 400ms (200ms * 2)
시도 4: 700ms 후 → 성공! ✅

총 경과 시간: 700ms (3회 재시도)
```

---

## ✅ Timeout 패턴

### 패턴: RestTemplate Timeout 설정

```java
package com.company.infrastructure.config;

/**
 * RestTemplate Configuration - Timeout 설정
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
public class RestTemplateConfig {

    /**
     * ✅ RestTemplate - Timeout 설정
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(2))  // ✅ 연결 타임아웃 2초
            .setReadTimeout(Duration.ofSeconds(5))     // ✅ 읽기 타임아웃 5초
            .build();
    }
}

/**
 * ✅ Resilience4j TimeLimiter (권장)
 */
@Configuration
public class TimeLimiterConfig {

    @Bean
    public TimeLimiter timeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(3))  // ✅ 전체 타임아웃 3초
            .cancelRunningFuture(true)  // ✅ Timeout 시 Future 취소
            .build();

        return TimeLimiter.of("default", config);
    }
}
```

```yaml
# application.yml - TimeLimiter 설정
resilience4j:
  timelimiter:
    configs:
      default:
        # ✅ 전체 실행 시간 제한 3초
        timeout-duration: 3s
        # ✅ Timeout 시 Future 취소
        cancel-running-future: true

    instances:
      paymentService:
        base-config: default
```

```java
/**
 * Payment Service - TimeLimiter 적용
 */
@Service
public class PaymentService {

    /**
     * ✅ TimeLimiter + Retry + CircuitBreaker 조합
     */
    @TimeLimiter(name = "paymentService")
    @Retry(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public CompletableFuture<PaymentResponse> processPaymentAsync(Money amount) {
        return CompletableFuture.supplyAsync(() -> {
            // ✅ 3초 이내 완료 필수
            return paymentClient.charge(amount);
        });
    }

    private CompletableFuture<PaymentResponse> processPaymentFallback(
            Money amount, Exception e) {
        log.error("Payment processing failed: {}", amount, e);

        return CompletableFuture.completedFuture(
            new PaymentResponse(null, "FAILED", e.getMessage())
        );
    }
}
```

---

## 🎯 실전 예제: Idempotency

### ✅ Example: 멱등성 보장

```java
/**
 * Idempotent Payment Request
 *
 * - Retry 시 중복 결제 방지
 * - Idempotency Key 사용
 *
 * @author development-team
 * @since 1.0.0
 */
public record ChargeRequest(
    String idempotencyKey,  // ✅ 고유 키 (UUID)
    Money amount,
    String currency
) {
    public static ChargeRequest create(Money amount) {
        return new ChargeRequest(
            UUID.randomUUID().toString(),  // ✅ 재시도 시 동일 키 사용
            amount,
            "KRW"
        );
    }
}

/**
 * Payment Client - Idempotency 보장
 */
@Component
public class PaymentClient {

    /**
     * ✅ Idempotency Key로 중복 방지
     */
    @Retry(name = "paymentService")
    public PaymentResponse charge(ChargeRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", request.idempotencyKey());  // ✅

        HttpEntity<ChargeRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
            "https://payment-api.example.com/charges",
            entity,
            PaymentResponse.class
        );

        return response.getBody();
    }
}

/**
 * Payment API Server - Idempotency 검증
 */
@RestController
public class PaymentApiController {

    private final PaymentRepository paymentRepository;

    @PostMapping("/charges")
    public ResponseEntity<PaymentResponse> charge(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ChargeRequest request) {

        // ✅ 이미 처리된 요청인지 확인
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            // ✅ 중복 요청 → 이전 결과 반환
            return ResponseEntity.ok(PaymentResponse.from(existing.get()));
        }

        // ✅ 새 요청 → 결제 처리
        Payment payment = Payment.create(idempotencyKey, request.amount());
        paymentRepository.save(payment);

        return ResponseEntity.ok(PaymentResponse.from(payment));
    }
}
```

---

## 🎯 실전 예제: Adapter 레이어 Port 구현

### ✅ Example: Adapter에서 Retry 및 Timeout 적용

**관련 문서**: [Hexagonal Architecture](../../02-domain-layer/package-guide/), [Port and Adapter](../../04-persistence-layer/)

#### Hexagonal Architecture에서의 Adapter

Adapter는 외부 시스템과의 통합을 담당하는 레이어로, 다음 책임을 가집니다:
- **Port 인터페이스 구현** (Application Layer에서 정의)
- **외부 시스템 호출** (REST API, Message Queue, S3 등)
- **Resilience 패턴 적용** (Retry, Timeout, Circuit Breaker)
- **데이터 변환** (외부 DTO ↔ Domain Entity)

#### 패키지 구조

```
infrastructure/
└── adapter/
    ├── client/           # 외부 API 클라이언트 (Adapter)
    │   ├── PaymentAdapter.java
    │   └── NotificationAdapter.java
    └── config/          # RestTemplate, WebClient 설정
        └── RestTemplateConfig.java

application/
└── port/
    └── out/             # Port 인터페이스 (Application Layer)
        ├── ChargePaymentPort.java
        └── SendNotificationPort.java
```

#### ❌ Before - Retry 없는 Adapter

```java
package com.company.infrastructure.adapter.client;

/**
 * ❌ 문제점: Retry 없음
 *
 * - 네트워크 일시적 장애 시 즉시 실패
 * - 503 Service Unavailable 에러 발생
 * - 재시도 없이 사용자에게 에러 반환
 */
@Component
public class PaymentAdapter implements ChargePaymentPort {

    private final RestTemplate restTemplate;
    private final PaymentProperties properties;

    @Override
    public PaymentResponse charge(Money amount, PaymentMethod paymentMethod) {
        // ❌ Retry 없음 - 일시적 장애도 즉시 실패
        ChargeRequest request = new ChargeRequest(
            UUID.randomUUID().toString(),  // Idempotency Key
            amount,
            paymentMethod
        );

        ResponseEntity<PaymentApiResponse> response = restTemplate.postForEntity(
            properties.getBaseUrl() + "/charges",
            request,
            PaymentApiResponse.class
        );

        return PaymentResponse.from(response.getBody());
    }
}
```

#### ✅ After - Retry 및 Timeout 적용한 Adapter

```java
package com.company.infrastructure.adapter.client;

import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

/**
 * Payment Adapter - Retry 및 Timeout 적용
 *
 * Adapter 책임:
 * - Port 인터페이스 구현
 * - 외부 Payment API 호출
 * - Retry 및 Timeout 적용 (Resilience)
 * - 도메인 객체 변환
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class PaymentAdapter implements ChargePaymentPort {

    private final RestTemplate restTemplate;
    private final PaymentProperties properties;

    /**
     * ✅ @Retry - Exponential Backoff
     * ✅ Idempotency Key로 중복 방지
     *
     * - 1회: 즉시
     * - 2회: 100ms 후
     * - 3회: 200ms 후 (100ms * 2)
     * - 4회: 400ms 후 (200ms * 2)
     *
     * 재시도 대상:
     * - 503 Service Unavailable
     * - TimeoutException
     * - Network Glitch
     */
    @Override
    @Retry(name = "paymentService", fallbackMethod = "chargePaymentFallback")
    @TimeLimiter(name = "paymentService")
    public PaymentResponse charge(Money amount, PaymentMethod paymentMethod) {
        log.info("Attempting to charge payment: {}", amount);

        // ✅ Idempotency Key 생성 (재시도 시 동일 키 사용)
        String idempotencyKey = UUID.randomUUID().toString();

        ChargeRequest request = new ChargeRequest(
            idempotencyKey,
            amount,
            paymentMethod
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);  // ✅ 중복 방지

        HttpEntity<ChargeRequest> entity = new HttpEntity<>(request, headers);

        // ✅ 외부 API 호출 (Timeout: 3초, Retry: 최대 3회)
        ResponseEntity<PaymentApiResponse> response = restTemplate.postForEntity(
            properties.getBaseUrl() + "/charges",
            entity,
            PaymentApiResponse.class
        );

        return PaymentResponse.from(response.getBody());
    }

    /**
     * ✅ Fallback - 모든 재시도 실패 시
     *
     * - 로깅 및 예외 변환
     * - Domain Exception 발행
     */
    private PaymentResponse chargePaymentFallback(
            Money amount,
            PaymentMethod paymentMethod,
            Exception e) {

        log.error("All retry attempts failed for payment: amount={}, method={}",
            amount, paymentMethod, e);

        throw new PaymentFailedException(
            "Payment service unavailable after retries",
            e
        );
    }
}
```

#### Adapter Configuration - RestTemplate Timeout

```java
package com.company.infrastructure.adapter.config;

/**
 * RestTemplate Configuration - Timeout 설정
 *
 * Adapter 레이어의 공통 설정:
 * - Connection Timeout: 연결 타임아웃
 * - Read Timeout: 읽기 타임아웃
 * - Interceptor: 공통 헤더, 로깅
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
public class RestTemplateConfig {

    /**
     * ✅ RestTemplate - Timeout 설정
     *
     * - connectTimeout: 2초 (서버 연결 대기)
     * - readTimeout: 5초 (응답 대기)
     * - 총 최대 시간: 7초
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(2))  // ✅ 연결 타임아웃 2초
            .setReadTimeout(Duration.ofSeconds(5))     // ✅ 읽기 타임아웃 5초
            .additionalInterceptors(new LoggingInterceptor())
            .build();
    }

    /**
     * ✅ 로깅 Interceptor
     */
    private static class LoggingInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {

            log.debug("Request: {} {}", request.getMethod(), request.getURI());

            ClientHttpResponse response = execution.execute(request, body);

            log.debug("Response: {}", response.getStatusCode());

            return response;
        }
    }
}
```

#### Resilience4j Configuration

```yaml
# application.yml
resilience4j:
  retry:
    configs:
      default:
        # ✅ 최대 3회 재시도
        max-attempts: 3
        # ✅ 초기 대기 시간 100ms
        wait-duration: 100ms
        # ✅ Exponential Backoff (2배씩 증가)
        exponential-backoff-multiplier: 2
        # ✅ 최대 대기 시간 2초
        exponential-max-wait-duration: 2s
        # ✅ 재시도 가능 예외
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException$ServiceUnavailable
          - java.util.concurrent.TimeoutException
          - java.net.SocketTimeoutException
        # ✅ 재시도 안함 예외 (클라이언트 에러)
        ignore-exceptions:
          - org.springframework.web.client.HttpClientErrorException
          - com.company.exception.BusinessException

    instances:
      paymentService:
        base-config: default

      notificationService:
        base-config: default
        max-attempts: 2  # Notification은 2회만 재시도

  timelimiter:
    configs:
      default:
        # ✅ 전체 실행 시간 제한 3초
        timeout-duration: 3s
        # ✅ Timeout 시 Future 취소
        cancel-running-future: true

    instances:
      paymentService:
        base-config: default

      notificationService:
        timeout-duration: 5s  # Notification은 5초 허용
```

#### 다중 Adapter에서의 Retry 전략

```java
/**
 * Notification Adapter - Best Effort Retry
 *
 * - 실패해도 비즈니스 로직에 영향 없음
 * - 재시도 횟수 적게 (2회)
 * - Timeout 길게 (5초)
 */
@Component
public class NotificationAdapter implements SendNotificationPort {

    private final RestTemplate restTemplate;

    /**
     * ✅ Best Effort - 실패해도 무방
     *
     * - 재시도: 2회 (적음)
     * - Timeout: 5초 (길게)
     * - Fallback: 로깅만 (Exception 안던짐)
     */
    @Override
    @Retry(name = "notificationService", fallbackMethod = "sendNotificationFallback")
    @TimeLimiter(name = "notificationService")
    public void sendNotification(CustomerId customerId, String message) {
        log.info("Sending notification to customer: {}", customerId);

        NotificationRequest request = new NotificationRequest(customerId, message);

        restTemplate.postForEntity(
            notificationProperties.getBaseUrl() + "/send",
            request,
            Void.class
        );
    }

    /**
     * ✅ Fallback - 로깅만 (Exception 안던짐)
     *
     * - Best Effort이므로 실패해도 무방
     * - Event 발행하여 나중에 재시도
     */
    private void sendNotificationFallback(
            CustomerId customerId,
            String message,
            Exception e) {

        log.warn("Failed to send notification to customer: {}. Will retry later.",
            customerId, e);

        // Event 발행하여 재시도 큐에 추가 (선택적)
        eventPublisher.publishEvent(
            new NotificationFailedEvent(customerId, message)
        );
    }
}
```

#### Adapter 레이어 아키텍처

```
┌─────────────────────────────────────────┐
│  Application Layer (UseCase)            │
│  - Port 인터페이스 정의                   │
│  - 비즈니스 로직 조율                     │
└────────────────┬────────────────────────┘
                 │ (의존성 역전)
                 ▼
┌─────────────────────────────────────────┐
│  Port Interface (Application/port/out)  │
│  - ChargePaymentPort                    │
│  - SendNotificationPort                 │
└────────────────┬────────────────────────┘
                 │ (구현)
                 ▼
┌─────────────────────────────────────────┐
│  Adapter Layer (Infrastructure)         │
│  ✅ @Retry 적용 (Exponential Backoff)   │
│  ✅ @TimeLimiter 적용 (Timeout 제어)    │
│  ✅ Idempotency Key 사용                │
│  - PaymentAdapter                       │
│  - NotificationAdapter                  │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  External System                        │
│  - Payment API                          │
│  - Notification API                     │
│  - S3, SQS, SNS, etc.                   │
└─────────────────────────────────────────┘
```

#### Retry 전략 비교

| Adapter | Retry 횟수 | Timeout | Fallback 전략 | 이유 |
|---------|-----------|---------|--------------|------|
| Payment | 3회 | 3초 | Exception 발생 | 결제는 필수 작업 |
| Notification | 2회 | 5초 | 로깅만 (Event 발행) | Best Effort |
| S3 Upload | 5회 | 10초 | Exception 발생 | 재시도 가능성 높음 |

#### 성능 비교

**Before (Retry 없음)**:
```
일시적 장애 발생 시:
- 요청 1: 네트워크 오류 → 즉시 실패
- 사용자: 에러 화면
- 재시도: 수동 (사용자가 다시 클릭)

실패율: 10% (일시적 장애로 인한 불필요한 실패)
```

**After (Retry 적용)**:
```
일시적 장애 발생 시:
- 요청 1: 네트워크 오류 (즉시)
- 대기: 100ms
- 요청 2: 성공! ✅

실패율: 1% (재시도로 대부분 성공)
성공률: 90% → 99% (9% 개선)
평균 응답 시간: 약간 증가하지만 사용자 경험 크게 향상
```

---

## 📋 Retry and Timeout 체크리스트

### 설계
- [ ] Retry 대상 선정 (Transient Error만)
- [ ] Exponential Backoff 설정 (2배 증가)
- [ ] Timeout 계층별 설정 (Connect, Read, Total)
- [ ] **Adapter별 Retry 전략 차별화 (Critical vs Best Effort)**
- [ ] **Idempotency 보장 방법 설계**

### 구현
- [ ] `@Retry` 일시적 장애에만 적용
- [ ] Idempotency Key 사용 (POST 요청)
- [ ] TimeLimiter로 전체 시간 제한
- [ ] **Adapter 레이어에 @Retry 적용**
- [ ] **Port 인터페이스와 구현 분리**
- [ ] **Fallback 메서드에서 Exception vs Event 발행 전략 구현**

### 모니터링
- [ ] Retry 횟수 메트릭
- [ ] Timeout 발생 빈도
- [ ] 평균 응답 시간
- [ ] **Adapter별 성공률 및 재시도 횟수 추적**

---

## 📚 관련 문서

**필수 읽기**:
- [Hexagonal Architecture](../../02-domain-layer/package-guide/) - Port and Adapter 패턴
- [Circuit Breaker](./01_circuit-breaker.md) - Circuit Breaker와 Retry 조합

**연관 패턴**:
- [Bulkhead Pattern](./03_bulkhead-pattern.md) - 리소스 격리
- [Transaction Boundaries](../../03-application-layer/transaction-management/01_transaction-boundaries.md) - 트랜잭션 분리

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
