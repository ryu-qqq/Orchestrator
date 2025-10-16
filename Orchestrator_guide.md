# Orchestrator 코어 SDK

> 본 문서는 특정 기술 구현체 미포함(Core-only) 철학으로 외부 호출을 수반하는 업무 흐름을 표준화하기 위한 Orchestrator 코어 SDK의 목적, 설계, 개발 지침, 테스트/운영 기준을 총망라합니다. 이 문서를 그대로 Jira 에픽/태스크로 전개할 수 있도록 수용 기준(AC), DoD, 리스크까지 포함했습니다.
> 

---

## 1) 배경 & 문제 정의

외부 API(결제, 파일, 써드파티 등)가 개입하는 플로우는 단일 ACID 트랜잭션으로 묶기 어렵습니다. 네트워크 경계, 재시도/중복/타임아웃/크래시 등으로 **업무 원자성**과 **최종 일관성**을 보장하려면 반복적인 설계/구현이 필요합니다. 각 프로젝트가 제각각 구현하면 정합/품질/운영이 분산되어 **개발 속도 저하, 장애 대응 난항**이 발생합니다.

**해결 목표**: 공통의 **수명주기 패턴과 계약(Contracts)**만 코어 SDK에서 강제하고, **구체 기술 선택(JPA/Kafka/SQS/Rabbit/NoSQL 등)은 각 서비스/팀이 어댑터로 구현**하도록 하는 **헥사고날 기반 표준 틀**을 제공합니다.

---

## 2) 목표(Goals) / 비목표(Non-Goals)

**Goals**

- 외부 호출 플로우의 **표준 수명주기** 제공: `S1(수락) → S2(실행) → S3(종결)`
- **강한 계약**(상태머신/멱등/시간/재시도-예산)으로 안전성 보장
- **러너 골격**(Inline Fast-Path, Queue Worker, Finalizer/Reaper) 제공 – 어댑터 미포함
- **SPI/Port** 정의: Store(Outbox/Result/Operation), Message(Bus), 기타 유틸
- *테스트킷(Contract Tests)**으로 어댑터 구현 적합성 자동 검증
- 헥사고날 순수성 유지(도메인/앱은 Port만 의존)

**Non-Goals**

- 특정 DB/브로커 구현 제공(단, 레퍼런스는 별도 모듈로 가능)
- 워크플로 엔진(Temporal 등) 대체
- UI/프론트/웹훅 구현

---

## 3) 아키텍처 철학 (헥사고날)

- **Core**: 도메인-무관 순수 자바. API/계약/정책/러너 골격/테스트킷만 포함.
- **Adapters(사용자 구현)**: 영속/브로커/표현층. Core의 Port를 구현.
- **Bootstrap**: 어떤 어댑터를 쓸지 런타임에 결정/주입.

```
[ Controller ] → Core(Orchestrator) → Ports ⇄ [ Store Adapter ]
                                         ⇄ [ Message Adapter ]
                                         ⇄ [ Result/Finalizer Adapter ]

```

---

## 4) 핵심 개념(계약)

### 4.1 타입 모델

```java
record OpId(UUID value) {}
record BizKey(String value) {}
record IdemKey(String value) {}
record Domain(String value) {}
record EventType(String value) {}
record Payload(String json) {}

record Command(Domain d, EventType t, BizKey k, Payload p, IdemKey idem) {}
record Envelope(OpId opId, Command cmd, long seq) {} // 파티션 내 단조증가(구현 책임)

sealed interface Outcome permits Ok, Retry, Fail {}
record Ok(String providerTxnId, Payload result) implements Outcome {}
record Retry(Duration backoff, String reason, boolean transientErr) implements Outcome {}
record Fail(String reason, int code) implements Outcome {}

enum TerminalStatus { COMPLETED, FAILED }
record OperationHandle(OpId opId, boolean completedFast, String responseBodyOrNull) {}

```

### 4.2 코어 API(3개)

```java
interface Orchestrator { OperationHandle start(Command c, Duration timeBudget); }
interface Executor { Outcome execute(Envelope env, Headers h); }
interface Runtime { void pump(Domain d, Executor ex); } // 큐→실행→종결 루프 골격

```

### 4.3 SPI/Ports (사용자 구현)

```java
interface Store {
  OpId accept(Command c); // S1: operation(IN_PROGRESS)+outbox(PENDING) 동커밋; idem 재입력 시 기존 OpId 반환
  boolean finalize(OpId id, TerminalStatus st, Payload p); // 허용전이만, 멱등
  void writeAhead(Domain d, OpId id, String providerTxnId, Payload p); // 외부 성공 즉시 내구화
  Stream<WARecord> scanWA(Domain d, int n); // Finalizer 입력 소스
}
interface Bus {
  void publish(Domain d, BizKey k, Envelope env, Headers h); // at-least-once, 파티션=BizKey
  void consume(Domain d, java.util.function.Consumer<Envelope> fn);
}
// 보호(Protection) SPI – 구현은 어댑터/사용자 영역
interface CircuitBreaker {
  enum State { CLOSED, OPEN, HALF_OPEN }
  <T> T call(String resourceKey, java.util.concurrent.Callable<T> supplier) throws Exception;
  State state(String resourceKey);
}
interface TimeoutPolicy { java.time.Duration perAttemptTimeout(String resourceKey); }
interface RateLimiter { boolean tryAcquire(String resourceKey, java.time.Duration maxWait); }
interface Bulkhead { <T> T submit(String resourceKey, java.util.concurrent.Callable<T> task) throws Exception; } // 동시성 격리
interface HedgePolicy { boolean shouldHedge(String resourceKey, int attempt, java.time.Duration elapsed); } // 헤징 재요청

// 기타 공통 유틸
interface Clocks { java.time.Instant now(); }
interface RetryBudget { boolean trySpend(BizKey k); }
interface Tracer { void tag(String k, String v); void event(String name, java.util.Map<String,String> f); }

```

### 4.4 불변식(코어가 강제)

- `finalize`: `IN_PROGRESS → COMPLETED|FAILED`만 허용 (역행 금지)
- `(Domain, EventType, BizKey, IdemKey)` 조합은 항상 동일 `OpId`
- `Outcome` 처리 규칙:
    - `Ok` → `writeAhead` → `finalize(COMPLETED)`
    - `Fail` → `finalize(FAILED)`
    - `Retry` → `RetryBudget` 내에서만 재게시; 소진 시 `finalize(FAILED)`
- `timeBudget` 내 종결 신호 미관측 시 반드시 202 경로

---

## 5) 데이터 모델 가이드 (멀티 테이블 권장)

- 도메인별 **독립 Outbox/ResultOutbox 테이블**
    - 예: `outbox_payments`, `result_outbox_payments`, `outbox_media`, …
- 공통 컬럼 원칙: `id(PK)`, `operation_id`, `event_type`, `business_key`, `payload(json)`, `status`, `occurred_at`, 인덱스(`status,id`), (선택) `unique(event_type,business_key)`
- `operations` 테이블: `id`, `status`, `version`, `idempotency_key(UNIQUE)`, `result_payload`, `created_at`, `updated_at`

> DDL과 마이그레이션(Flyway/Liquibase)은 각 도메인/서비스가 소유. Core는 스키마를 강제하지 않음.
> 
> 
> (멀티 테이블 권장)
> 
- 도메인별 **독립 Outbox/ResultOutbox 테이블**
    - 예: `outbox_payments`, `result_outbox_payments`, `outbox_media`, …
- 공통 컬럼 원칙: `id(PK)`, `operation_id`, `event_type`, `business_key`, `payload(json)`, `status`, `occurred_at`, 인덱스(`status,id`), (선택) `unique(event_type,business_key)`
- `operations` 테이블: `id`, `status`, `version`, `idempotency_key(UNIQUE)`, `result_payload`, `created_at`, `updated_at`

> DDL과 마이그레이션(Flyway/Liquibase)은 각 도메인/서비스가 소유. Core는 스키마를 강제하지 않음.
> 

---

## 6) 동작 흐름(표준 시퀀스)

1. **S1 – 수락**: `Store.accept(cmd)` → `OpId` 반환, 커밋 후 `Bus.publish(...)`
2. **Inline Fast-Path**: `timeBudget` 동안 소프트 폴링/이벤트 대기 → 완료 시 200, 아니면 202 + `/status/{opId}`
3. **S2 – 실행**: `Runtime.pump(domain, executor)`
    - 메시지 → (보호 훅 래핑) → `executor.execute(...)`
    - 보호 훅 적용 순서(권장): **Timeout → CircuitBreaker → Bulkhead → RateLimiter → Executor → HedgePolicy(선택)**
    - `Ok`이면 `Store.writeAhead(...)` → `Store.finalize(..., COMPLETED)`
    - `Retry`면 `RetryBudget`과 브로커 정책으로 재게시
    - `Fail`이면 `Store.finalize(..., FAILED)`
4. **S3 – Finalizer/Reaper**: `scanWA(PENDING)` → 미종결건 강제 종결 / 장기 `IN_PROGRESS` 리컨실

---

## 7) 정책(전략 주입) 설계 (확장판)

```java
public interface RetryPolicy { RetryPlan planFor(Throwable t, int attempt); }
public record RetryPlan(boolean retry, Duration backoff, boolean jitter) {}

public interface IdempotencyPolicy {
  String outboundKey(Command cmd);  // 외부 멱등키 헤더용(기본: IdemKey)
  boolean acceptDuplicate(EventType type); // 외부 중복 응답 허용 여부
}

public interface TransitionPolicy { boolean isAllowed(String from, String to); }
public interface TimeBudgetPolicy { Duration inlineWait(); }

// 보호(Protection) 정책 – SPI와 함께 사용
public interface CircuitBreakerPolicy { String resourceKey(Command cmd); }
public interface RateLimitPolicy { String resourceKey(Command cmd); long permitsPerSec(String resourceKey); }
public interface BulkheadPolicy { String resourceKey(Command cmd); int maxConcurrent(String resourceKey); }
public interface HedgeStrategy { boolean enable(Command cmd); Duration hedgeDelay(Command cmd); }
public interface FallbackPolicy { boolean enable(Command cmd); Payload fallback(Command cmd, Throwable cause); }
public interface LoadSheddingPolicy { boolean shed(Command cmd, double systemLoad1m); }

```

- **서킷브레이커**: 코어에는 **인터페이스만(NoOp 기본)**. 구현은 resilience4j/Envoy 등으로 어댑터 주입.
- **타임아웃**: 시도당 제한(Per-attempt). 브로커의 가시성과 별개로 앱 레벨에서 강제.
- **벌크헤드**: 도메인/리소스 키 별 동시성 상한.
- **헤징(Hedging)**: 일정 지연 후 세컨더리 시도(멱등 보장 전제). 외부가 허용될 때만 사용.
- **레이트리미터**: 토큰버킷/고정윈도우 등 구현은 자유. 과열 방지.
- **로드셰딩**: 시스템 부하/큐 길이 기준으로 요청 조기 차단.
- **폴백**: 제한적으로만(읽기성/캐시 가능 케이스). 결제 등 쓰기성 업무엔 기본 비활성.
- **RetryPolicy**: 지수 백오프, Jitter, HTTP/네트워크/429/5xx 기준
- **IdempotencyPolicy**: 외부 멱등키 생성 규칙(기본: `IdemKey`), 허용 중복 범위
- **TransitionPolicy**: 상태 전이 허용/차단 테이블(역행 금지)
- **TimeBudgetPolicy**: 기본 2~5s, 서비스별 오버라이드 가능
- **CircuitBreaker(옵션)**: 외부 장애 시 재시도 억제

---

## 8) 관측 & 운영 가이드

- **로그/트레이스 표준 태그**: `op.id`, `domain`, `event.type`, `biz.key`, `idem.key`, `attempt`, `latency.ms`, `outcome`
- **메트릭**: 성공률, 재시도 횟수, 가시성 연장, DLQ, 처리지연 p95/p99, Finalizer 처리량
- **알람**: DLQ 증가, 장기 `IN_PROGRESS`, 실패율 급증, Finalizer 지연
- **백프레셔**: 컨슈머 병렬도/파티션/visibility/prefetch 조정(브로커별)

---

## 9) 보안/거버넌스

- **시크릿/IAM**: 외부 API 키/권한 최소화, 키 로테이션
- **PII/로깅**: 민감정보 마스킹, Payload 필터링
- **감사**: `OpId`/`providerTxnId` 기준 추적

---

## 10) 테스트 전략

### 10.1 유닛/도메인 테스트

- 상태 전이/멱등/시간 예산 로직 검증

### 10.2 **테스트킷(Contract Tests)** – 어댑터 적합성 필수 통과

시나리오(발췌)

1. **S1 원자성**: Operation/Outbox 동커밋, 롤백 일관성
2. **중복 소비**: 같은 메시지 2회 전달 → 외부 호출 1회 효과(멱등)
3. **장기 실행/재전달**: 가시성 연장/재전달 시 한쪽만 유효 처리
4. **외부 성공→내부 실패**: `writeAhead`만 성공해도 Finalizer가 종결
5. **역행 전이 금지**: COMPLETED 이후 상태 역행 시도 차단
6. **200↔202 분기 정확성**: TimeBudget 경계 테스트
7. **보호 훅 시나리오**: CB OPEN/HALF_OPEN/CLOSED, Timeout, Bulkhead 초과, RL 거부, Hedging 활성 케이스

### 10.3 성능/부하

- 배치 드레인 크기, 컨슈머 병렬도, DB 인덱스/쿼리 플랜 검증
- 보호 정책 영향 평가: 헤징/재시도 조합 시 외부 RPS 상한 검증

---

## 11) 적용 가이드(서비스 입장)

1. **어댑터 구현**: `Store`, `Bus` + (선택) `CircuitBreaker/Timeout/RateLimiter/Bulkhead` 구현체
2. **부트스트랩**: 코어 러너(Inline/Worker/Finalizer)에 구현체 주입
3. **컨트롤러**: `orchestrator.start(cmd, Duration.ofSeconds(3))` 호출
4. **핸들러**(Executor): 외부 API 호출 + 응답을 `Outcome`으로 매핑
5. **운영**: 대시보드/알람 패널 연결, 리컨실 크론 설정

---

## 12) 예시 코드 스니펫 (개발자 진입점)

```java
// Controller
var cmd = new Command(new Domain("payments"), new EventType("PAYMENT.CANCEL.REQUEST"),
                      new BizKey(paymentId), new Payload(json), new IdemKey(idemKey));
OperationHandle h = orchestrator.start(cmd, Duration.ofSeconds(3));
return h.completedFast() ? ok(h) : accepted(h.opId(), "/status/"+h.opId());

// Executor (사용자 구현)
class CancelExecutor implements Executor {
  public Outcome execute(Envelope env, Headers h) {
    try {
      var res = pspClient.cancel(env.cmd().bizKey().value(), h.idemKey());
      return new Ok(res.txnId(), new Payload(res.body()));
    } catch (TransientHttp e) { return new Retry(e.backoff(), e.getMessage(), true); }
      catch (PermanentHttp e) { return new Fail(e.getMessage(), e.code()); }
  }
}

```

---

## 13) Jira 전개 템플릿(요약)

**Epic A – Core API/계약**

- Story: 타입/계약 정의, 상태머신/불변식 구현 → **AC**: Javadoc/테스트 통과
- Story: Inline Runner 구현 → **AC**: timeBudget 분기 테스트
- Story: Worker/Finalizer 러너 구현 → **AC**: Outcome 규칙/Finalizer 경로 테스트
- Story: **Protection SPI(CB/Timeout/RL/Bulkhead/Hedge) 추가** → **AC**: NoOp 기본, 경계 테스트 통과

**Epic B – 테스트킷**

- Story: Contract 시나리오 1~7 구현 → **AC**: 샘플 어댑터 통과

**Epic C – 문서/가이드**

- Story: Quick Start/배선 가이드 → **AC**: 신규 서비스 실습 통과

**Epic D – 레퍼런스 어댑터(선택)**

- Story: In-memory Store/Bus → **AC**: 테스트킷 통과

---

## 14) 리스크 & 대응

- 외부 멱등 미지원 → 조회 API와 업무키로 보정
- 브로커 장기 지연/재전달 → RetryBudget + Finalizer 보정
- 보호 훅 남용(헤징/재시도 폭주) → RateLimiter/RetryBudget/LoadShedding로 상한
- 개발 표준 미준수 → ArchUnit/Contract Tests 게이트

---

## 15) Definition of Done (코어 SDK)

- 공개 API 안정화(세만틱 버저닝 0.x) & 문서화
- Contract Tests 100% 통과(샘플 어댑터 포함)
- 헥사고날 의존성 룰(ArchUnit) 통과
- 예제/Quick Start 제공, 기본 관측 태그 정의

---

## 16) 부록: 구성 파라미터 예시(권장값)

- `timeBudget`: 2–5s
- `retry.maxAttempts`: 6, `backoff`: 200ms → 10s (jitter)
- Finalizer 주기: 1–5s, 배치: 100–500
- 보호: per-attempt timeout 2–5s, CB 실패율>50%/윈도우 20req/30s, RL 서비스별 QPS 상한, Bulkhead 동시성 8–64
- 운영 알람 임계: 장기 `IN_PROGRESS` > 5m, DLQ 증가, 실패율 p95 > X%