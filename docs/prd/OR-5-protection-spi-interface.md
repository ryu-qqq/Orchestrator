# OR-5: Protection SPI 인터페이스 정의 및 NoOp 구현 PRD

**작성일**: 2025-10-18
**작성자**: Orchestrator Team
**Epic**: OR-1 (Epic A: Core API 및 계약 구현)
**버전**: 1.0.0

---

## 1. Overview

### 1.1 목적

OR-5는 **Protection SPI 인터페이스 정의 및 NoOp 구현**을 하는 작업으로, Orchestrator Core SDK의 보호 메커니즘 확장점을 제공합니다. 이 작업은 Circuit Breaker, Timeout Policy, Rate Limiter, Bulkhead, Hedge Policy 등의 보호 훅(Protection Hook)을 정의하여, 외부 API 호출 시 발생할 수 있는 장애를 격리하고 시스템 안정성을 보장하는 인터페이스를 제공합니다.

### 1.2 Epic OR-1과의 관계

Epic OR-1은 Orchestrator Core SDK의 기반 인프라를 구축하는 작업이며, OR-5는 다음 컴포넌트와 밀접하게 연관됩니다:

- **OR-2 (타입 모델 및 상태머신)**: OpId, Envelope, Outcome을 활용
- **OR-3 (Inline Fast-Path Runner)**: 실행 전 Protection Hook 적용
- **OR-4 (Queue Worker Runner)**: 큐 메시지 처리 전 Protection Hook 적용
- **핵심 API 3개**: Executor가 Protection Hook 체인을 실행

### 1.3 해결하는 문제

외부 API 호출을 포함하는 업무 플로우에서 발생하는 보호 메커니즘의 문제점:

1. **장애 전파 (Cascading Failure)**: 외부 API 장애가 전체 시스템으로 전파됨
2. **리소스 고갈**: 느린 외부 API로 인한 스레드 고갈, 메모리 부족
3. **과부하 (Overload)**: 요청 폭증 시 시스템 다운
4. **타임아웃 부재**: 무한 대기로 인한 시스템 멈춤
5. **재시도 폭증 (Retry Storm)**: 동시 재시도로 인한 외부 API 과부하

**Protection SPI의 해결책**:

- **Circuit Breaker**: 장애 감지 및 빠른 실패(Fail-Fast)로 장애 격리
- **TimeoutPolicy**: 작업별 타임아웃 설정으로 무한 대기 방지
- **RateLimiter**: 초당 요청 수 제한으로 과부하 방지
- **Bulkhead**: 동시 실행 수 제한으로 리소스 격리
- **HedgePolicy**: 헤징(Hedging) 재요청으로 지연 최소화
- **NoOp 구현**: 프로덕션 전 개발/테스트 환경에서 보호 없이 실행

---

## 2. Requirements

### 2.1 기능 요구사항

#### FR-1: CircuitBreaker SPI 정의

**설명**: Circuit Breaker 패턴을 구현하기 위한 인터페이스

**상태 모델**:
```
CLOSED (정상)
  │
  ▼ (실패율 임계값 초과)
OPEN (차단)
  │
  ▼ (대기 시간 경과)
HALF_OPEN (반개방)
  │
  ├─► 성공 → CLOSED
  └─► 실패 → OPEN
```

**메서드 시그니처**:
```java
/**
 * Circuit Breaker SPI.
 *
 * <p>외부 API 호출의 실패율을 추적하고, 임계값 초과 시 빠르게 실패(Fail-Fast)하여
 * 장애가 전체 시스템으로 전파되는 것을 방지합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface CircuitBreaker {

    /**
     * Circuit Breaker 통과 허용 여부 확인.
     *
     * <p>현재 Circuit Breaker 상태에 따라 요청 통과를 허용할지 결정합니다.</p>
     *
     * <ul>
     *   <li>CLOSED: 항상 true 반환 (정상 통과)</li>
     *   <li>OPEN: 항상 false 반환 (즉시 차단)</li>
     *   <li>HALF_OPEN: 제한된 수의 요청만 true (테스트 통과)</li>
     * </ul>
     *
     * @param opId Operation ID (통계 및 로깅용)
     * @return true: 요청 통과 허용, false: 요청 차단
     */
    boolean tryAcquire(OpId opId);

    /**
     * 실행 성공 기록.
     *
     * <p>외부 API 호출 성공 시 호출되어 Circuit Breaker 상태를 갱신합니다.</p>
     *
     * <ul>
     *   <li>CLOSED: 성공 카운터 증가</li>
     *   <li>HALF_OPEN: 연속 성공 임계값 도달 시 CLOSED로 전이</li>
     * </ul>
     *
     * @param opId Operation ID
     */
    void recordSuccess(OpId opId);

    /**
     * 실행 실패 기록.
     *
     * <p>외부 API 호출 실패 시 호출되어 Circuit Breaker 상태를 갱신합니다.</p>
     *
     * <ul>
     *   <li>CLOSED: 실패율 계산 후 임계값 초과 시 OPEN으로 전이</li>
     *   <li>HALF_OPEN: 즉시 OPEN으로 전이</li>
     * </ul>
     *
     * @param opId Operation ID
     * @param throwable 발생한 예외
     */
    void recordFailure(OpId opId, Throwable throwable);

    /**
     * 현재 Circuit Breaker 상태 조회.
     *
     * @return CLOSED, OPEN, HALF_OPEN 중 하나
     */
    CircuitBreakerState getState();

    /**
     * Circuit Breaker를 CLOSED 상태로 강제 리셋.
     *
     * <p>수동 복구 또는 테스트 목적으로 사용됩니다.</p>
     */
    void reset();
}

/**
 * Circuit Breaker 상태.
 */
public enum CircuitBreakerState {
    /** 정상 상태 (요청 통과) */
    CLOSED,

    /** 차단 상태 (요청 즉시 거부) */
    OPEN,

    /** 반개방 상태 (일부 요청만 통과하여 테스트) */
    HALF_OPEN
}
```

**설정 파라미터 (참고용)**:
```java
public class CircuitBreakerConfig {
    private double failureRateThreshold = 0.5;      // 실패율 임계값 (50%)
    private int minimumNumberOfCalls = 10;          // 최소 호출 수
    private long waitDurationInOpenStateMs = 60000; // OPEN 상태 대기 시간 (1분)
    private int permittedNumberOfCallsInHalfOpen = 3; // HALF_OPEN 상태 허용 호출 수
}
```

**의존성**: OR-2의 OpId

---

#### FR-2: TimeoutPolicy SPI 정의

**설명**: 작업별 타임아웃 정책을 적용하기 위한 인터페이스

**메서드 시그니처**:
```java
/**
 * Timeout Policy SPI.
 *
 * <p>외부 API 호출의 최대 허용 시간을 설정하여 무한 대기를 방지합니다.</p>
 *
 * <p><strong>타임아웃 적용 방식:</strong></p>
 * <ul>
 *   <li>perAttemptTimeout: 각 재시도마다 적용되는 타임아웃</li>
 *   <li>totalTimeout: 모든 재시도를 포함한 전체 타임아웃 (향후 확장)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface TimeoutPolicy {

    /**
     * 시도당(per-attempt) 타임아웃 시간 조회.
     *
     * <p>이 시간 내에 작업이 완료되지 않으면 TimeoutException이 발생합니다.</p>
     *
     * @param opId Operation ID
     * @return 타임아웃 시간 (밀리초), 0은 타임아웃 없음을 의미
     */
    long getPerAttemptTimeoutMs(OpId opId);

    /**
     * 타임아웃 발생 기록.
     *
     * <p>타임아웃이 발생했음을 기록하여 통계 및 모니터링에 활용합니다.</p>
     *
     * @param opId Operation ID
     * @param elapsedMs 실제 경과 시간 (밀리초)
     */
    void recordTimeout(OpId opId, long elapsedMs);
}
```

**설정 파라미터 (참고용)**:
```java
public class TimeoutConfig {
    private long perAttemptTimeoutMs = 30000;  // 기본 30초
    private long totalTimeoutMs = 0;           // 0 = 무제한 (향후 확장)
}
```

**의존성**: OR-2의 OpId

---

#### FR-3: RateLimiter SPI 정의

**설명**: 초당 요청 수 제한을 적용하기 위한 인터페이스

**메서드 시그니처**:
```java
/**
 * Rate Limiter SPI.
 *
 * <p>초당 요청 수를 제한하여 외부 API 과부하 및 내부 리소스 고갈을 방지합니다.</p>
 *
 * <p><strong>Rate Limiting 알고리즘:</strong></p>
 * <ul>
 *   <li>Token Bucket: 일정 속도로 토큰 생성, 요청 시 토큰 소비</li>
 *   <li>Fixed Window: 시간 윈도우 내 요청 수 카운트</li>
 *   <li>Sliding Window: 이동 윈도우로 더 정확한 제한</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface RateLimiter {

    /**
     * Rate Limiter 통과 허용 여부 확인 (비블로킹).
     *
     * <p>현재 Rate Limit 내에서 요청 처리가 가능한지 즉시 확인합니다.
     * 허용되지 않으면 false를 반환하며, 대기하지 않습니다.</p>
     *
     * @param opId Operation ID (통계 및 로깅용)
     * @return true: 요청 허용, false: Rate Limit 초과
     */
    boolean tryAcquire(OpId opId);

    /**
     * Rate Limiter 통과 허용 여부 확인 (타임아웃 대기).
     *
     * <p>지정된 시간 동안 대기하여 Rate Limit 허용을 시도합니다.
     * 대기 시간 내에 허용되면 true, 타임아웃 시 false를 반환합니다.</p>
     *
     * @param opId Operation ID
     * @param timeoutMs 최대 대기 시간 (밀리초)
     * @return true: 요청 허용, false: 타임아웃 또는 인터럽트
     * @throws InterruptedException 대기 중 인터럽트 발생
     */
    boolean tryAcquire(OpId opId, long timeoutMs) throws InterruptedException;

    /**
     * Rate Limiter 설정 정보 조회.
     *
     * @return Rate Limiter 설정 (QPS 등)
     */
    RateLimiterConfig getConfig();
}

/**
 * Rate Limiter 설정.
 */
public class RateLimiterConfig {
    private double permitsPerSecond;  // 초당 허용 요청 수 (예: 100.0)
    private int maxBurstSize;         // 버스트 허용량 (Token Bucket의 버킷 크기)
}
```

**의존성**: OR-2의 OpId

---

#### FR-4: Bulkhead SPI 정의

**설명**: 동시 실행 수 제한을 적용하여 리소스 격리

**메서드 시그니처**:
```java
/**
 * Bulkhead SPI.
 *
 * <p>동시 실행 수를 제한하여 특정 작업이 전체 시스템 리소스를 독점하지 못하도록 격리합니다.</p>
 *
 * <p><strong>Bulkhead 패턴:</strong></p>
 * <ul>
 *   <li>Semaphore-based: Semaphore로 동시 실행 수 제한</li>
 *   <li>Thread Pool-based: 전용 스레드 풀로 격리 (향후 확장)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface Bulkhead {

    /**
     * Bulkhead 진입 허용 여부 확인 (비블로킹).
     *
     * <p>현재 동시 실행 수가 제한 이하인지 확인합니다.
     * 허용되지 않으면 false를 반환하며, 대기하지 않습니다.</p>
     *
     * @param opId Operation ID (통계 및 로깅용)
     * @return true: 진입 허용, false: 동시 실행 제한 초과
     */
    boolean tryAcquire(OpId opId);

    /**
     * Bulkhead 진입 허용 여부 확인 (타임아웃 대기).
     *
     * <p>지정된 시간 동안 대기하여 Bulkhead 진입을 시도합니다.
     * 대기 시간 내에 허용되면 true, 타임아웃 시 false를 반환합니다.</p>
     *
     * @param opId Operation ID
     * @param timeoutMs 최대 대기 시간 (밀리초)
     * @return true: 진입 허용, false: 타임아웃 또는 인터럽트
     * @throws InterruptedException 대기 중 인터럽트 발생
     */
    boolean tryAcquire(OpId opId, long timeoutMs) throws InterruptedException;

    /**
     * Bulkhead 진입 해제.
     *
     * <p>작업 완료 후 Bulkhead를 해제하여 다른 작업이 진입할 수 있도록 합니다.
     * 반드시 try-finally 블록에서 호출되어야 합니다.</p>
     *
     * @param opId Operation ID
     */
    void release(OpId opId);

    /**
     * 현재 동시 실행 수 조회.
     *
     * @return 현재 진입 중인 작업 수
     */
    int getCurrentConcurrency();

    /**
     * Bulkhead 설정 정보 조회.
     *
     * @return Bulkhead 설정 (최대 동시 실행 수 등)
     */
    BulkheadConfig getConfig();
}

/**
 * Bulkhead 설정.
 */
public class BulkheadConfig {
    private int maxConcurrentCalls;  // 최대 동시 실행 수 (예: 10)
    private int maxWaitDurationMs;   // 최대 대기 시간 (기본값)
}
```

**의존성**: OR-2의 OpId

---

#### FR-5: HedgePolicy SPI 정의

**설명**: 헤징(Hedging) 재요청을 통한 지연 최소화

**헤징 개념**:
- P99 지연 시간이 100ms인 API에 대해, 50ms 대기 후 추가 요청 발송
- 먼저 응답하는 쪽의 결과를 사용
- 지연 감소 효과 (Tail Latency 최적화)

**메서드 시그니처**:
```java
/**
 * Hedge Policy SPI.
 *
 * <p>외부 API 호출 시 일정 시간 후 추가 요청을 발송하여,
 * 먼저 응답하는 쪽의 결과를 사용함으로써 지연 시간을 최소화합니다.</p>
 *
 * <p><strong>Hedging 전략:</strong></p>
 * <pre>
 * 1. 첫 번째 요청 발송 (t=0ms)
 * 2. hedgeDelayMs 대기 (예: 50ms)
 * 3. 첫 번째 요청 미완료 시 두 번째 요청 발송 (t=50ms)
 * 4. 먼저 응답하는 쪽 결과 사용
 * 5. 나머지 요청 취소
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface HedgePolicy {

    /**
     * Hedging 적용 여부 확인.
     *
     * <p>특정 Operation에 대해 Hedging을 적용할지 여부를 결정합니다.</p>
     *
     * @param opId Operation ID
     * @return true: Hedging 적용, false: 적용 안 함
     */
    boolean shouldHedge(OpId opId);

    /**
     * Hedge 요청 발송까지 대기 시간 조회.
     *
     * <p>첫 번째 요청 발송 후 몇 ms 후에 Hedge 요청을 발송할지 결정합니다.</p>
     *
     * @param opId Operation ID
     * @return Hedge 요청 발송 대기 시간 (밀리초)
     */
    long getHedgeDelayMs(OpId opId);

    /**
     * 최대 Hedge 요청 수 조회.
     *
     * <p>원본 요청 외에 추가로 발송할 Hedge 요청의 최대 수를 결정합니다.
     * 일반적으로 1-2개가 적절합니다.</p>
     *
     * @param opId Operation ID
     * @return 최대 Hedge 요청 수 (기본: 1)
     */
    int getMaxHedges(OpId opId);

    /**
     * Hedge 요청 발송 기록.
     *
     * <p>Hedge 요청이 발송되었음을 기록하여 통계 및 모니터링에 활용합니다.</p>
     *
     * @param opId Operation ID
     * @param hedgeNumber Hedge 요청 번호 (1부터 시작)
     */
    void recordHedgeAttempt(OpId opId, int hedgeNumber);

    /**
     * Hedge 성공 기록 (어느 요청이 성공했는지).
     *
     * <p>원본 또는 Hedge 요청 중 어느 것이 먼저 성공했는지 기록합니다.</p>
     *
     * @param opId Operation ID
     * @param wasHedge true: Hedge 요청 성공, false: 원본 요청 성공
     */
    void recordSuccess(OpId opId, boolean wasHedge);
}
```

**설정 파라미터 (참고용)**:
```java
public class HedgePolicyConfig {
    private boolean enabled = false;     // Hedging 활성화 여부
    private long hedgeDelayMs = 50;      // Hedge 요청 발송 대기 시간
    private int maxHedges = 1;           // 최대 Hedge 요청 수
}
```

**의존성**: OR-2의 OpId

---

#### FR-6: NoOp 구현 제공

**설명**: 모든 Protection SPI의 NoOp (No Operation) 기본 구현 제공

**NoOp 동작**:
- 모든 메서드가 아무 동작도 하지 않음
- `tryAcquire()` 계열 메서드는 항상 `true` 반환 (항상 허용)
- `record*()` 계열 메서드는 빈 구현 (통계 기록 안 함)
- 프로덕션 전 개발/테스트 환경에서 보호 없이 빠른 실행

**NoOp 구현 예시**:
```java
/**
 * Circuit Breaker NoOp 구현.
 *
 * <p>모든 요청을 항상 허용하며, 상태 추적을 하지 않습니다.
 * 개발 및 테스트 환경에서 사용하거나, 보호 없이 실행하고자 할 때 사용합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpCircuitBreaker implements CircuitBreaker {

    @Override
    public boolean tryAcquire(OpId opId) {
        return true; // 항상 허용
    }

    @Override
    public void recordSuccess(OpId opId) {
        // NoOp
    }

    @Override
    public void recordFailure(OpId opId, Throwable throwable) {
        // NoOp
    }

    @Override
    public CircuitBreakerState getState() {
        return CircuitBreakerState.CLOSED; // 항상 CLOSED
    }

    @Override
    public void reset() {
        // NoOp
    }
}
```

**NoOp 클래스 목록**:
- `NoOpCircuitBreaker`
- `NoOpTimeoutPolicy`
- `NoOpRateLimiter`
- `NoOpBulkhead`
- `NoOpHedgePolicy`

**위치**: `orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/protection/noop/`

**의존성**: 각 Protection SPI 인터페이스

---

#### FR-7: Protection Hook 체인 순서 정의

**설명**: Protection Hook의 실행 순서 및 동작 방식 명세

**체인 순서** (Executor 진입 전):
```
1. TimeoutPolicy   → 타임아웃 시작 (타이머 등록)
2. CircuitBreaker  → tryAcquire() 호출 (OPEN 시 즉시 실패)
3. Bulkhead        → tryAcquire() 호출 (동시 실행 제한)
4. RateLimiter     → tryAcquire() 호출 (QPS 제한)
5. Executor        → 실제 작업 실행
6. HedgePolicy     → (Executor 내부에서 적용, 병렬 요청 발송)
```

**체인 동작 방식**:
```java
// 의사 코드 (Executor 내부)
public Outcome execute(Envelope envelope) {
    OpId opId = envelope.opId();

    // 1. Timeout 타이머 시작
    long startTime = System.currentTimeMillis();
    long timeout = timeoutPolicy.getPerAttemptTimeoutMs(opId);

    try {
        // 2. Circuit Breaker 체크
        if (!circuitBreaker.tryAcquire(opId)) {
            circuitBreaker.recordFailure(opId, new CircuitBreakerOpenException());
            return new Fail("CB-OPEN", "Circuit Breaker is OPEN");
        }

        // 3. Bulkhead 진입
        if (!bulkhead.tryAcquire(opId, timeout)) {
            return new Fail("BULKHEAD-FULL", "Bulkhead is full");
        }

        try {
            // 4. Rate Limiter 체크
            if (!rateLimiter.tryAcquire(opId, timeout)) {
                return new Fail("RATE-LIMIT", "Rate limit exceeded");
            }

            // 5. 실제 작업 실행 (Hedge 적용)
            Outcome outcome = executeWithHedge(envelope);

            // 6. 성공 기록
            circuitBreaker.recordSuccess(opId);
            return outcome;

        } finally {
            // 7. Bulkhead 해제
            bulkhead.release(opId);
        }

    } catch (TimeoutException e) {
        timeoutPolicy.recordTimeout(opId, System.currentTimeMillis() - startTime);
        circuitBreaker.recordFailure(opId, e);
        return new Retry("Timeout", 1, 1000);

    } catch (Exception e) {
        circuitBreaker.recordFailure(opId, e);
        return new Fail("ERROR", e.getMessage());
    }
}
```

**순서 선정 이유**:
1. **Timeout First**: 전체 작업의 상한선 설정
2. **Circuit Breaker**: 빠른 실패로 불필요한 리소스 소비 방지
3. **Bulkhead**: 리소스 진입 제어 (Semaphore)
4. **Rate Limiter**: QPS 제어 (마지막 체크포인트)
5. **Executor**: 실제 작업 실행
6. **Hedge**: Executor 내부에서 병렬 요청 관리

---

### 2.2 비기능 요구사항

#### NFR-1: 확장성

**목표**: 사용자가 각 Protection SPI를 구체 구현으로 교체 가능

**확장 방법**:
```java
// 1. Resilience4j CircuitBreaker 어댑터 구현
public class Resilience4jCircuitBreakerAdapter implements CircuitBreaker {
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker delegate;

    // Resilience4j CircuitBreaker를 위임
}

// 2. Spring 설정으로 Bean 등록
@Configuration
public class ProtectionConfig {
    @Bean
    public CircuitBreaker circuitBreaker() {
        return new Resilience4jCircuitBreakerAdapter(...);
    }
}
```

**어댑터 패턴 활용**:
- Resilience4j
- Netflix Hystrix (deprecated, 참고용)
- Alibaba Sentinel
- 커스텀 구현

#### NFR-2: 성능

**목표**: Protection Hook 오버헤드 최소화

**성능 요구사항**:
- `tryAcquire()` 호출 시간: < 1ms (P99)
- `record*()` 호출 시간: < 0.5ms (P99)
- 메모리 오버헤드: < 10MB (10,000개 OpId 추적 시)

**NoOp 구현 성능**:
- `tryAcquire()`: < 0.01ms (즉시 반환)
- `record*()`: < 0.01ms (빈 메서드)

#### NFR-3: 관찰 가능성

**메트릭** (구현체가 제공):
- `orchestrator.protection.circuit_breaker.state`: CLOSED/OPEN/HALF_OPEN 상태
- `orchestrator.protection.circuit_breaker.calls.total`: 총 호출 수
- `orchestrator.protection.circuit_breaker.calls.failed`: 실패 호출 수
- `orchestrator.protection.timeout.count`: 타임아웃 발생 수
- `orchestrator.protection.rate_limiter.rejected`: Rate Limit 거부 수
- `orchestrator.protection.bulkhead.concurrency`: 현재 동시 실행 수
- `orchestrator.protection.hedge.attempts`: Hedge 시도 수

**로깅**:
- INFO: Protection Hook 활성화 정보
- WARN: Circuit Breaker OPEN 전이, Rate Limit 거부
- ERROR: Protection 예외 발생

#### NFR-4: 테스트 용이성

**NoOp 구현 활용**:
- 개발 환경: NoOp으로 보호 없이 빠른 테스트
- 통합 테스트: NoOp으로 Protection 영향 제거
- 프로덕션: 실제 구현으로 교체

**테스트 더블**:
- Mock: Mockito로 Protection SPI Mock
- Spy: 실제 Protection 동작 추적

---

### 2.3 제약사항

#### 아키텍처 제약

**헥사고날 아키텍처 준수**:
- Protection SPI는 **도메인 레이어** 인터페이스 (core 모듈)
- NoOp 구현은 **도메인 레이어** 구현 (core 모듈)
- 실제 구현 (Resilience4j 등)은 **인프라 레이어** (adapter-protection 모듈)

**의존성 방향**:
```
adapter-protection (Resilience4j 어댑터)
  ↓ implements
core/protection (Protection SPI 인터페이스)
  ↑ uses
domain/executor (Executor가 Protection Hook 사용)
```

#### 기술 스택 제약

**Java 21**:
- Interface default method 활용 가능
- Sealed interface 고려 (향후)

**Lombok 금지**:
- NoOp 구현에서 Pure Java 사용
- Getter/Setter 직접 작성

**Zero External Dependencies** (Core 모듈):
- Protection SPI는 순수 Java 21로만 정의
- 외부 라이브러리 의존 없음

---

## 3. Technical Specifications

### 3.1 Protection SPI 인터페이스 설계

**패키지 구조**:
```
orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/protection/
├── CircuitBreaker.java
├── CircuitBreakerState.java
├── TimeoutPolicy.java
├── RateLimiter.java
├── RateLimiterConfig.java
├── Bulkhead.java
├── BulkheadConfig.java
├── HedgePolicy.java
├── noop/
│   ├── NoOpCircuitBreaker.java
│   ├── NoOpTimeoutPolicy.java
│   ├── NoOpRateLimiter.java
│   ├── NoOpBulkhead.java
│   └── NoOpHedgePolicy.java
└── package-info.java
```

### 3.2 NoOp 구현 상세

#### NoOpCircuitBreaker
```java
package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.CircuitBreaker;
import com.ryuqq.orchestrator.core.protection.CircuitBreakerState;

/**
 * Circuit Breaker NoOp 구현.
 *
 * <p>모든 요청을 항상 허용하며, 상태 추적을 하지 않습니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpCircuitBreaker implements CircuitBreaker {

    @Override
    public boolean tryAcquire(OpId opId) {
        return true;
    }

    @Override
    public void recordSuccess(OpId opId) {
        // NoOp
    }

    @Override
    public void recordFailure(OpId opId, Throwable throwable) {
        // NoOp
    }

    @Override
    public CircuitBreakerState getState() {
        return CircuitBreakerState.CLOSED;
    }

    @Override
    public void reset() {
        // NoOp
    }
}
```

#### NoOpTimeoutPolicy
```java
package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.TimeoutPolicy;

/**
 * Timeout Policy NoOp 구현.
 *
 * <p>타임아웃을 적용하지 않습니다 (0 반환).</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpTimeoutPolicy implements TimeoutPolicy {

    @Override
    public long getPerAttemptTimeoutMs(OpId opId) {
        return 0; // 타임아웃 없음
    }

    @Override
    public void recordTimeout(OpId opId, long elapsedMs) {
        // NoOp
    }
}
```

#### NoOpRateLimiter
```java
package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.RateLimiter;
import com.ryuqq.orchestrator.core.protection.RateLimiterConfig;

/**
 * Rate Limiter NoOp 구현.
 *
 * <p>모든 요청을 항상 허용합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpRateLimiter implements RateLimiter {

    @Override
    public boolean tryAcquire(OpId opId) {
        return true;
    }

    @Override
    public boolean tryAcquire(OpId opId, long timeoutMs) {
        return true;
    }

    @Override
    public RateLimiterConfig getConfig() {
        return new RateLimiterConfig(Double.MAX_VALUE, Integer.MAX_VALUE);
    }
}
```

#### NoOpBulkhead
```java
package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.Bulkhead;
import com.ryuqq.orchestrator.core.protection.BulkheadConfig;

/**
 * Bulkhead NoOp 구현.
 *
 * <p>동시 실행 수 제한을 적용하지 않습니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpBulkhead implements Bulkhead {

    @Override
    public boolean tryAcquire(OpId opId) {
        return true;
    }

    @Override
    public boolean tryAcquire(OpId opId, long timeoutMs) {
        return true;
    }

    @Override
    public void release(OpId opId) {
        // NoOp
    }

    @Override
    public int getCurrentConcurrency() {
        return 0; // 항상 0
    }

    @Override
    public BulkheadConfig getConfig() {
        return new BulkheadConfig(Integer.MAX_VALUE, 0);
    }
}
```

#### NoOpHedgePolicy
```java
package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.HedgePolicy;

/**
 * Hedge Policy NoOp 구현.
 *
 * <p>Hedging을 적용하지 않습니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpHedgePolicy implements HedgePolicy {

    @Override
    public boolean shouldHedge(OpId opId) {
        return false; // Hedging 비활성화
    }

    @Override
    public long getHedgeDelayMs(OpId opId) {
        return 0;
    }

    @Override
    public int getMaxHedges(OpId opId) {
        return 0;
    }

    @Override
    public void recordHedgeAttempt(OpId opId, int hedgeNumber) {
        // NoOp
    }

    @Override
    public void recordSuccess(OpId opId, boolean wasHedge) {
        // NoOp
    }
}
```

---

## 4. Implementation Plan

### 4.1 작업 분해

#### Task 1: 브랜치 생성 및 체크아웃

**목표**: 작업 브랜치 생성

```bash
git checkout -b feature/OR-5-protection-spi-interface
```

**상태**: 대기

#### Task 2: Protection SPI 패키지 구조 생성

**목표**: 패키지 디렉토리 생성

**작업 내용**:
- `orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/protection/` 디렉토리 생성
- `orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/protection/noop/` 디렉토리 생성

**검증**: 디렉토리 존재 확인

#### Task 3: CircuitBreaker SPI 인터페이스 정의

**목표**: `CircuitBreaker.java` 인터페이스 작성

**작업 내용**:
- 메서드 시그니처 정의 (`tryAcquire`, `recordSuccess`, `recordFailure`, `getState`, `reset`)
- Javadoc 작성
- `CircuitBreakerState.java` Enum 정의

**위치**: `orchestrator-core/src/main/java/.../protection/CircuitBreaker.java`

**검증**: 컴파일 성공, Javadoc 생성 확인

#### Task 4: TimeoutPolicy SPI 인터페이스 정의

**목표**: `TimeoutPolicy.java` 인터페이스 작성

**작업 내용**:
- 메서드 시그니처 정의 (`getPerAttemptTimeoutMs`, `recordTimeout`)
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/TimeoutPolicy.java`

**검증**: 컴파일 성공

#### Task 5: RateLimiter SPI 인터페이스 정의

**목표**: `RateLimiter.java` 인터페이스 작성

**작업 내용**:
- 메서드 시그니처 정의 (`tryAcquire`, `tryAcquire(timeout)`, `getConfig`)
- `RateLimiterConfig.java` 클래스 작성
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/RateLimiter.java`

**검증**: 컴파일 성공

#### Task 6: Bulkhead SPI 인터페이스 정의

**목표**: `Bulkhead.java` 인터페이스 작성

**작업 내용**:
- 메서드 시그니처 정의 (`tryAcquire`, `tryAcquire(timeout)`, `release`, `getCurrentConcurrency`, `getConfig`)
- `BulkheadConfig.java` 클래스 작성
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/Bulkhead.java`

**검증**: 컴파일 성공

#### Task 7: HedgePolicy SPI 인터페이스 정의

**목표**: `HedgePolicy.java` 인터페이스 작성

**작업 내용**:
- 메서드 시그니처 정의 (`shouldHedge`, `getHedgeDelayMs`, `getMaxHedges`, `recordHedgeAttempt`, `recordSuccess`)
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/HedgePolicy.java`

**검증**: 컴파일 성공

#### Task 8: NoOpCircuitBreaker 구현

**목표**: `NoOpCircuitBreaker.java` 클래스 작성

**작업 내용**:
- CircuitBreaker 인터페이스 구현
- 모든 메서드를 NoOp으로 구현
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/noop/NoOpCircuitBreaker.java`

**검증**: 유닛 테스트 작성

#### Task 9: NoOpTimeoutPolicy 구현

**목표**: `NoOpTimeoutPolicy.java` 클래스 작성

**작업 내용**:
- TimeoutPolicy 인터페이스 구현
- `getPerAttemptTimeoutMs()` → 0 반환
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/noop/NoOpTimeoutPolicy.java`

**검증**: 유닛 테스트 작성

#### Task 10: NoOpRateLimiter 구현

**목표**: `NoOpRateLimiter.java` 클래스 작성

**작업 내용**:
- RateLimiter 인터페이스 구현
- `tryAcquire()` → true 반환
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/noop/NoOpRateLimiter.java`

**검증**: 유닛 테스트 작성

#### Task 11: NoOpBulkhead 구현

**목표**: `NoOpBulkhead.java` 클래스 작성

**작업 내용**:
- Bulkhead 인터페이스 구현
- `tryAcquire()` → true 반환
- `release()` → NoOp
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/noop/NoOpBulkhead.java`

**검증**: 유닛 테스트 작성

#### Task 12: NoOpHedgePolicy 구현

**목표**: `NoOpHedgePolicy.java` 클래스 작성

**작업 내용**:
- HedgePolicy 인터페이스 구현
- `shouldHedge()` → false 반환
- Javadoc 작성

**위치**: `orchestrator-core/src/main/java/.../protection/noop/NoOpHedgePolicy.java`

**검증**: 유닛 테스트 작성

#### Task 13: package-info.java 작성

**목표**: Protection 패키지 설명 문서 작성

**작업 내용**:
- Protection SPI 개요 설명
- 체인 순서 및 동작 방식 설명
- NoOp 구현 사용법 설명

**위치**: `orchestrator-core/src/main/java/.../protection/package-info.java`

**검증**: Javadoc 생성 확인

#### Task 14: 유닛 테스트 - NoOp 구현 동작 검증

**목표**: 모든 NoOp 구현의 동작 검증

**테스트 시나리오**:
- NoOpCircuitBreaker: `tryAcquire()` 항상 true 반환
- NoOpTimeoutPolicy: `getPerAttemptTimeoutMs()` 0 반환
- NoOpRateLimiter: `tryAcquire()` 항상 true 반환
- NoOpBulkhead: `tryAcquire()` 항상 true, `release()` 정상 동작
- NoOpHedgePolicy: `shouldHedge()` false 반환

**검증**: 모든 NoOp 테스트 통과

#### Task 15: Javadoc 작성

**목표**: 모든 공개 API Javadoc 작성

**대상**:
- CircuitBreaker 인터페이스
- TimeoutPolicy 인터페이스
- RateLimiter 인터페이스
- Bulkhead 인터페이스
- HedgePolicy 인터페이스
- 모든 NoOp 구현 클래스

**포함 내용**:
- `@author Orchestrator Team`
- `@since 1.0.0`
- `@param`, `@return`, `@throws` 상세 설명

**검증**: `mvn javadoc:javadoc` 성공

#### Task 16: 테스트 커버리지 ≥ 80% 확인

**목표**: JaCoCo 커버리지 확인

**대상**:
- 모든 NoOp 구현 클래스

**커버리지 타겟**:
- Line Coverage: ≥ 80%
- Branch Coverage: ≥ 70%

**검증**: `mvn test jacoco:report`

#### Task 17: 코드 리뷰 준비

**목표**: 리뷰 가능한 상태로 정리

**체크리스트**:
- [ ] 모든 테스트 통과
- [ ] Javadoc 완성
- [ ] Lombok 미사용 확인
- [ ] Law of Demeter 준수
- [ ] 커밋 메시지 정리

#### Task 18: PR 생성

**목표**: Epic OR-1 리뷰 요청

**PR 템플릿**:
- **제목**: `[OR-5] Protection SPI 인터페이스 정의 및 NoOp 구현`
- **설명**:
  - 구현 내용 요약
  - Acceptance Criteria 충족 여부
  - 테스트 커버리지 리포트
- **리뷰어**: Epic OR-1 담당자

### 4.2 작업 일정 (예상)

| Task | 예상 시간 | 의존성 |
|------|-----------|--------|
| Task 1 (브랜치) | 10분 | - |
| Task 2 (패키지 구조) | 20분 | Task 1 |
| Task 3 (CircuitBreaker IF) | 2시간 | Task 2 |
| Task 4 (TimeoutPolicy IF) | 1시간 | Task 2 |
| Task 5 (RateLimiter IF) | 1.5시간 | Task 2 |
| Task 6 (Bulkhead IF) | 1.5시간 | Task 2 |
| Task 7 (HedgePolicy IF) | 1.5시간 | Task 2 |
| Task 8 (NoOpCB) | 1시간 | Task 3 |
| Task 9 (NoOpTimeout) | 30분 | Task 4 |
| Task 10 (NoOpRL) | 45분 | Task 5 |
| Task 11 (NoOpBH) | 45분 | Task 6 |
| Task 12 (NoOpHedge) | 45분 | Task 7 |
| Task 13 (package-info) | 1시간 | Task 3-7 |
| Task 14 (유닛 테스트) | 3시간 | Task 8-12 |
| Task 15 (Javadoc) | 2시간 | Task 14 |
| Task 16 (커버리지) | 1시간 | Task 15 |
| Task 17-18 (리뷰/PR) | 2시간 | Task 16 |
| **총합** | **21시간** | |

---

## 5. Testing Strategy

### 5.1 유닛 테스트

#### NoOpCircuitBreakerTest
```java
@Test
void tryAcquire_항상_true_반환() {
    // given
    CircuitBreaker cb = new NoOpCircuitBreaker();
    OpId opId = OpId.of("test-op");

    // when
    boolean result = cb.tryAcquire(opId);

    // then
    assertTrue(result);
}

@Test
void getState_항상_CLOSED_반환() {
    // given
    CircuitBreaker cb = new NoOpCircuitBreaker();

    // when
    CircuitBreakerState state = cb.getState();

    // then
    assertEquals(CircuitBreakerState.CLOSED, state);
}

@Test
void recordSuccess_예외_없이_실행() {
    // given
    CircuitBreaker cb = new NoOpCircuitBreaker();
    OpId opId = OpId.of("test-op");

    // when & then
    assertDoesNotThrow(() -> cb.recordSuccess(opId));
}

@Test
void recordFailure_예외_없이_실행() {
    // given
    CircuitBreaker cb = new NoOpCircuitBreaker();
    OpId opId = OpId.of("test-op");
    Throwable error = new RuntimeException("test error");

    // when & then
    assertDoesNotThrow(() -> cb.recordFailure(opId, error));
}
```

#### NoOpTimeoutPolicyTest
```java
@Test
void getPerAttemptTimeoutMs_항상_0_반환() {
    // given
    TimeoutPolicy policy = new NoOpTimeoutPolicy();
    OpId opId = OpId.of("test-op");

    // when
    long timeout = policy.getPerAttemptTimeoutMs(opId);

    // then
    assertEquals(0, timeout);
}

@Test
void recordTimeout_예외_없이_실행() {
    // given
    TimeoutPolicy policy = new NoOpTimeoutPolicy();
    OpId opId = OpId.of("test-op");

    // when & then
    assertDoesNotThrow(() -> policy.recordTimeout(opId, 1000));
}
```

#### NoOpRateLimiterTest
```java
@Test
void tryAcquire_항상_true_반환() {
    // given
    RateLimiter limiter = new NoOpRateLimiter();
    OpId opId = OpId.of("test-op");

    // when
    boolean result = limiter.tryAcquire(opId);

    // then
    assertTrue(result);
}

@Test
void tryAcquireWithTimeout_항상_true_반환() throws InterruptedException {
    // given
    RateLimiter limiter = new NoOpRateLimiter();
    OpId opId = OpId.of("test-op");

    // when
    boolean result = limiter.tryAcquire(opId, 1000);

    // then
    assertTrue(result);
}
```

#### NoOpBulkheadTest
```java
@Test
void tryAcquire_항상_true_반환() {
    // given
    Bulkhead bulkhead = new NoOpBulkhead();
    OpId opId = OpId.of("test-op");

    // when
    boolean result = bulkhead.tryAcquire(opId);

    // then
    assertTrue(result);
}

@Test
void release_예외_없이_실행() {
    // given
    Bulkhead bulkhead = new NoOpBulkhead();
    OpId opId = OpId.of("test-op");

    // when & then
    assertDoesNotThrow(() -> bulkhead.release(opId));
}

@Test
void getCurrentConcurrency_항상_0_반환() {
    // given
    Bulkhead bulkhead = new NoOpBulkhead();

    // when
    int concurrency = bulkhead.getCurrentConcurrency();

    // then
    assertEquals(0, concurrency);
}
```

#### NoOpHedgePolicyTest
```java
@Test
void shouldHedge_항상_false_반환() {
    // given
    HedgePolicy policy = new NoOpHedgePolicy();
    OpId opId = OpId.of("test-op");

    // when
    boolean result = policy.shouldHedge(opId);

    // then
    assertFalse(result);
}

@Test
void getHedgeDelayMs_0_반환() {
    // given
    HedgePolicy policy = new NoOpHedgePolicy();
    OpId opId = OpId.of("test-op");

    // when
    long delay = policy.getHedgeDelayMs(opId);

    // then
    assertEquals(0, delay);
}

@Test
void recordHedgeAttempt_예외_없이_실행() {
    // given
    HedgePolicy policy = new NoOpHedgePolicy();
    OpId opId = OpId.of("test-op");

    // when & then
    assertDoesNotThrow(() -> policy.recordHedgeAttempt(opId, 1));
}
```

---

## 6. Acceptance Criteria (상세)

### AC-1: CircuitBreaker SPI 정의 및 NoOp 구현 완료

**조건**:
- CircuitBreaker 인터페이스 정의됨
- NoOpCircuitBreaker 구현됨
- Javadoc 작성 완료

**검증 방법**:
```java
@Test
void AC1_CircuitBreaker_SPI_정의_완료() {
    CircuitBreaker cb = new NoOpCircuitBreaker();
    OpId opId = OpId.of("test");

    assertTrue(cb.tryAcquire(opId));
    assertEquals(CircuitBreakerState.CLOSED, cb.getState());
    assertDoesNotThrow(() -> cb.recordSuccess(opId));
    assertDoesNotThrow(() -> cb.recordFailure(opId, new RuntimeException()));
    assertDoesNotThrow(() -> cb.reset());
}
```

**성공 기준**:
- 모든 메서드가 NoOp으로 정상 동작
- Javadoc 생성 성공

### AC-2: TimeoutPolicy SPI 정의 및 NoOp 구현 완료

**조건**:
- TimeoutPolicy 인터페이스 정의됨
- NoOpTimeoutPolicy 구현됨
- Javadoc 작성 완료

**검증 방법**:
```java
@Test
void AC2_TimeoutPolicy_SPI_정의_완료() {
    TimeoutPolicy policy = new NoOpTimeoutPolicy();
    OpId opId = OpId.of("test");

    assertEquals(0, policy.getPerAttemptTimeoutMs(opId));
    assertDoesNotThrow(() -> policy.recordTimeout(opId, 1000));
}
```

**성공 기준**:
- getPerAttemptTimeoutMs() 0 반환
- recordTimeout() NoOp 정상 동작

### AC-3: RateLimiter SPI 정의 및 NoOp 구현 완료

**조건**:
- RateLimiter 인터페이스 정의됨
- NoOpRateLimiter 구현됨
- RateLimiterConfig 클래스 작성됨
- Javadoc 작성 완료

**검증 방법**:
```java
@Test
void AC3_RateLimiter_SPI_정의_완료() throws InterruptedException {
    RateLimiter limiter = new NoOpRateLimiter();
    OpId opId = OpId.of("test");

    assertTrue(limiter.tryAcquire(opId));
    assertTrue(limiter.tryAcquire(opId, 1000));
    assertNotNull(limiter.getConfig());
}
```

**성공 기준**:
- tryAcquire() 항상 true 반환
- getConfig() 정상 반환

### AC-4: Bulkhead SPI 정의 및 NoOp 구현 완료

**조건**:
- Bulkhead 인터페이스 정의됨
- NoOpBulkhead 구현됨
- BulkheadConfig 클래스 작성됨
- Javadoc 작성 완료

**검증 방법**:
```java
@Test
void AC4_Bulkhead_SPI_정의_완료() throws InterruptedException {
    Bulkhead bulkhead = new NoOpBulkhead();
    OpId opId = OpId.of("test");

    assertTrue(bulkhead.tryAcquire(opId));
    assertTrue(bulkhead.tryAcquire(opId, 1000));
    assertDoesNotThrow(() -> bulkhead.release(opId));
    assertEquals(0, bulkhead.getCurrentConcurrency());
    assertNotNull(bulkhead.getConfig());
}
```

**성공 기준**:
- tryAcquire() 항상 true 반환
- release() NoOp 정상 동작
- getCurrentConcurrency() 0 반환

### AC-5: HedgePolicy SPI 정의 및 NoOp 구현 완료

**조건**:
- HedgePolicy 인터페이스 정의됨
- NoOpHedgePolicy 구현됨
- Javadoc 작성 완료

**검증 방법**:
```java
@Test
void AC5_HedgePolicy_SPI_정의_완료() {
    HedgePolicy policy = new NoOpHedgePolicy();
    OpId opId = OpId.of("test");

    assertFalse(policy.shouldHedge(opId));
    assertEquals(0, policy.getHedgeDelayMs(opId));
    assertEquals(0, policy.getMaxHedges(opId));
    assertDoesNotThrow(() -> policy.recordHedgeAttempt(opId, 1));
    assertDoesNotThrow(() -> policy.recordSuccess(opId, false));
}
```

**성공 기준**:
- shouldHedge() false 반환
- 모든 메서드 NoOp 정상 동작

---

## 7. Dependencies

### 7.1 OR-2 의존성

**타입 모델**:
- `OpId`: Protection Hook에서 Operation 식별용

**OR-5가 OR-2에 의존하는 이유**:
- 모든 Protection SPI 메서드가 OpId 파라미터 사용
- OpId를 통한 Operation별 보호 정책 적용

### 7.2 Epic OR-1 작업 간 관계

**OR-5 → OR-3 (Inline Fast-Path Runner)**:
- OR-3의 Executor가 Protection Hook 체인 실행
- Protection Hook 실패 시 빠른 실패 응답

**OR-5 → OR-4 (Queue Worker Runner)**:
- OR-4의 Executor가 Protection Hook 체인 실행
- 큐 메시지 처리 전 보호 메커니즘 적용

### 7.3 외부 의존성

**Zero External Dependencies** (Core 모듈):
- 순수 Java 21로만 구현
- 외부 라이브러리 의존 없음

**향후 어댑터 구현 (adapter-protection 모듈)**:
- Resilience4j
- Alibaba Sentinel
- 커스텀 구현

---

## 8. Risks & Mitigation

### 8.1 인터페이스 설계 변경 리스크

**리스크**:
- Protection SPI 인터페이스 확정 후 변경이 어려움
- 실제 사용 시 메서드 부족 또는 불필요한 메서드 발견

**영향**:
- 하위 호환성 깨짐
- 어댑터 구현 전면 수정 필요

**완화 전략**:
1. **Resilience4j 분석**: 검증된 라이브러리의 인터페이스 참고
2. **프로토타입 구현**: NoOp이 아닌 간단한 실제 구현으로 검증
3. **리뷰 과정**: 아키텍처 리뷰에서 인터페이스 설계 검증
4. **Default Method**: 향후 확장 시 default method로 하위 호환성 유지

### 8.2 Protection Hook 성능 리스크

**리스크**:
- Protection Hook 체인이 요청 처리 지연 증가
- 각 Hook마다 오버헤드 발생

**영향**:
- Fast-Path 200ms 목표 달성 실패
- 전체 처리량 저하

**완화 전략**:
1. **NoOp 기본 제공**: 개발/테스트 환경에서 오버헤드 제거
2. **성능 벤치마크**: Protection Hook 오버헤드 측정 (< 1ms 목표)
3. **선택적 적용**: 필요한 Hook만 활성화
4. **비동기 처리**: 통계 기록은 비동기로 처리

### 8.3 Protection 체인 순서 오류 리스크

**리스크**:
- 잘못된 체인 순서로 인한 비효율 또는 오작동
- 예: Rate Limiter를 Circuit Breaker보다 먼저 실행 시 불필요한 리소스 소비

**영향**:
- 보호 메커니즘 효과 저하
- 리소스 낭비

**완화 전략**:
1. **명확한 순서 문서화**: PRD 및 Javadoc에 순서 명시
2. **Executor 구현 검증**: Executor 구현 시 순서 준수 확인
3. **통합 테스트**: 전체 체인 동작 시나리오 테스트

---

## 9. Definition of Done

### 9.1 기능 완성도

- [ ] Task 1-13: Protection SPI 인터페이스 및 NoOp 구현 완료
  - CircuitBreaker 인터페이스 및 NoOp
  - TimeoutPolicy 인터페이스 및 NoOp
  - RateLimiter 인터페이스 및 NoOp
  - Bulkhead 인터페이스 및 NoOp
  - HedgePolicy 인터페이스 및 NoOp

### 9.2 테스트 완성도

- [ ] Task 14: 유닛 테스트 통과 (AC 1-5)
- [ ] 테스트 커버리지 ≥ 80% (JaCoCo)

### 9.3 문서화

- [ ] Task 15: Javadoc 작성 완료
  - 모든 public 인터페이스/클래스
  - `@author`, `@since` 태그 포함
- [ ] package-info.java 작성

### 9.4 코드 품질

- [ ] Checkstyle 통과
- [ ] Lombok 미사용 확인
- [ ] Law of Demeter 준수
- [ ] ArchUnit 레이어 의존성 검증 통과

### 9.5 Epic OR-1 DoD

- [ ] 모든 공개 API Javadoc 작성
- [ ] 유닛 테스트 커버리지 ≥ 80%
- [ ] ArchUnit 헥사고날 의존성 규칙 검증

### 9.6 PR 승인

- [ ] Task 17: 코드 리뷰 완료
- [ ] Task 18: PR 머지
- [ ] CI/CD 파이프라인 통과

---

## 10. 참고 자료

### 10.1 관련 문서

- **Epic OR-1**: Epic A: Core API 및 계약 구현
- **OR-2 PRD**: 타입 모델 및 상태머신 구현
- **OR-3 PRD**: Inline Fast-Path Runner 구현
- **OR-4 PRD**: Queue Worker 및 Finalizer/Reaper 러너 구현
- **헥사고날 아키텍처 가이드**: `docs/coding_convention/00-architecture/`

### 10.2 Protection 패턴

- **Resilience4j Documentation**: https://resilience4j.readme.io/
  - Circuit Breaker: https://resilience4j.readme.io/docs/circuitbreaker
  - Rate Limiter: https://resilience4j.readme.io/docs/ratelimiter
  - Bulkhead: https://resilience4j.readme.io/docs/bulkhead
  - TimeLimiter: https://resilience4j.readme.io/docs/timeout
  - Retry: https://resilience4j.readme.io/docs/retry

- **Alibaba Sentinel**: https://sentinelguard.io/
  - Flow Control: https://sentinelguard.io/en-us/docs/flow-control.html
  - Circuit Breaking: https://sentinelguard.io/en-us/docs/circuit-breaking.html

- **Hedging (Google SRE)**: https://sre.google/sre-book/addressing-cascading-failures/

### 10.3 보호 메커니즘 이론

- **Release It!** (Michael T. Nygard) - Chapter 5: Stability Patterns
  - Circuit Breaker
  - Bulkheads
  - Timeouts
  - Rate Limiting

- **The Art of Scalability** (Martin L. Abbott, Michael T. Fisher) - Chapter 12: Fault Isolation

---

## 11. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|------|------|----------|--------|
| 1.0.0 | 2025-10-18 | 최초 작성 | Orchestrator Team |

---

**문서 승인**: (Epic Owner 승인 필요)
**다음 단계**: Epic OR-1 완료 후 Epic OR-2 (Runner Executor 구현) 시작
