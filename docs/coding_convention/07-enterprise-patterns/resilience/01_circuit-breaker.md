# Circuit Breaker - 장애 전파 차단

**목적**: Resilience4j Circuit Breaker로 연쇄 장애 방지 및 빠른 실패 처리

**관련 문서**:
- [Retry and Timeout](./02_retry-and-timeout.md)
- [Bulkhead Pattern](./03_bulkhead-pattern.md)

**필수 버전**: Spring Boot 3.0+, Resilience4j 2.0+

---

## 📌 핵심 원칙

### Circuit Breaker란?

1. **연쇄 장애 방지**: 외부 서비스 장애 시 빠른 실패
2. **3가지 상태**: Closed → Open → Half-Open
3. **Fallback**: 장애 시 대체 응답
4. **자동 복구**: 일정 시간 후 재시도

### Circuit Breaker가 필요한 이유

- ✅ **빠른 실패**: 타임아웃 대기 없이 즉시 실패 반환
- ✅ **리소스 절약**: 실패 확실한 요청에 리소스 낭비 방지
- ✅ **복구 시간 확보**: 외부 서비스가 복구될 시간 제공

---

## ❌ Circuit Breaker 없는 문제점

### 문제: 외부 서비스 장애 시 연쇄 장애

```java
// ❌ Before - Circuit Breaker 없음
@Service
public class OrderService {

    private final PaymentClient paymentClient;

    /**
     * ❌ 문제점:
     * - Payment API 다운 시 모든 요청이 30초 타임아웃 대기
     * - 100 req/sec → 100 * 30초 = 3000개 스레드 블로킹
     * - 서버 리소스 고갈 → 전체 서비스 다운
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderCommand command) {
        Order order = orderRepository.save(Order.create(command));

        try {
            // ⚠️ Payment API 호출 (30초 타임아웃)
            PaymentResponse payment = paymentClient.charge(order.getTotalAmount());

            order.markAsPaid(payment.transactionId());
            return OrderResponse.from(order);

        } catch (PaymentException e) {
            // ⚠️ 30초 후 예외 발생 → 너무 늦음!
            order.cancel("Payment failed");
            throw new OrderCreationFailedException(e);
        }
    }
}
```

**문제 시나리오**:
```
1. Payment API 다운 (응답 없음)
2. 100 req/sec → 모두 30초 타임아웃 대기
3. 30초 * 100 req = 3000개 스레드 블로킹
4. 서버 리소스 고갈 → 전체 서비스 다운 (연쇄 장애)
```

---

## ✅ Circuit Breaker 패턴

### 패턴: Resilience4j 통합

```java
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.0.2'
}
```

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    configs:
      default:
        # ✅ 실패율 50% 이상 시 Circuit Open
        failure-rate-threshold: 50
        # ✅ 최소 10개 요청 이후 실패율 계산
        minimum-number-of-calls: 10
        # ✅ Sliding Window (100개 요청 기준)
        sliding-window-size: 100
        sliding-window-type: COUNT_BASED
        # ✅ Open 상태 60초 유지 → Half-Open 전환
        wait-duration-in-open-state: 60s
        # ✅ Half-Open에서 5개 테스트 후 Close 결정
        permitted-number-of-calls-in-half-open-state: 5
        # ✅ 느린 호출 기준 (2초 이상)
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 2s
        # ✅ 예외 발생 시 실패로 기록
        record-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - java.util.concurrent.TimeoutException
        # ✅ 특정 예외 무시 (실패로 기록 안함)
        ignore-exceptions:
          - com.company.exception.BusinessException

    instances:
      paymentService:
        base-config: default
```

```java
package com.company.application.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * Order Service - Circuit Breaker 적용
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OrderService {

    /**
     * ✅ @CircuitBreaker - 자동 장애 차단
     *
     * - Closed: 정상 동작
     * - Open: 즉시 실패 반환 (Fallback)
     * - Half-Open: 일부 요청으로 테스트
     */
    @CircuitBreaker(name = "paymentService", fallbackMethod = "createOrderFallback")
    @Transactional
    public OrderResponse createOrder(CreateOrderCommand command) {
        Order order = orderRepository.save(Order.create(command));

        // ✅ Payment API 호출 (Circuit Breaker 보호)
        PaymentResponse payment = paymentClient.charge(order.getTotalAmount());

        order.markAsPaid(payment.transactionId());
        return OrderResponse.from(order);
    }

    /**
     * ✅ Fallback 메서드
     *
     * - Circuit Open 시 자동 호출
     * - 대체 응답 제공
     */
    private OrderResponse createOrderFallback(CreateOrderCommand command, Exception e) {
        log.warn("Circuit breaker activated. Creating order without payment: {}", e.getMessage());

        // ✅ 대체 전략: 결제 없이 주문 생성 (나중에 재시도)
        Order order = orderRepository.save(Order.createPendingPayment(command));

        return OrderResponse.from(order);
    }
}
```

**Circuit Breaker 상태 전환**:
```
[Closed 상태] (정상)
  ↓ 실패율 50% 초과 (10개 중 5개 실패)
[Open 상태] (차단)
  - 모든 요청 즉시 실패 (Fallback 호출)
  - 60초 대기
  ↓ 60초 경과
[Half-Open 상태] (테스트)
  - 5개 요청 테스트
  - 성공률 > 50% → Closed
  - 실패율 > 50% → Open (다시 60초 대기)
```

---

## 🎯 실전 예제: Monitoring

### ✅ Example: Circuit Breaker 상태 모니터링

```java
/**
 * Circuit Breaker Monitoring
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class CircuitBreakerMonitoring {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;

    /**
     * ✅ Circuit Breaker 이벤트 리스닝
     */
    @PostConstruct
    public void registerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Circuit Breaker state changed: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState());

                    // ✅ Metrics 기록
                    meterRegistry.counter("circuit_breaker.state_transition",
                        "name", circuitBreaker.getName(),
                        "from", event.getStateTransition().getFromState().name(),
                        "to", event.getStateTransition().getToState().name()
                    ).increment();
                });

            circuitBreaker.getEventPublisher()
                .onCallNotPermitted(event -> {
                    log.warn("Circuit Breaker blocked call: {}",
                        event.getCircuitBreakerName());
                });
        });
    }

    /**
     * ✅ 현재 Circuit Breaker 상태 조회 API
     */
    @GetMapping("/actuator/circuit-breakers")
    public Map<String, CircuitBreakerStatus> getCircuitBreakerStatus() {
        return circuitBreakerRegistry.getAllCircuitBreakers().stream()
            .collect(Collectors.toMap(
                CircuitBreaker::getName,
                cb -> new CircuitBreakerStatus(
                    cb.getState().name(),
                    cb.getMetrics().getFailureRate(),
                    cb.getMetrics().getNumberOfSuccessfulCalls(),
                    cb.getMetrics().getNumberOfFailedCalls()
                )
            ));
    }

    public record CircuitBreakerStatus(
        String state,
        float failureRate,
        long successfulCalls,
        long failedCalls
    ) {}
}
```

---

## 🎯 실전 예제: Application Layer UseCase 통합

### ✅ Example: UseCase 패턴에서 Circuit Breaker 적용

**관련 문서**: [UseCase Pattern](../../03-application-layer/use-case-pattern/)

#### UseCase 패턴이란?

Application Layer에서 하나의 비즈니스 유스케이스를 표현하는 패턴으로, 다음 특징을 가집니다:
- 단일 public 메서드 (`execute()`)
- Command 객체로 입력 받기
- 트랜잭션 경계 분리 (외부 API 호출은 트랜잭션 밖)
- 명확한 책임 분리 (조율자 역할)

#### ❌ Before - Circuit Breaker 없는 UseCase

```java
package com.company.application.usecase;

/**
 * ❌ 문제점: Circuit Breaker 없음
 *
 * - Payment API 다운 시 모든 요청이 타임아웃 대기 (30초)
 * - 100 req/sec → 3000개 스레드 블로킹
 * - 서버 리소스 고갈
 */
@UseCase
public class CreateOrderUseCase {

    private final LoadCustomerPort loadCustomerPort;
    private final ChargePaymentPort chargePaymentPort;
    private final OrderPersistenceService persistenceService;

    public OrderResponse execute(CreateOrderCommand command) {
        // 1. 고객 정보 조회 (내부 DB - OK)
        Customer customer = loadCustomerPort.loadById(command.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(command.customerId()));

        // ❌ 2. Payment API 호출 (외부 API - Circuit Breaker 없음)
        //    문제: Payment API 다운 시 30초 타임아웃 대기
        PaymentResponse payment = chargePaymentPort.charge(
            command.totalAmount(),
            customer.getPaymentMethod()
        );

        // 3. Order 생성 및 저장 (트랜잭션)
        Order order = Order.create(
            customer.getId(),
            command.items(),
            payment.getTransactionId()
        );

        return persistenceService.saveOrder(order);
    }
}
```

#### ✅ After - Circuit Breaker 적용한 UseCase

```java
package com.company.application.usecase;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * Create Order UseCase - Circuit Breaker 적용
 *
 * UseCase 패턴:
 * - 단일 public 메서드 (execute)
 * - Command 객체로 입력
 * - 외부 API 호출은 Circuit Breaker로 보호
 * - 트랜잭션은 Persistence Service에 위임
 *
 * @author development-team
 * @since 1.0.0
 */
@UseCase
public class CreateOrderUseCase {

    private final LoadCustomerPort loadCustomerPort;
    private final ChargePaymentPort chargePaymentPort;
    private final OrderPersistenceService persistenceService;

    /**
     * ✅ UseCase 진입점
     *
     * - @CircuitBreaker 적용 (외부 API 보호)
     * - Fallback 전략 정의
     * - 트랜잭션 없음 (외부 API 호출 포함)
     */
    @CircuitBreaker(name = "paymentService", fallbackMethod = "executeWithPendingPayment")
    public OrderResponse execute(CreateOrderCommand command) {
        // 1. 고객 정보 조회 (내부 DB - Circuit Breaker 불필요)
        Customer customer = loadCustomerPort.loadById(command.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(command.customerId()));

        // ✅ 2. Payment API 호출 (Circuit Breaker 보호)
        //    - Closed: 정상 호출
        //    - Open: 즉시 Fallback 실행 (30초 대기 없음)
        PaymentResponse payment = chargePaymentPort.charge(
            command.totalAmount(),
            customer.getPaymentMethod()
        );

        // 3. Order 생성 및 저장 (별도 트랜잭션)
        Order order = Order.create(
            customer.getId(),
            command.items(),
            payment.getTransactionId()
        );

        return persistenceService.saveOrder(order);
    }

    /**
     * ✅ Fallback 메서드 - Circuit Open 시
     *
     * - Payment 없이 주문 생성 (PENDING_PAYMENT 상태)
     * - 나중에 재시도 또는 수동 처리
     * - 서비스 가용성 유지 (일부 기능 저하)
     */
    private OrderResponse executeWithPendingPayment(
            CreateOrderCommand command,
            Exception e) {

        log.warn("Circuit breaker activated. Creating order without payment: {}",
            e.getMessage());

        // 고객 정보 조회 (동일)
        Customer customer = loadCustomerPort.loadById(command.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(command.customerId()));

        // ✅ 결제 없이 주문 생성 (PENDING_PAYMENT)
        Order order = Order.createPendingPayment(
            customer.getId(),
            command.items()
        );

        OrderResponse response = persistenceService.saveOrder(order);

        // 메시지 추가: 결제 대기 중
        response.setWarning("Payment processing delayed. Order will be confirmed shortly.");

        return response;
    }
}
```

#### UseCase 레이어 아키텍처

```
┌─────────────────────────────────────────┐
│  Controller (REST API)                  │
│  - HTTP 요청/응답 변환                    │
│  - DTO → Command 변환                    │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  UseCase (Application Layer)            │
│  ✅ @CircuitBreaker 적용 (외부 API 보호) │
│  ✅ Fallback 메서드 구현                 │
│  ❌ @Transactional 없음                  │
│  - 비즈니스 흐름 조율                     │
│  - 외부 Port 호출                        │
└────────────────┬────────────────────────┘
                 │
        ┌────────┴────────┐
        ▼                 ▼
┌──────────────┐  ┌──────────────────────┐
│ External API │  │ Persistence Service  │
│ (Port)       │  │ (Internal)           │
│              │  │                      │
│ ✅ Circuit    │  │ ✅ @Transactional    │
│    Breaker   │  │ ❌ Circuit Breaker   │
│    보호됨     │  │    불필요            │
└──────────────┘  └──────────────────────┘
```

#### Circuit Breaker 상태별 동작

**Closed 상태** (정상):
```
1. Controller → UseCase.execute(command)
2. UseCase → ChargePaymentPort.charge() (외부 API)
3. Payment API → 성공 응답 (200ms)
4. UseCase → PersistenceService.saveOrder() (트랜잭션)
5. UseCase → Controller: OrderResponse (정상 완료)
```

**Open 상태** (장애):
```
1. Controller → UseCase.execute(command)
2. Circuit Breaker: OPEN 감지
3. ⚡ Fallback 즉시 실행 (타임아웃 대기 없음)
4. UseCase → executeWithPendingPayment()
5. UseCase → PersistenceService.saveOrder() (PENDING_PAYMENT)
6. UseCase → Controller: OrderResponse (경고 메시지 포함)

성능: 30초 → 50ms (99.8% 개선)
```

**Half-Open 상태** (테스트):
```
1. Controller → UseCase.execute(command)
2. Circuit Breaker: 5개 요청 테스트
3. 성공률 > 50% → Closed 전환
4. 실패율 > 50% → Open 유지 (60초 대기)
```

#### 다중 외부 API 호출 시 Circuit Breaker

```java
/**
 * UseCase with Multiple External APIs
 *
 * - 각 외부 API마다 독립적인 Circuit Breaker
 * - 하나의 서비스 장애가 다른 서비스 영향 없음
 */
@UseCase
public class CreateOrderUseCase {

    /**
     * ✅ Payment Circuit Breaker만 적용
     *
     * - Notification 실패는 Circuit Breaker 대상 아님
     */
    @CircuitBreaker(name = "paymentService", fallbackMethod = "executeWithPendingPayment")
    public OrderResponse execute(CreateOrderCommand command) {
        Customer customer = loadCustomerPort.loadById(command.customerId())
            .orElseThrow();

        // ✅ Payment API (Circuit Breaker 보호)
        PaymentResponse payment = chargePaymentPort.charge(
            command.totalAmount(),
            customer.getPaymentMethod()
        );

        Order order = Order.create(customer.getId(), command.items(), payment.getTransactionId());
        OrderResponse response = persistenceService.saveOrder(order);

        // ✅ Notification API (별도 Circuit Breaker - Best Effort)
        sendNotificationSafely(order, customer);

        return response;
    }

    /**
     * ✅ Notification은 별도 Circuit Breaker
     *
     * - 실패해도 주문 생성에는 영향 없음
     * - Best Effort 방식
     */
    @CircuitBreaker(name = "notificationService", fallbackMethod = "logNotificationFailure")
    private void sendNotificationSafely(Order order, Customer customer) {
        notificationPort.sendOrderConfirmation(order.getId(), customer.getEmail());
    }

    private void logNotificationFailure(Order order, Customer customer, Exception e) {
        log.warn("Notification failed for order: {}. Will retry later.", order.getId(), e);
        // Event 발행하여 재시도 큐에 추가
        eventPublisher.publishEvent(new NotificationFailedEvent(order.getId()));
    }
}
```

#### 성능 비교

**Before (Circuit Breaker 없음)**:
```
Payment API 다운 시:
- 요청 1: 30초 타임아웃 대기 → 실패
- 요청 2: 30초 타임아웃 대기 → 실패
- ...
- 요청 100: 30초 타임아웃 대기 → 실패

총 시간: 100 * 30초 = 3000초 (50분)
서버 상태: 3000개 스레드 블로킹 → 서비스 다운
```

**After (Circuit Breaker 적용)**:
```
Payment API 다운 시:
- 요청 1-10: 각각 30초 타임아웃 → 실패 (임계값 도달)
- Circuit Open (60초 대기)
- 요청 11-100: 즉시 Fallback 실행 (50ms)

총 시간: (10 * 30초) + (90 * 50ms) = 304.5초 (5분)
서버 상태: 정상 (스레드 블로킹 없음)
성능 개선: 90% (3000초 → 304.5초)
```

---

## 📋 Circuit Breaker 체크리스트

### 설계
- [ ] 실패율 임계값 설정 (50% 권장)
- [ ] Wait Duration 결정 (60초 권장)
- [ ] Fallback 전략 정의
- [ ] **UseCase별 Circuit Breaker 전략 수립**
- [ ] **다중 외부 API 호출 시 독립적 Circuit Breaker 설계**

### 구현
- [ ] `@CircuitBreaker` 모든 외부 API 호출에 적용
- [ ] Fallback 메서드 구현
- [ ] 예외 분류 (recordExceptions vs ignoreExceptions)
- [ ] **UseCase 진입점에 @CircuitBreaker 적용**
- [ ] **내부 DB 조회는 Circuit Breaker 제외**
- [ ] **Fallback 메서드에서 PENDING 상태 처리**

### 모니터링
- [ ] 상태 전환 이벤트 로깅
- [ ] Metrics 수집 (Micrometer)
- [ ] 알림 설정 (Open 상태 시)
- [ ] **UseCase별 Circuit Breaker 상태 추적**

---

## 📚 관련 문서

**필수 읽기**:
- [UseCase Pattern](../../03-application-layer/use-case-pattern/) - Application Layer 패턴
- [Transaction Boundaries](../../03-application-layer/transaction-management/01_transaction-boundaries.md) - 외부 API와 트랜잭션 분리

**연관 패턴**:
- [Retry and Timeout](./02_retry-and-timeout.md) - 재시도 전략
- [Bulkhead Pattern](./03_bulkhead-pattern.md) - 리소스 격리

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
