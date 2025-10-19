# 어댑터 구현 가이드

> 🎯 목표: Store, Bus, Protection 어댑터를 실제 기술 스택(DB, Message Queue, Circuit Breaker)으로 구현하기

이 가이드는 Orchestrator SDK의 SPI(Service Provider Interface)를 실제 인프라로 구현하는 방법을 설명합니다.

---

## 📚 목차

1. [Store 인터페이스 구현](#1-store-인터페이스-구현)
   - JPA + PostgreSQL 예시
   - 트랜잭션 처리
   - 멱등성 보장
2. [Bus 인터페이스 구현](#2-bus-인터페이스-구현)
   - AWS SQS 예시
   - 메시지 재시도 및 DLQ
3. [Protection 어댑터 구현](#3-protection-어댑터-구현)
   - Resilience4j 통합
   - Circuit Breaker, Timeout, RateLimiter

---

## 1. Store 인터페이스 구현

### 1.1 개요

`Store` 인터페이스는 다음을 제공해야 합니다:

- **Operation 상태 관리**: PENDING → IN_PROGRESS → COMPLETED/FAILED
- **Write-Ahead Log (WAL)**: Outcome을 먼저 영속화하여 장애 복구 가능
- **멱등성**: 동일한 OpId로 여러 번 호출 시 안전하게 처리
- **복구 메커니즘**: Finalizer/Reaper가 사용할 스캔 메서드

### 1.2 데이터 모델 (PostgreSQL 예시)

```sql
-- Operations 테이블
CREATE TABLE operations (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operations_state ON operations(state, id);
CREATE INDEX idx_operations_idem_key ON operations(idempotency_key);

-- Write-Ahead Log 테이블
CREATE TABLE write_ahead_log (
    id BIGSERIAL PRIMARY KEY,
    op_id UUID NOT NULL UNIQUE REFERENCES operations(id),
    outcome_type VARCHAR(20) NOT NULL CHECK (outcome_type IN ('OK', 'RETRY', 'FAIL')),
    provider_txn_id VARCHAR(255),
    result_payload JSONB,
    error_code INT,
    error_message TEXT,
    wal_state VARCHAR(20) NOT NULL CHECK (wal_state IN ('PENDING', 'COMPLETED')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wal_state ON write_ahead_log(wal_state, created_at);
CREATE INDEX idx_wal_op_id ON write_ahead_log(op_id);

-- Envelopes 테이블
CREATE TABLE envelopes (
    op_id UUID PRIMARY KEY REFERENCES operations(id),
    domain VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_envelopes_domain ON envelopes(domain, event_type);
```

### 1.3 JPA Entity 정의

```java
package com.example.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operations")
public class OperationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private OperationStateEnum state;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Getters, Setters, Constructors
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

enum OperationStateEnum {
    PENDING, IN_PROGRESS, COMPLETED, FAILED
}
```

### 1.4 Store 구현 - writeAhead()

**목적**: 외부 API 성공 직후 Outcome을 WAL에 내구화

```java
package com.example.adapter.persistence;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.*;
import com.ryuqq.orchestrator.core.spi.Store;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaStore implements Store {

    private final OperationRepository operationRepo;
    private final WriteAheadLogRepository walRepo;
    private final EnvelopeRepository envelopeRepo;

    public JpaStore(OperationRepository operationRepo,
                    WriteAheadLogRepository walRepo,
                    EnvelopeRepository envelopeRepo) {
        this.operationRepo = operationRepo;
        this.walRepo = walRepo;
        this.envelopeRepo = envelopeRepo;
    }

    @Override
    @Transactional
    public void writeAhead(OpId opId, Outcome outcome) {
        if (opId == null || outcome == null) {
            throw new IllegalArgumentException("opId and outcome cannot be null");
        }

        WriteAheadLogEntity wal = new WriteAheadLogEntity();
        wal.setOpId(opId.value());
        wal.setWalState(WriteAheadState.PENDING);

        // Outcome 타입에 따라 저장
        if (outcome instanceof Ok ok) {
            wal.setOutcomeType("OK");
            wal.setProviderTxnId(ok.providerTxnId());
            wal.setResultPayload(ok.result().json());
        } else if (outcome instanceof Retry retry) {
            wal.setOutcomeType("RETRY");
            wal.setErrorMessage(retry.reason());
        } else if (outcome instanceof Fail fail) {
            wal.setOutcomeType("FAIL");
            wal.setErrorCode(fail.code());
            wal.setErrorMessage(fail.reason());
        }

        // 멱등성: 기존 WAL이 있으면 업데이트, 없으면 INSERT
        // Note: 이 로직은 find와 save 사이에 race condition이 발생할 수 있습니다.
        // 프로덕션 코드에서는 DB의 UPSERT 기능을 사용하거나 DataIntegrityViolationException을
        // 처리하여 원자성을 보장하는 것이 좋습니다.
        WriteAheadLogEntity existing = walRepo.findByOpId(opId.value());
        if (existing != null) {
            existing.setOutcomeType(wal.getOutcomeType());
            existing.setProviderTxnId(wal.getProviderTxnId());
            existing.setResultPayload(wal.getResultPayload());
            existing.setErrorCode(wal.getErrorCode());
            existing.setErrorMessage(wal.getErrorMessage());
            walRepo.save(existing);
        } else {
            try {
                walRepo.save(wal);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // 동시성 환경에서 다른 스레드가 이미 삽입한 경우
                // 재조회 후 업데이트 시도
                existing = walRepo.findByOpId(opId.value());
                if (existing != null) {
                    existing.setOutcomeType(wal.getOutcomeType());
                    existing.setProviderTxnId(wal.getProviderTxnId());
                    existing.setResultPayload(wal.getResultPayload());
                    existing.setErrorCode(wal.getErrorCode());
                    existing.setErrorMessage(wal.getErrorMessage());
                    walRepo.save(existing);
                } else {
                    throw e; // 예상치 못한 경우, 재발생
                }
            }
        }
    }
}
```

**핵심 포인트**:
- `@Transactional`: WAL 저장이 원자적으로 수행됨
- **멱등성**: 동일 OpId로 여러 번 호출 시 UPDATE 처리
- **Outcome 타입 분기**: Ok, Retry, Fail에 따라 다른 컬럼에 저장

### 1.5 Store 구현 - finalize()

**목적**: Operation 상태를 COMPLETED/FAILED로 종결하고 WAL을 COMPLETED로 마킹

```java
@Override
@Transactional
public void finalize(OpId opId, OperationState state) {
    if (opId == null || state == null) {
        throw new IllegalArgumentException("opId and state cannot be null");
    }

    // 상태 검증: terminal state만 허용
    if (state != OperationState.COMPLETED && state != OperationState.FAILED) {
        throw new IllegalArgumentException("state must be COMPLETED or FAILED, but was: " + state);
    }

    // Operation 조회
    OperationEntity operation = operationRepo.findById(opId.value())
        .orElseThrow(() -> new IllegalStateException("Operation not found for opId: " + opId));

    // 이미 종결된 상태인지 확인
    if (operation.getState().isTerminal()) {
        throw new IllegalStateException(
            "Operation already finalized with state: " + operation.getState());
    }

    // 상태 업데이트 (Optimistic Locking으로 동시성 제어)
    operation.setState(toEntityState(state));
    operationRepo.save(operation);

    // WAL 상태를 COMPLETED로 변경
    WriteAheadLogEntity wal = walRepo.findByOpId(opId.value());
    if (wal != null) {
        wal.setWalState(WriteAheadState.COMPLETED);
        walRepo.save(wal);
    }
}

private OperationStateEnum toEntityState(OperationState state) {
    return switch (state) {
        case PENDING -> OperationStateEnum.PENDING;
        case IN_PROGRESS -> OperationStateEnum.IN_PROGRESS;
        case COMPLETED -> OperationStateEnum.COMPLETED;
        case FAILED -> OperationStateEnum.FAILED;
    };
}
```

**핵심 포인트**:
- **Optimistic Locking**: `@Version` 필드로 동시성 제어
- **상태 전이 검증**: terminal state만 허용
- **원자적 업데이트**: Operation + WAL을 동일 트랜잭션 내 처리

### 1.6 Store 구현 - scanWA()

**목적**: Finalizer가 미처리 WAL을 스캔하여 복구

```java
@Override
@Transactional(readOnly = true)
public List<OpId> scanWA(WriteAheadState state, int batchSize) {
    if (state == null || batchSize <= 0) {
        throw new IllegalArgumentException("state cannot be null and batchSize must be positive");
    }

    List<WriteAheadLogEntity> walEntries = walRepo.findByWalStateOrderByCreatedAtAsc(
        state, PageRequest.of(0, batchSize));

    return walEntries.stream()
        .map(wal -> new OpId(wal.getOpId()))
        .toList();
}
```

**JPA Repository**:
```java
public interface WriteAheadLogRepository extends JpaRepository<WriteAheadLogEntity, Long> {

    WriteAheadLogEntity findByOpId(UUID opId);

    @Query("SELECT w FROM WriteAheadLogEntity w WHERE w.walState = :state ORDER BY w.createdAt ASC")
    List<WriteAheadLogEntity> findByWalStateOrderByCreatedAtAsc(
        @Param("state") WriteAheadState state,
        Pageable pageable);
}
```

### 1.7 Store 구현 - scanInProgress()

**목적**: Reaper가 장기 IN_PROGRESS Operation을 스캔

```java
@Override
@Transactional(readOnly = true)
public List<OpId> scanInProgress(long timeoutThreshold, int batchSize) {
    if (timeoutThreshold <= 0 || batchSize <= 0) {
        throw new IllegalArgumentException("timeoutThreshold and batchSize must be positive");
    }

    Instant cutoffTime = Instant.now().minusMillis(timeoutThreshold);

    List<OperationEntity> operations = operationRepo.findStuckInProgress(
        OperationStateEnum.IN_PROGRESS,
        cutoffTime,
        PageRequest.of(0, batchSize));

    return operations.stream()
        .map(op -> new OpId(op.getId()))
        .toList();
}
```

**JPA Repository**:
```java
public interface OperationRepository extends JpaRepository<OperationEntity, UUID> {

    @Query("""
        SELECT o FROM OperationEntity o
        WHERE o.state = :state
          AND o.updatedAt < :cutoffTime
        ORDER BY o.updatedAt ASC
    """)
    List<OperationEntity> findStuckInProgress(
        @Param("state") OperationStateEnum state,
        @Param("cutoffTime") Instant cutoffTime,
        Pageable pageable);
}
```

---

## 2. Bus 인터페이스 구현

### 2.1 AWS SQS 어댑터 예시

**의존성 추가** (`build.gradle`):
```gradle
dependencies {
    implementation 'software.amazon.awssdk:sqs:2.20.0'
}
```

**SqsBus 구현**:
```java
package com.example.adapter.messaging;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.outcome.Fail;
import com.ryuqq.orchestrator.core.spi.Bus;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SqsBus implements Bus {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final String dlqUrl;
    private final ObjectMapper objectMapper;
    private final Map<OpId, String> receiptHandleCache = new java.util.concurrent.ConcurrentHashMap<>();

    public SqsBus(SqsClient sqsClient, String queueUrl, String dlqUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.dlqUrl = dlqUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void publish(Envelope envelope, long delayMs) {
        if (envelope == null || delayMs < 0) {
            throw new IllegalArgumentException("envelope cannot be null and delayMs must be non-negative");
        }

        try {
            String messageBody = objectMapper.writeValueAsString(envelope);

            SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .delaySeconds((int) (delayMs / 1000)) // SQS delay in seconds (max 900s)
                .messageAttributes(Map.of(
                    "OpId", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(envelope.opId().value().toString())
                        .build(),
                    "Domain", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(envelope.command().domain().value())
                        .build()
                ))
                .build();

            sqsClient.sendMessage(request);

        } catch (Exception e) {
            throw new RuntimeException("Failed to publish envelope to SQS", e);
        }
    }

    @Override
    public List<Envelope> dequeue(int batchSize) {
        if (batchSize <= 0 || batchSize > 10) {
            throw new IllegalArgumentException("batchSize must be between 1 and 10 for SQS");
        }

        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(batchSize)
            .visibilityTimeout(30) // 30초 동안 invisible
            .waitTimeSeconds(20)   // Long polling (20초)
            .build();

        ReceiveMessageResponse response = sqsClient.receiveMessage(request);

        return response.messages().stream()
            .map(this::deserializeEnvelope)
            .collect(Collectors.toList());
    }

    @Override
    public void ack(Envelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }

        // SQS Receipt Handle을 envelope에서 추출 (메타데이터로 저장 필요)
        String receiptHandle = getReceiptHandle(envelope);

        DeleteMessageRequest request = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .build();

        sqsClient.deleteMessage(request);
    }

    @Override
    public void nack(Envelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }

        String receiptHandle = getReceiptHandle(envelope);

        // Visibility timeout을 0으로 설정하여 즉시 재처리 가능하도록
        ChangeMessageVisibilityRequest request = ChangeMessageVisibilityRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(0)
            .build();

        sqsClient.changeMessageVisibility(request);
    }

    @Override
    public void publishToDLQ(Envelope envelope, Fail fail) {
        if (envelope == null || fail == null) {
            throw new IllegalArgumentException("envelope and fail cannot be null");
        }

        try {
            String messageBody = objectMapper.writeValueAsString(Map.of(
                "envelope", envelope,
                "failureReason", fail.reason(),
                "failureCode", fail.code(),
                "timestamp", System.currentTimeMillis()
            ));

            SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(dlqUrl)
                .messageBody(messageBody)
                .build();

            sqsClient.sendMessage(request);

        } catch (Exception e) {
            throw new RuntimeException("Failed to publish to DLQ", e);
        }
    }

    private Envelope deserializeEnvelope(Message message) {
        try {
            Envelope envelope = objectMapper.readValue(message.body(), Envelope.class);
            // OpId와 receiptHandle을 매핑하여 캐시에 저장
            receiptHandleCache.put(envelope.opId(), message.receiptHandle());
            return envelope;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize envelope", e);
        }
    }

    private String getReceiptHandle(Envelope envelope) {
        // 캐시에서 receiptHandle을 조회하고 사용 후 제거
        String receiptHandle = receiptHandleCache.remove(envelope.opId());
        if (receiptHandle == null) {
            throw new IllegalStateException("Receipt handle not found for OpId: " + envelope.opId() +
                ". It might have been already processed or expired.");
        }
        return receiptHandle;
    }
}
```

**핵심 포인트**:
- **Delay 메시지**: SQS의 delay seconds 기능 활용 (최대 900초)
- **Long Polling**: `waitTimeSeconds=20`으로 비용 절감
- **Visibility Timeout**: 30초 동안 다른 consumer가 못 받도록
- **DLQ**: 재시도 소진 후 Dead Letter Queue로 이동

---

## 3. Protection 어댑터 구현

### 3.1 Resilience4j 통합

**의존성 추가**:
```gradle
dependencies {
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.1.0'
    implementation 'io.github.resilience4j:resilience4j-circuitbreaker:2.1.0'
    implementation 'io.github.resilience4j:resilience4j-ratelimiter:2.1.0'
    implementation 'io.github.resilience4j:resilience4j-bulkhead:2.1.0'
}
```

### 3.2 CircuitBreaker 구현

```java
package com.example.adapter.protection;

import com.ryuqq.orchestrator.core.protection.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;
import java.util.concurrent.Callable;

public class Resilience4jCircuitBreaker implements CircuitBreaker {

    private final CircuitBreakerRegistry registry;

    public Resilience4jCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)                     // 실패율 50% 이상
            .slowCallRateThreshold(50)                     // 느린 호출 50% 이상
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .waitDurationInOpenState(Duration.ofSeconds(30)) // OPEN 30초 유지
            .slidingWindowSize(20)                         // 최근 20개 호출 기준
            .minimumNumberOfCalls(10)                      // 최소 10개 호출 필요
            .permittedNumberOfCallsInHalfOpenState(5)      // HALF_OPEN 시 5개 테스트
            .build();

        this.registry = CircuitBreakerRegistry.of(config);
    }

    @Override
    public <T> T call(String resourceKey, Callable<T> supplier) throws Exception {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
            registry.circuitBreaker(resourceKey);

        return cb.executeCallable(supplier);
    }

    @Override
    public State state(String resourceKey) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
            registry.circuitBreaker(resourceKey);

        return switch (cb.getState()) {
            case CLOSED -> State.CLOSED;
            case OPEN -> State.OPEN;
            case HALF_OPEN -> State.HALF_OPEN;
            default -> State.CLOSED;
        };
    }
}
```

### 3.3 RateLimiter 구현

```java
package com.example.adapter.protection;

import com.ryuqq.orchestrator.core.protection.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import java.time.Duration;

public class Resilience4jRateLimiter implements RateLimiter {

    private final RateLimiterRegistry registry;

    public Resilience4jRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(100)                  // 100 calls
            .limitRefreshPeriod(Duration.ofSeconds(1)) // per second
            .timeoutDuration(Duration.ofMillis(500))   // 500ms 대기
            .build();

        this.registry = RateLimiterRegistry.of(config);
    }

    @Override
    public boolean tryAcquire(String resourceKey, Duration maxWait) {
        io.github.resilience4j.ratelimiter.RateLimiter limiter =
            registry.rateLimiter(resourceKey);

        return limiter.tryAcquirePermission(1, maxWait);
    }
}
```

---

## 🎉 마무리

이제 실제 인프라로 Orchestrator SDK를 통합할 수 있습니다!

**다음 단계**:
- 정책 설정 가이드 (작성 예정): Retry, Idempotency, Transition 정책 설정
- 운영 가이드 (작성 예정): 관측성, 알람, 백프레셔 설정

**체크리스트**:
- [ ] Store 구현 완료 (writeAhead, finalize, scanWA, scanInProgress)
- [ ] Bus 구현 완료 (publish, dequeue, ack, nack, publishToDLQ)
- [ ] Protection 어댑터 설정 완료 (CircuitBreaker, RateLimiter)
- [ ] Contract Tests 통과 확인
