# OR-3: Inline Fast-Path Runner 구현 PRD

**작성일**: 2025-10-18
**작성자**: Orchestrator Team
**Epic**: OR-1 (Epic A: Core API 및 계약 구현)
**버전**: 1.0.0

---

## 1. Overview

### 1.1 목적

OR-3는 **Inline Fast-Path Runner**를 구현하는 작업으로, Orchestrator Core SDK의 핵심 실행 엔진 중 하나입니다. 이 러너는 클라이언트 요청에 대해 **지능형 동기/비동기 분기 전략**을 제공하여, 빠르게 완료 가능한 작업은 즉시 응답(HTTP 200)하고, 시간이 걸리는 작업은 비동기로 전환(HTTP 202)하는 Fast-Path 패턴을 구현합니다.

### 1.2 Epic OR-1과의 관계

Epic OR-1은 Orchestrator Core SDK의 기반 인프라를 구축하는 작업이며, OR-3는 다음 컴포넌트와 밀접하게 연관됩니다:

- **OR-2 (타입 모델 및 상태머신)**: OpId, Command, Envelope, Outcome, OperationState를 활용
- **핵심 API 3개**: Orchestrator, Executor, Runtime 중 **Orchestrator 인터페이스를 직접 구현**
- **Queue Worker Runner (OR-4)**: Fast-Path에서 처리하지 못한 비동기 작업을 Queue로 전달

### 1.3 해결하는 문제

기존 동기/비동기 처리 방식의 문제점:

1. **고정적 응답 방식**: 모든 요청을 동기 또는 비동기 중 하나로만 처리
2. **사용자 경험 저하**: 빠른 작업도 비동기 폴링 필요 → 불필요한 지연
3. **서버 리소스 낭비**: 느린 작업도 동기 처리 → 스레드 블로킹
4. **유연성 부족**: 작업 특성에 따른 적응적 처리 불가

**Inline Fast-Path Runner의 해결책**:

- **timeBudget 기반 적응형 분기**: 설정된 시간 내 완료 시 즉시 응답
- **소프트 폴링 메커니즘**: 블로킹 없이 작업 완료 감지
- **리소스 효율성**: 짧은 작업은 인라인 처리, 긴 작업은 비동기 전환
- **사용자 경험 최적화**: 80% 이상의 빠른 작업은 즉시 응답 제공

---

## 2. Requirements

### 2.1 기능 요구사항

#### FR-1: Orchestrator 인터페이스 구현

**설명**: `Orchestrator` 인터페이스를 구현하여 클라이언트 요청 수락 및 실행 조정 기능 제공

**상세**:
- **메서드**: `OperationHandle submit(Command command, long timeBudgetMs)`
  - `Command`: 실행할 업무 명령 (domain, eventType, bizKey, idemKey, payload)
  - `timeBudgetMs`: Fast-Path 대기 시간 제한 (밀리초, primitive long 타입)
  - `OperationHandle`: 작업 상태 및 결과를 추적할 수 있는 핸들
- **책임**:
  - Command 유효성 검증
  - OpId 생성 (UUID 기반)
  - Envelope 생성 (OpId + Command + acceptedAt)
  - Executor에게 실행 위임
  - OperationHandle 즉시 반환

**의존성**: OR-2의 Command, OpId, Envelope 타입

#### FR-2: OperationHandle 반환 로직

**설명**: Fast-Path 대기 결과를 표현하는 OperationHandle 구조 정의

**OperationHandle 필드**:
```java
public final class OperationHandle {
    private final OpId opId;
    private final boolean completedFast;        // timeBudget 내 완료 여부
    private final Outcome responseBodyOrNull;   // completedFast=true일 때만 non-null
    private final String statusUrlOrNull;       // completedFast=false일 때만 non-null
}
```

**결정 로직**:
- **completedFast = true**: timeBudget 내 완료
  - `responseBodyOrNull`: 실제 Outcome 결과 (Ok, Retry, Fail)
  - `statusUrlOrNull`: null
- **completedFast = false**: timeBudget 초과
  - `responseBodyOrNull`: null
  - `statusUrlOrNull`: `/api/operations/{opId}/status` 형식

**의존성**: OR-2의 OpId, Outcome 타입

#### FR-3: TimeBudget 기반 동기/비동기 분기

**설명**: 설정된 timeBudget 내 작업 완료 시 동기 응답(200), 초과 시 비동기 응답(202) 분기

**알고리즘**:
```
1. 현재 시각 기록: startTime = System.currentTimeMillis()
2. Executor.execute(envelope) 실행 시작
3. 소프트 폴링 시작:
   while (elapsed < timeBudget):
       if (작업 완료):
           return OperationHandle(completedFast=true, outcome)
       sleep(10ms)  // 폴링 간격
       elapsed = System.currentTimeMillis() - startTime
4. timeBudget 초과:
   return OperationHandle(completedFast=false, statusUrl)
```

**TimeBudget 값 예시**:
- **Fast-Path 우선**: 200ms (대부분의 빠른 작업 커버)
- **Balanced**: 500ms (중간 복잡도 작업 포함)
- **Conservative**: 1000ms (느린 작업도 일부 대기)

**제약사항**:
- timeBudget 범위: 50ms ~ 5000ms (설정 가능)
- 기본값: 200ms

#### FR-4: 소프트 폴링/이벤트 대기 메커니즘

**설명**: 작업 완료를 감지하기 위한 비블로킹 대기 메커니즘

**Phase 1: 소프트 폴링 (MVP)**:
- **방식**: 주기적으로 작업 상태 체크 (10ms 간격)
- **장점**: 구현 단순, 외부 의존성 없음
- **단점**: CPU 사용량 증가 가능 (짧은 간격)
- **적용 시나리오**: 초기 구현, 성능 테스트 통과 목표

**Phase 2: 이벤트 기반 대기 (향후 개선)**:
- **방식**: CompletableFuture 또는 이벤트 버스 활용
- **장점**: CPU 효율적, 즉각 반응
- **단점**: 복잡도 증가, 동시성 제어 필요
- **적용 시나리오**: 성능 최적화 필요 시

**OR-3 범위**: Phase 1 (소프트 폴링) 구현

**폴링 로직 상세**:
```java
long startTime = System.currentTimeMillis();
long pollingInterval = 10; // ms

while (System.currentTimeMillis() - startTime < timeBudget) {
    OperationState currentState = executor.getState(opId);
    if (currentState.isTerminal()) {
        Outcome outcome = executor.getOutcome(opId);
        return new OperationHandle(opId, true, outcome, null);
    }
    Thread.sleep(pollingInterval);
}
// timeBudget 초과
return new OperationHandle(opId, false, null, buildStatusUrl(opId));
```

#### FR-5: 응답 처리 로직

**설명**: Fast-Path 결과에 따른 HTTP 응답 생성 전략

**200 응답 (완료)**:
```json
{
  "opId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "outcome": {
    "type": "Ok",
    "message": "Order created successfully",
    "data": { "orderId": "ORDER-123" }
  }
}
```

**202 응답 (비동기 전환)**:
```json
{
  "opId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "statusUrl": "/api/operations/550e8400-e29b-41d4-a716-446655440000/status",
  "estimatedCompletionTime": "2025-10-18T12:00:00Z"
}
```

**응답 매핑 로직**:
- **completedFast = true**:
  - HTTP Status: 200
  - Body: opId + status(COMPLETED/FAILED) + outcome
- **completedFast = false**:
  - HTTP Status: 202
  - Body: opId + status(IN_PROGRESS) + statusUrl

**오류 응답**:
- **400 Bad Request**: Command 유효성 검증 실패
- **500 Internal Server Error**: Executor 실행 예외

### 2.2 비기능 요구사항

#### NFR-1: 성능

**목표**: 1000 TPS 이상 처리 가능 (단일 인스턴스)

**측정 기준**:
- **처리량**: 초당 요청 수 (Requests Per Second)
- **응답 시간**: P50, P95, P99 레이턴시
- **리소스 사용량**: CPU, 메모리, 스레드 수

**성능 시나리오**:
1. **Fast-Path 완료 케이스**: 80% 요청이 200ms 내 완료
   - 목표: P95 < 250ms
2. **Async 전환 케이스**: 20% 요청이 202 응답
   - 목표: P95 < 220ms (timeBudget + 오버헤드)
3. **혼합 부하**: 80/20 비율로 1000 TPS
   - 목표: 안정적 처리, 오류율 < 0.1%

#### NFR-2: 안정성

**멀티스레드 안전성**:
- Orchestrator 인스턴스는 **stateless** 설계 (thread-safe)
- Executor 상태 조회는 **동시성 제어** 필요
- OperationHandle 생성은 **불변 객체** 사용

**예외 처리**:
- Executor 실행 실패 → OperationHandle(completedFast=true, Fail outcome)
- 폴링 중 인터럽트 → graceful shutdown, 진행 중 작업 보존
- timeBudget 초과 → 정상적 202 응답, 백그라운드 계속 실행

#### NFR-3: 확장성

**수평 확장 가능**:
- Orchestrator 인스턴스는 상태 없음 → 다중 인스턴스 배포 가능
- OpId 기반 상태 조회 → 인스턴스 간 상태 공유 불필요 (Executor가 처리)

**설정 유연성**:
- timeBudget: 애플리케이션별 커스터마이징
- 폴링 간격: 성능/CPU 트레이드오프 조정 가능

#### NFR-4: 관찰 가능성

**메트릭**:
- `orchestrator.submit.count`: 총 요청 수
- `orchestrator.fast_path.completed`: Fast-Path 완료 수
- `orchestrator.async.converted`: 비동기 전환 수
- `orchestrator.latency`: 응답 시간 분포

**로깅**:
- INFO: 요청 수락, Fast-Path 완료, Async 전환
- WARN: timeBudget 경계값 근처, Executor 지연
- ERROR: Executor 실행 실패, 예상치 못한 예외

### 2.3 제약사항

#### 아키텍처 제약

**헥사고날 아키텍처 준수**:
- Orchestrator는 **애플리케이션 레이어** 인터페이스
- 구현체(InlineFastPathRunner)는 **인프라 레이어** (adapter-runner 모듈)
- Executor는 **도메인 서비스** (의존성 주입)

**의존성 방향**:
```
adapter-runner (InlineFastPathRunner)
  ↓ depends on
application (Orchestrator interface)
  ↓ depends on
domain (Executor, OperationState, Outcome)
  ↓ depends on
core (OpId, Command, Envelope)
```

#### 기술 스택 제약

**Spring Boot 3.5.x**:
- Spring Context에서 Bean 관리
- `@Component` 또는 `@Service`로 등록
- 생성자 주입 방식 사용

**Java 21**:
- Virtual Thread 활용 고려 (폴링 대기 시)
- Pattern Matching, Record 활용
- Sealed Interface 준수 (Outcome)

**Lombok 금지**:
- Pure Java getter/setter 작성
- Builder 패턴 수동 구현

---

## 3. Technical Specifications

### 3.1 Orchestrator 인터페이스 설계

**위치**: `application/src/main/java/com/ryuqq/orchestrator/application/orchestrator/Orchestrator.java`

**인터페이스 정의**:
```java
package com.ryuqq.orchestrator.application.orchestrator;

import com.ryuqq.orchestrator.core.contract.Command;

/**
 * Operation 실행 조정자.
 *
 * <p>클라이언트 요청을 수락하고, timeBudget 기반 동기/비동기 분기를 수행합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface Orchestrator {

    /**
     * Command를 제출하고 Fast-Path 대기.
     *
     * @param command 실행할 명령
     * @param timeBudgetMs Fast-Path 대기 시간 (밀리초)
     * @return OperationHandle (완료 여부 및 결과)
     * @throws IllegalArgumentException command가 null이거나 유효하지 않은 경우
     * @throws IllegalArgumentException timeBudgetMs가 허용 범위를 벗어난 경우
     */
    OperationHandle submit(Command command, long timeBudgetMs);
}
```

**설계 원칙**:
- **단일 책임**: 요청 수락 및 Fast-Path 분기만 담당
- **의존성 역전**: Executor 인터페이스에 의존 (구현 아님)
- **불변성**: OperationHandle 반환 시 상태 변경 불가

### 3.2 OperationHandle 설계

**위치**: `application/src/main/java/com/ryuqq/orchestrator/application/orchestrator/OperationHandle.java`

**클래스 정의**:
```java
package com.ryuqq.orchestrator.application.orchestrator;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Outcome;

/**
 * Operation 실행 핸들.
 *
 * <p>Fast-Path 대기 결과를 표현하며, 동기/비동기 응답 전략을 결정합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class OperationHandle {

    private final OpId opId;
    private final boolean completedFast;
    private final Outcome responseBodyOrNull;
    private final String statusUrlOrNull;

    // Private constructor - use static factory methods
    private OperationHandle(OpId opId, boolean completedFast,
                            Outcome responseBodyOrNull, String statusUrlOrNull) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        this.opId = opId;
        this.completedFast = completedFast;
        this.responseBodyOrNull = responseBodyOrNull;
        this.statusUrlOrNull = statusUrlOrNull;
    }

    /**
     * Fast-Path 완료 핸들 생성.
     *
     * @param opId Operation ID
     * @param outcome 실행 결과
     * @return OperationHandle
     */
    public static OperationHandle completed(OpId opId, Outcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome cannot be null");
        }
        return new OperationHandle(opId, true, outcome, null);
    }

    /**
     * 비동기 전환 핸들 생성.
     *
     * @param opId Operation ID
     * @param statusUrl 상태 조회 URL
     * @return OperationHandle
     */
    public static OperationHandle async(OpId opId, String statusUrl) {
        if (statusUrl == null || statusUrl.isBlank()) {
            throw new IllegalArgumentException("statusUrl cannot be null or blank");
        }
        return new OperationHandle(opId, false, null, statusUrl);
    }

    // Getters
    public OpId getOpId() { return opId; }
    public boolean isCompletedFast() { return completedFast; }
    public Outcome getResponseBodyOrNull() { return responseBodyOrNull; }
    public String getStatusUrlOrNull() { return statusUrlOrNull; }
}
```

### 3.3 InlineFastPathRunner 구현 설계

**위치**: `adapter-runner/src/main/java/com/ryuqq/orchestrator/adapter/runner/InlineFastPathRunner.java`

**클래스 구조**:
```java
package com.ryuqq.orchestrator.adapter.runner;

import com.ryuqq.orchestrator.application.orchestrator.Orchestrator;
import com.ryuqq.orchestrator.application.orchestrator.OperationHandle;
import com.ryuqq.orchestrator.core.contract.Command;
import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.domain.executor.Executor;

/**
 * Inline Fast-Path Runner 구현체.
 *
 * <p>timeBudget 기반 동기/비동기 분기를 수행합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class InlineFastPathRunner implements Orchestrator {

    private static final long MIN_TIME_BUDGET_MS = 50;
    private static final long MAX_TIME_BUDGET_MS = 5000;
    private static final long DEFAULT_POLLING_INTERVAL_MS = 10;

    private final Executor executor;
    private final long pollingIntervalMs;

    /**
     * 생성자.
     *
     * @param executor 작업 실행자
     */
    public InlineFastPathRunner(Executor executor) {
        this(executor, DEFAULT_POLLING_INTERVAL_MS);
    }

    /**
     * 생성자 (폴링 간격 커스터마이징).
     *
     * @param executor 작업 실행자
     * @param pollingIntervalMs 폴링 간격 (밀리초)
     */
    public InlineFastPathRunner(Executor executor, long pollingIntervalMs) {
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        if (pollingIntervalMs <= 0) {
            throw new IllegalArgumentException("pollingIntervalMs must be positive");
        }
        this.executor = executor;
        this.pollingIntervalMs = pollingIntervalMs;
    }

    @Override
    public OperationHandle submit(Command command, long timeBudgetMs) {
        validateInput(command, timeBudgetMs);

        // 1. OpId 생성
        OpId opId = OpId.of(generateOpIdValue());

        // 2. Envelope 생성
        Envelope envelope = Envelope.now(opId, command);

        // 3. Executor에게 실행 시작 요청
        executor.execute(envelope);

        // 4. Fast-Path 폴링
        return pollForCompletion(opId, timeBudgetMs);
    }

    private void validateInput(Command command, long timeBudgetMs) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        if (timeBudgetMs < MIN_TIME_BUDGET_MS || timeBudgetMs > MAX_TIME_BUDGET_MS) {
            throw new IllegalArgumentException(
                String.format("timeBudgetMs must be between %d and %d ms (current: %d)",
                    MIN_TIME_BUDGET_MS, MAX_TIME_BUDGET_MS, timeBudgetMs));
        }
    }

    private OperationHandle pollForCompletion(OpId opId, long timeBudgetMs) {
        long startTimeNanos = System.nanoTime();
        long timeBudgetNanos = timeBudgetMs * 1_000_000L; // ms를 ns로 변환

        while (System.nanoTime() - startTimeNanos < timeBudgetNanos) {
            OperationState state = executor.getState(opId);

            if (state.isTerminal()) {
                Outcome outcome = executor.getOutcome(opId);
                return OperationHandle.completed(opId, outcome);
            }

            sleep(pollingIntervalMs);
        }

        // timeBudget 초과 → 비동기 전환
        String statusUrl = buildStatusUrl(opId);
        return OperationHandle.async(opId, statusUrl);
    }

    private String generateOpIdValue() {
        return java.util.UUID.randomUUID().toString();
    }

    private String buildStatusUrl(OpId opId) {
        return "/api/operations/" + opId.getValue() + "/status";
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Polling interrupted", e);
        }
    }
}
```

### 3.4 TimeBudget 분기 알고리즘

**알고리즘 흐름도**:
```
START
  ↓
[Command 수락]
  ↓
[OpId 생성]
  ↓
[Envelope 생성]
  ↓
[Executor.execute(envelope)] → 비동기 실행 시작
  ↓
[startTime 기록]
  ↓
┌─────────────────────┐
│ Polling Loop        │
│ while (elapsed < TB)│
│   ↓                 │
│ [getState(opId)]    │
│   ↓                 │
│ isTerminal?         │
│   YES → [getOutcome]│ → [200 응답]
│   NO  → [sleep 10ms]│ → 계속 루프
│   ↓                 │
│ [elapsed 계산]      │
└─────────────────────┘
  ↓
[timeBudget 초과]
  ↓
[202 응답 + statusUrl]
  ↓
END
```

**경계값 처리**:
- **정확히 timeBudget 시점**: 마지막 폴링 이후 sleep 중 초과 가능
  - 해결: while 조건을 `<` (미만)으로 설정 → timeBudget 도달 즉시 종료
- **폴링 간격 오차**: 10ms sleep ≠ 정확히 10ms
  - 해결: 경과 시간을 `System.currentTimeMillis()` 기준으로 계산

### 3.5 상태 조회 메커니즘

**Executor 인터페이스 (가정)**:
```java
public interface Executor {
    void execute(Envelope envelope);
    OperationState getState(OpId opId);
    Outcome getOutcome(OpId opId);
}
```

**InlineFastPathRunner의 Executor 사용**:
- **execute()**: 작업 시작 (비블로킹, 백그라운드 실행)
- **getState()**: 현재 상태 조회 (PENDING, IN_PROGRESS, COMPLETED, FAILED)
- **getOutcome()**: 최종 결과 조회 (Ok, Retry, Fail) - COMPLETED/FAILED 상태에서만 호출

**동시성 제어**:
- Executor 구현체는 상태 조회 시 **thread-safe** 보장 필요
- InlineFastPathRunner는 상태 없음 → 동시성 문제 없음

---

## 4. Implementation Plan

### 4.1 작업 분해 (TodoList 기반)

#### Task 1: 브랜치 체크아웃 (완료)
- 상태: ✅ 완료
- 브랜치: `feature/OR-3-inline-fast-path-runner`

#### Task 2: 설계 분석 (timeBudget 전략)
- **목표**: Fast-Path 폴링 알고리즘 설계 확정
- **산출물**:
  - 폴링 간격 결정 (10ms vs 50ms vs 100ms)
  - timeBudget 범위 정의 (50ms ~ 5000ms)
  - 경계값 처리 전략 문서화
- **검증**: 설계 리뷰 (팀 승인)

#### Task 3: Orchestrator 인터페이스 구현
- **목표**: `Orchestrator.java` 인터페이스 정의
- **작업 내용**:
  - `submit(Command, timeBudget)` 메서드 시그니처 정의
  - Javadoc 작성 (`@param`, `@return`, `@throws`)
  - package-info.java 작성 (모듈 설명)
- **위치**: `application/src/main/java/.../orchestrator/Orchestrator.java`
- **검증**: 컴파일 성공, Javadoc 생성 확인

#### Task 4: OperationHandle 반환 로직
- **목표**: `OperationHandle.java` 클래스 구현
- **작업 내용**:
  - 필드 정의 (opId, completedFast, responseBodyOrNull, statusUrlOrNull)
  - 정적 팩토리 메서드 (`completed()`, `async()`)
  - 불변성 보장 (final 필드, private 생성자)
  - Javadoc 작성
- **위치**: `application/src/main/java/.../orchestrator/OperationHandle.java`
- **검증**: 유닛 테스트 작성 (OperationHandleTest.java)

#### Task 5: 동기/비동기 분기 로직 (200 vs 202)
- **목표**: `InlineFastPathRunner.java` 핵심 로직 구현
- **작업 내용**:
  - `submit()` 메서드 구현
  - timeBudget 유효성 검증
  - OpId 생성 (UUID 기반)
  - Envelope 생성 및 Executor.execute() 호출
- **위치**: `adapter-runner/src/main/java/.../runner/InlineFastPathRunner.java`
- **검증**: 컴파일 성공, 기본 플로우 동작 확인

#### Task 6: 소프트 폴링/이벤트 대기 메커니즘
- **목표**: `pollForCompletion()` 메서드 구현
- **작업 내용**:
  - while 루프 구현 (elapsed < timeBudget)
  - Executor.getState() 호출
  - isTerminal() 체크 → Outcome 조회
  - Thread.sleep() 예외 처리
- **검증**: 폴링 로직 단위 테스트

#### Task 7: 응답 처리 로직 (완료/타임아웃)
- **목표**: OperationHandle 생성 로직 완성
- **작업 내용**:
  - `OperationHandle.completed()` 호출 (Fast-Path 완료)
  - `OperationHandle.async()` 호출 (timeBudget 초과)
  - statusUrl 생성 로직 (`buildStatusUrl()`)
- **검증**: 응답 포맷 확인 (JSON 직렬화 가능)

#### Task 8: 유닛 테스트 - timeBudget 내 완료 시 200 응답
- **목표**: Acceptance Criteria #1 검증
- **테스트 시나리오**:
  - timeBudget = 200ms, 작업 완료 시간 = 100ms
  - 예상: completedFast = true, outcome = Ok
- **Mock**: Executor.getState()가 100ms 후 COMPLETED 반환
- **검증**: OperationHandle.isCompletedFast() == true

#### Task 9: 유닛 테스트 - timeBudget 초과 시 202 응답
- **목표**: Acceptance Criteria #2 검증
- **테스트 시나리오**:
  - timeBudget = 200ms, 작업 완료 시간 = 500ms
  - 예상: completedFast = false, statusUrl != null
- **Mock**: Executor.getState()가 계속 IN_PROGRESS 반환
- **검증**: OperationHandle.isCompletedFast() == false

#### Task 10: 유닛 테스트 - 경계값 테스트
- **목표**: Acceptance Criteria #3 검증
- **테스트 시나리오**:
  - timeBudget = 200ms, 작업 완료 시간 = 정확히 200ms
  - 예상: 구현에 따라 200 또는 202 (명확한 정의 필요)
- **검증**: 경계값 동작 명세 확인

#### Task 11: 멀티스레드 안전성 검증 테스트
- **목표**: Acceptance Criteria #4 검증
- **테스트 시나리오**:
  - 10개 스레드에서 동시에 submit() 호출
  - 각 스레드마다 다른 Command
- **검증**:
  - 모든 요청이 정상 처리됨
  - OpId 중복 없음
  - Race condition 없음
- **도구**: ExecutorService, CountDownLatch

#### Task 12: 성능 테스트 - 1000 TPS 이상
- **목표**: Acceptance Criteria #5 검증
- **테스트 환경**:
  - JMeter 또는 Gatling
  - 부하: 1000 RPS (Requests Per Second)
  - 지속 시간: 60초
- **측정 항목**:
  - P50, P95, P99 레이턴시
  - 오류율
  - CPU/메모리 사용량
- **성공 기준**:
  - P95 < 250ms (Fast-Path 완료)
  - 오류율 < 0.1%

#### Task 13: Javadoc 작성
- **목표**: Epic OR-1 DoD 충족 (모든 공개 API Javadoc)
- **대상**:
  - Orchestrator 인터페이스
  - OperationHandle 클래스
  - InlineFastPathRunner 클래스
- **포함 내용**:
  - `@author Orchestrator Team`
  - `@since 1.0.0`
  - `@param`, `@return`, `@throws` 상세 설명
- **검증**: `mvn javadoc:javadoc` 성공, HTML 생성 확인

#### Task 14: 테스트 커버리지 ≥ 80% 확인
- **목표**: Epic OR-1 DoD 충족
- **도구**: JaCoCo
- **대상**:
  - InlineFastPathRunner 클래스
  - OperationHandle 클래스
- **커버리지 타겟**:
  - Line Coverage: ≥ 80%
  - Branch Coverage: ≥ 70%
- **검증**: `mvn test jacoco:report` 후 리포트 확인

#### Task 15: 코드 리뷰 준비
- **목표**: 리뷰 가능한 상태로 정리
- **체크리스트**:
  - [ ] 모든 테스트 통과
  - [ ] Javadoc 완성
  - [ ] 코드 스타일 준수 (Checkstyle)
  - [ ] Lombok 미사용 확인
  - [ ] Law of Demeter 준수
  - [ ] 커밋 메시지 정리
- **산출물**: PR 설명 초안 작성

#### Task 16: PR 생성
- **목표**: Epic OR-1 리뷰 요청
- **PR 템플릿**:
  - **제목**: `[OR-3] Inline Fast-Path Runner 구현`
  - **설명**:
    - 구현 내용 요약
    - Acceptance Criteria 충족 여부
    - 성능 테스트 결과
    - 스크린샷 (테스트 커버리지 리포트)
  - **리뷰어**: Epic OR-1 담당자
- **검증**: CI/CD 파이프라인 통과

### 4.2 작업 일정 (예상)

| Task | 예상 시간 | 의존성 |
|------|-----------|--------|
| Task 2 (설계) | 2시간 | - |
| Task 3 (Orchestrator IF) | 1시간 | Task 2 |
| Task 4 (OperationHandle) | 2시간 | Task 3 |
| Task 5 (분기 로직) | 3시간 | Task 4 |
| Task 6 (폴링 메커니즘) | 3시간 | Task 5 |
| Task 7 (응답 처리) | 2시간 | Task 6 |
| Task 8-10 (유닛 테스트) | 4시간 | Task 7 |
| Task 11 (멀티스레드 테스트) | 3시간 | Task 10 |
| Task 12 (성능 테스트) | 4시간 | Task 11 |
| Task 13 (Javadoc) | 2시간 | Task 12 |
| Task 14 (커버리지) | 1시간 | Task 13 |
| Task 15-16 (리뷰/PR) | 2시간 | Task 14 |
| **총합** | **29시간** | |

---

## 5. Testing Strategy

### 5.1 유닛 테스트

#### OperationHandleTest
```java
@Test
void completed_핸들_생성() {
    // given
    OpId opId = OpId.of("test-op-id");
    Outcome outcome = new Ok("Success");

    // when
    OperationHandle handle = OperationHandle.completed(opId, outcome);

    // then
    assertTrue(handle.isCompletedFast());
    assertEquals(outcome, handle.getResponseBodyOrNull());
    assertNull(handle.getStatusUrlOrNull());
}

@Test
void async_핸들_생성() {
    // given
    OpId opId = OpId.of("test-op-id");
    String statusUrl = "/api/operations/test-op-id/status";

    // when
    OperationHandle handle = OperationHandle.async(opId, statusUrl);

    // then
    assertFalse(handle.isCompletedFast());
    assertNull(handle.getResponseBodyOrNull());
    assertEquals(statusUrl, handle.getStatusUrlOrNull());
}
```

#### InlineFastPathRunnerTest
```java
@Test
void timeBudget_내_완료_시_200_응답() {
    // given
    Executor mockExecutor = mock(Executor.class);
    when(mockExecutor.getState(any()))
        .thenReturn(OperationState.IN_PROGRESS)
        .thenReturn(OperationState.COMPLETED);
    when(mockExecutor.getOutcome(any()))
        .thenReturn(new Ok("Success"));

    InlineFastPathRunner runner = new InlineFastPathRunner(mockExecutor);
    Command command = createTestCommand();

    // when
    OperationHandle handle = runner.submit(command, 200);

    // then
    assertTrue(handle.isCompletedFast());
    assertNotNull(handle.getResponseBodyOrNull());
    assertNull(handle.getStatusUrlOrNull());
}

@Test
void timeBudget_초과_시_202_응답() {
    // given
    Executor mockExecutor = mock(Executor.class);
    when(mockExecutor.getState(any()))
        .thenReturn(OperationState.IN_PROGRESS); // 계속 진행 중

    InlineFastPathRunner runner = new InlineFastPathRunner(mockExecutor);
    Command command = createTestCommand();

    // when
    OperationHandle handle = runner.submit(command, 100);

    // then
    assertFalse(handle.isCompletedFast());
    assertNull(handle.getResponseBodyOrNull());
    assertNotNull(handle.getStatusUrlOrNull());
}
```

### 5.2 통합 테스트

#### 실제 Executor 구현체와 통합
```java
@SpringBootTest
class InlineFastPathRunnerIntegrationTest {

    @Autowired
    private Orchestrator orchestrator;

    @Test
    void 빠른_작업_Fast_Path_완료() {
        // given
        Command command = createFastCommand(); // 50ms 이내 완료

        // when
        OperationHandle handle = orchestrator.submit(command, 200);

        // then
        assertTrue(handle.isCompletedFast());
    }

    @Test
    void 느린_작업_비동기_전환() {
        // given
        Command command = createSlowCommand(); // 500ms 소요

        // when
        OperationHandle handle = orchestrator.submit(command, 100);

        // then
        assertFalse(handle.isCompletedFast());
    }
}
```

### 5.3 멀티스레드 안전성 테스트

```java
@Test
void 동시_요청_처리_안전성() throws Exception {
    // given
    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    Set<String> opIds = Collections.synchronizedSet(new HashSet<>());

    // when
    for (int i = 0; i < threadCount; i++) {
        int index = i;
        executorService.submit(() -> {
            try {
                Command command = createTestCommand("cmd-" + index);
                OperationHandle handle = orchestrator.submit(command, 200);
                opIds.add(handle.getOpId().getValue());
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(10, TimeUnit.SECONDS);
    executorService.shutdown();

    // then
    assertEquals(threadCount, opIds.size()); // OpId 중복 없음
}
```

### 5.4 성능 테스트

#### JMeter 시나리오
```xml
<ThreadGroup>
  <stringProp name="ThreadGroup.num_threads">100</stringProp>
  <stringProp name="ThreadGroup.ramp_time">10</stringProp>
  <stringProp name="ThreadGroup.duration">60</stringProp>
</ThreadGroup>
```

#### 성능 목표
- **처리량**: 1000 TPS
- **레이턴시**:
  - P50 < 150ms
  - P95 < 250ms
  - P99 < 500ms
- **오류율**: < 0.1%

---

## 6. Quality Gates

### 6.1 코드 품질

#### Checkstyle 규칙
- 모든 public 클래스/메서드 Javadoc 필수
- `@author`, `@since` 태그 포함
- 들여쓰기 4칸 (탭 아님)
- 최대 라인 길이 120자

#### Lombok 금지 검증
```bash
# adapter-runner 모듈에서 Lombok 사용 확인
grep -r "@Data\|@Builder\|@Getter\|@Setter" adapter-runner/src
# 결과: 없어야 함
```

### 6.2 테스트 커버리지

#### JaCoCo 설정
```xml
<execution>
  <id>jacoco-check</id>
  <goals>
    <goal>check</goal>
  </goals>
  <configuration>
    <rules>
      <rule>
        <element>CLASS</element>
        <limits>
          <limit>
            <counter>LINE</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.80</minimum>
          </limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</execution>
```

#### 커버리지 타겟
- **InlineFastPathRunner**: ≥ 85%
- **OperationHandle**: ≥ 90% (단순 클래스)

### 6.3 ArchUnit 검증

#### 레이어 의존성 규칙
```java
@Test
void adapter_레이어는_application_레이어에만_의존() {
    layeredArchitecture()
        .layer("Adapter").definedBy("..adapter..")
        .layer("Application").definedBy("..application..")
        .layer("Domain").definedBy("..domain..")
        .whereLayer("Adapter").mayOnlyAccessLayers("Application", "Domain")
        .check(importedClasses);
}
```

### 6.4 코드 리뷰 체크리스트

- [ ] **기능 요구사항**: FR-1 ~ FR-5 모두 구현됨
- [ ] **비기능 요구사항**: 성능 테스트 통과
- [ ] **아키텍처**: 헥사고날 레이어 준수
- [ ] **코드 품질**: Lombok 미사용, Law of Demeter 준수
- [ ] **테스트**: 커버리지 ≥ 80%, Acceptance Criteria 모두 검증
- [ ] **문서화**: Javadoc 완성, 주석 명확
- [ ] **에러 처리**: 예외 케이스 모두 처리
- [ ] **동시성**: 멀티스레드 안전성 검증

---

## 7. Acceptance Criteria (상세)

### AC-1: timeBudget 내 완료 시 200 응답

**조건**:
- Command 제출
- timeBudget = 200ms
- 실제 작업 완료 시간 = 100ms

**검증 방법**:
```java
@Test
void AC1_timeBudget_내_완료() {
    OperationHandle handle = orchestrator.submit(command, 200);

    assertTrue(handle.isCompletedFast());
    assertNotNull(handle.getResponseBodyOrNull());
    assertNull(handle.getStatusUrlOrNull());
}
```

**예상 응답**:
```json
{
  "opId": "...",
  "status": "COMPLETED",
  "outcome": { "type": "Ok", ... }
}
```

### AC-2: timeBudget 초과 시 202 응답

**조건**:
- Command 제출
- timeBudget = 200ms
- 실제 작업 완료 시간 = 500ms (초과)

**검증 방법**:
```java
@Test
void AC2_timeBudget_초과() {
    OperationHandle handle = orchestrator.submit(command, 200);

    assertFalse(handle.isCompletedFast());
    assertNull(handle.getResponseBodyOrNull());
    assertNotNull(handle.getStatusUrlOrNull());
    assertTrue(handle.getStatusUrlOrNull().contains("/status/"));
}
```

**예상 응답**:
```json
{
  "opId": "...",
  "status": "IN_PROGRESS",
  "statusUrl": "/api/operations/.../status"
}
```

### AC-3: 경계값 테스트

**시나리오 1**: timeBudget = 200ms, 완료 시간 = 199ms
- 예상: completedFast = true

**시나리오 2**: timeBudget = 200ms, 완료 시간 = 201ms
- 예상: completedFast = false

**시나리오 3**: timeBudget = 200ms, 완료 시간 = 정확히 200ms
- 예상: 명세에 따라 결정 (문서화 필요)
- 권장: false (보수적 처리)

**검증 방법**:
```java
@ParameterizedTest
@CsvSource({
    "200, 199, true",
    "200, 201, false",
    "200, 200, false"  // 경계값은 false로 처리
})
void AC3_경계값_처리(long timeBudget, long actualTime, boolean expectedFast) {
    // Mock Executor to complete after actualTime
    OperationHandle handle = orchestrator.submit(command, timeBudget);
    assertEquals(expectedFast, handle.isCompletedFast());
}
```

### AC-4: 멀티스레드 안전성

**조건**:
- 10개 스레드에서 동시 요청
- 각각 다른 Command
- timeBudget = 200ms

**검증 방법**:
```java
@Test
void AC4_멀티스레드_안전성() {
    // 동시 실행
    List<OperationHandle> handles = submitConcurrently(10);

    // 검증
    Set<String> opIds = handles.stream()
        .map(h -> h.getOpId().getValue())
        .collect(Collectors.toSet());

    assertEquals(10, opIds.size()); // OpId 중복 없음
    assertTrue(handles.stream().allMatch(h -> h.getOpId() != null));
}
```

**성공 기준**:
- Race condition 없음
- OpId 중복 없음
- 모든 요청 정상 처리

### AC-5: 성능 - 1000 TPS 이상

**조건**:
- 부하: 1000 RPS
- 지속 시간: 60초
- Fast-Path 완료율: 80%

**검증 방법**:
- JMeter/Gatling 부하 테스트
- 메트릭 수집 (Micrometer)

**성공 기준**:
- **처리량**: ≥ 1000 RPS
- **레이턴시**:
  - P50 < 150ms
  - P95 < 250ms
  - P99 < 500ms
- **오류율**: < 0.1%
- **리소스**:
  - CPU < 70%
  - 메모리 < 512MB

---

## 8. Dependencies

### 8.1 OR-2 의존성

**타입 모델**:
- `OpId`: Operation 식별자
- `Command`: 실행 명령 (domain, eventType, bizKey, idemKey, payload)
- `Envelope`: Command + OpId + acceptedAt
- `Outcome`: 실행 결과 (Ok, Retry, Fail)
- `OperationState`: 상태 (PENDING, IN_PROGRESS, COMPLETED, FAILED)

**상태머신**:
- `StateTransition.validate()`: 상태 전이 검증

**OR-3가 OR-2에 의존하는 이유**:
- Orchestrator.submit()이 Command를 입력으로 받음
- OperationHandle이 Outcome을 포함
- Executor.getState()가 OperationState 반환

### 8.2 Epic OR-1 작업 간 관계

**OR-3 → OR-4 (Queue Worker)**:
- Fast-Path에서 처리하지 못한 작업은 Queue로 전달
- OperationHandle.async() 응답 후 백그라운드 실행 계속

**OR-3 → Protection SPI**:
- 향후 Rate Limiting, Circuit Breaker 적용 가능
- Orchestrator.submit() 진입 시점에 보호 로직 추가

### 8.3 외부 의존성

**Spring Boot**:
- `spring-boot-starter`: 기본 의존성
- `spring-boot-starter-test`: 테스트 의존성

**JUnit 5**:
- `junit-jupiter`: 유닛 테스트
- `mockito-core`: Mock 객체

**JaCoCo**:
- 테스트 커버리지 측정

**ArchUnit**:
- 아키텍처 규칙 검증

---

## 9. Risks & Mitigation

### 9.1 timeBudget 경계값 처리 리스크

**리스크**:
- 폴링 간격(10ms)과 timeBudget의 조합에 따라 경계값 동작이 예측 불가능할 수 있음
- 예: timeBudget=200ms, 폴링 간격=10ms → 마지막 폴링이 190ms 또는 200ms에 발생

**영향**:
- 사용자가 200ms 내 완료를 기대했으나 202 응답 받을 수 있음

**완화 전략**:
1. **명확한 명세**: 경계값은 보수적으로 처리 (timeBudget 도달 시점에는 무조건 202)
2. **테스트 케이스**: 경계값 시나리오를 명시적으로 테스트
3. **문서화**: API 문서에 경계값 동작 명시

### 9.2 멀티스레드 환경 동시성 이슈

**리스크**:
- Executor.getState() 호출 시 동시성 문제 발생 가능
- OpId 생성 시 UUID 충돌 가능성 (극히 낮음)

**영향**:
- Race condition으로 인한 잘못된 상태 조회
- OpId 중복으로 인한 작업 추적 실패

**완화 전략**:
1. **Stateless 설계**: InlineFastPathRunner는 상태를 가지지 않음
2. **Executor 구현 책임**: Executor 구현체가 thread-safe 보장
3. **UUID v4 사용**: 충돌 확률 무시 가능 수준
4. **멀티스레드 테스트**: Task 11에서 명시적 검증

### 9.3 성능 목표 미달성 리스크

**리스크**:
- 폴링 오버헤드로 인해 1000 TPS 목표 달성 실패
- Thread.sleep() 호출이 CPU 리소스 낭비

**영향**:
- 성능 SLA 미충족
- 프로덕션 배포 지연

**완화 전략**:
1. **초기 성능 테스트**: Task 12에서 조기 검증
2. **폴링 간격 최적화**: 10ms → 50ms로 조정 고려 (CPU vs 반응성 트레이드오프)
3. **Virtual Thread 활용**: Java 21 Virtual Thread로 블로킹 비용 감소
4. **Phase 2 준비**: 이벤트 기반 대기 메커니즘 설계 준비

### 9.4 Executor 구현 지연 리스크

**리스크**:
- OR-3 구현 시점에 Executor 인터페이스가 아직 미완성일 수 있음
- Executor의 동작이 예상과 다를 수 있음

**영향**:
- 통합 테스트 불가
- Fast-Path 로직 검증 지연

**완화 전략**:
1. **Mock 기반 개발**: Mockito로 Executor 행동 시뮬레이션
2. **인터페이스 먼저 정의**: Executor 인터페이스만 먼저 확정
3. **계약 기반 테스트**: Consumer-Driven Contract Testing 고려

---

## 10. Definition of Done

### 10.1 기능 완성도

- [x] Task 1-7: 핵심 기능 구현 완료
  - Orchestrator 인터페이스
  - OperationHandle 클래스
  - InlineFastPathRunner 구현
  - 폴링 메커니즘

### 10.2 테스트 완성도

- [ ] Task 8-10: 유닛 테스트 통과 (AC 1-3)
- [ ] Task 11: 멀티스레드 안전성 검증 (AC 4)
- [ ] Task 12: 성능 테스트 통과 (AC 5)
- [ ] 테스트 커버리지 ≥ 80% (JaCoCo)

### 10.3 문서화

- [ ] Task 13: Javadoc 작성 완료
  - 모든 public 클래스/메서드
  - `@author`, `@since` 태그 포함
- [ ] package-info.java 작성

### 10.4 코드 품질

- [ ] Checkstyle 통과
- [ ] Lombok 미사용 확인
- [ ] Law of Demeter 준수
- [ ] ArchUnit 레이어 의존성 검증 통과

### 10.5 Epic OR-1 DoD

- [ ] 모든 공개 API Javadoc 작성
- [ ] 유닛 테스트 커버리지 ≥ 80%
- [ ] 상태 전이 불변식 검증 (OR-2 상태머신 활용)
- [ ] ArchUnit 헥사고날 의존성 규칙 검증

### 10.6 PR 승인

- [ ] Task 15: 코드 리뷰 완료
- [ ] Task 16: PR 머지
- [ ] CI/CD 파이프라인 통과

---

## 11. 참고 자료

### 11.1 관련 문서

- **Epic OR-1**: Epic A: Core API 및 계약 구현
- **OR-2 PRD**: 타입 모델 및 상태머신 구현
- **헥사고날 아키텍처 가이드**: `docs/coding_convention/00-architecture/`
- **Spring Boot 3.5.x 문서**: https://docs.spring.io/spring-boot/docs/3.5.x/reference/html/

### 11.2 코드 예시

- **OpId 구현**: `orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/model/OpId.java`
- **OperationState**: `orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/statemachine/OperationState.java`
- **StateTransition**: `orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/statemachine/StateTransition.java`

### 11.3 성능 벤치마크

- **목표 TPS**: 1000 RPS
- **예상 폴링 오버헤드**: 10ms * (timeBudget / 10) = 최대 200ms
- **Virtual Thread 활용**: Java 21 Project Loom

---

## 12. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|------|------|-----------|--------|
| 1.0.0 | 2025-10-18 | 최초 작성 | Orchestrator Team |

---

**문서 승인**: (Epic Owner 승인 필요)
**다음 단계**: OR-4 (Queue Worker Runner 구현 PRD)
