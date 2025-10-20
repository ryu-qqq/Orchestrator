# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 프로젝트 개요

**Orchestrator Core SDK**는 외부 API 호출(결제, 파일, 써드파티 등)을 수반하는 업무 플로우에서 **업무 원자성**과 **최종 일관성**을 보장하는 헥사고날 기반 Core-only 프레임워크입니다.

### 핵심 철학
- **헥사고날 아키텍처**: Core는 순수 Java로 구체 기술(JPA/Kafka/SQS 등) 미포함
- **Port & Adapter 패턴**: SPI 계약만 정의, 구현은 어댑터에서 담당
- **강한 계약**: 상태머신, 멱등성, 시간/재시도 예산 기반 안전성 보장
- **3단계 수명주기**: S1(수락) → S2(실행) → S3(종결)

---

## 빌드 및 테스트

### 빌드
```bash
./gradlew build
```

### 테스트
```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :orchestrator-core:test
./gradlew :orchestrator-testkit:test

# 커버리지 리포트 생성
./gradlew jacocoTestReport
# 결과: build/reports/jacoco/test/html/index.html
```

### 단일 테스트 실행
```bash
# JUnit 5 패턴 기반
./gradlew test --tests "클래스명"
./gradlew test --tests "*패턴*"

# 예시
./gradlew :orchestrator-core:test --tests "OpIdTest"
./gradlew :orchestrator-testkit:test --tests "*Contract*"
```

---

## 프로젝트 구조 (멀티모듈)

```
orchestrator/
├── orchestrator-core/              # 핵심 SDK (순수 Java, 의존성 없음)
│   ├── api/                        # 공개 API (Orchestrator, Executor, Runtime)
│   ├── model/                      # 타입 모델 (OpId, Command, Envelope 등)
│   ├── outcome/                    # 실행 결과 (Ok, Retry, Fail)
│   ├── contract/                   # 계약 타입
│   ├── spi/                        # SPI 인터페이스 (Store, Bus, Protection)
│   ├── statemachine/               # 상태 전이 규칙
│   └── protection/                 # 보호 정책 (Circuit Breaker, Rate Limiter 등)
│
├── orchestrator-application/       # 애플리케이션 계층 (Runner 골격)
│
├── orchestrator-adapter-runner/    # Runner 구현체
│
├── orchestrator-testkit/           # Contract Tests (어댑터 적합성 검증)
│   └── contracts/                  # 7가지 시나리오 테스트
│
└── orchestrator-adapter-inmemory/  # 레퍼런스 어댑터 (In-Memory 구현)
    ├── store/                      # InMemoryStore 구현
    └── bus/                        # InMemoryBus 구현
```

### 모듈 간 의존성 규칙
- **orchestrator-core**: 외부 의존성 **절대 금지** (순수 Java 21만 사용)
- **orchestrator-testkit**: core만 의존
- **orchestrator-adapter-***: core + testkit 의존 허용
- **orchestrator-application**: core 의존

---

## 핵심 아키텍처 개념

### 1. 타입 모델 (Value Objects - Java Records)
모든 도메인 타입은 **불변 Records**로 정의됩니다:

```java
record OpId(UUID value) {}           // Operation ID
record BizKey(String value) {}       // 비즈니스 키 (파티션 키)
record IdemKey(String value) {}      // 멱등성 키
record Domain(String value) {}       // 도메인 (예: "payment", "order")
record EventType(String value) {}    // 이벤트 타입
record Payload(String json) {}       // JSON 페이로드

record Command(Domain d, EventType t, BizKey k, Payload p, IdemKey idem) {}
record Envelope(OpId opId, Command cmd, long seq) {} // 순서 보장용
```

### 2. 핵심 API (3개 인터페이스)

```java
// 1. Orchestrator: 업무 시작점 (S1 - 수락)
interface Orchestrator {
    OperationHandle start(Command c, Duration timeBudget);
}

// 2. Executor: 실제 비즈니스 로직 실행 (S2 - 실행)
interface Executor {
    Outcome execute(Envelope env, Headers h);
}

// 3. Runtime: 큐→실행→종결 루프 골격 (S3 - 종결)
interface Runtime {
    void pump(Domain d, Executor ex);
}
```

### 3. SPI (Service Provider Interface) - 어댑터 구현 필수

```java
// Store SPI: 영속성 계약
interface Store {
    OpId accept(Command c);           // S1: 멱등 수락
    boolean finalize(OpId id, ...);   // S3: 종결 (COMPLETED/FAILED)
    void writeAhead(...);             // WAL 기록
}

// Bus SPI: 메시징 계약
interface Bus {
    void publish(...);                // at-least-once 발행
    void consume(...);                // 구독
}

// Protection SPI: 보호 정책 (Circuit Breaker, Rate Limiter 등)
interface CircuitBreaker { ... }
interface RateLimiter { ... }
```

### 4. 상태 전이 규칙 (State Machine)

```
IN_PROGRESS → EXECUTING → COMPLETED (성공)
            → EXECUTING → IN_PROGRESS (재시도)
            → FAILED (실패)
```

**핵심 제약**:
- 상태 전이는 **허용된 경로만** 가능 (StateTransition 검증)
- 모든 전이는 **멱등적**이어야 함
- 최종 상태(COMPLETED/FAILED)는 **불변**

---

## 코딩 규칙 (Zero-Tolerance)

### 1. Lombok 절대 금지
- ❌ `@Data`, `@Builder`, `@Getter`, `@Setter` 등 모든 Lombok 어노테이션
- ✅ Java 21 Records 또는 Plain Java 사용

### 2. Records 사용 원칙
- 모든 Value Object는 **불변 Records**로 정의
- Validation은 **Compact Constructor**에서 수행
- 예시:
```java
public record OpId(UUID value) {
    public OpId {
        if (value == null) {
            throw new IllegalArgumentException("OpId cannot be null");
        }
    }
}
```

### 3. SPI 구현 규칙
- Core 모듈은 **구체 기술 의존성 절대 금지**
- SPI 구현은 **반드시 어댑터 모듈**에서
- NoOp 구현 (기본값)은 `protection/noop/` 패키지에 제공

### 4. 상태 전이 검증
- 모든 상태 변경은 `StateTransition.isValid()` 검증 필수
- 허용되지 않은 전이 시 `IllegalStateException` 발생

### 5. 멱등성 보장
- 모든 write 연산은 **멱등적**이어야 함
- `IdemKey` 기반 중복 요청 처리
- Store 구현에서 멱등성 검사 필수

### 6. Javadoc 필수
- 모든 **public 인터페이스/클래스**에 Javadoc 필수
- SPI 계약은 특히 상세히 작성 (구현 가이드 포함)

---

## 테스트 전략

### 1. Contract Tests (orchestrator-testkit)
어댑터 구현 적합성을 자동 검증하는 **7가지 시나리오**:

1. 멱등성 수락 (동일 IdemKey 재입력)
2. 정상 종결 (COMPLETED)
3. 실패 종결 (FAILED)
4. 재시도 플로우
5. WAL 스캔
6. 시간 예산 초과
7. 상태 전이 검증

### 2. 어댑터 테스트 패턴
```java
// 어댑터 구현 후 Contract Tests 상속
class MyStoreAdapterTest extends StoreContractTest {
    @Override
    protected Store createStore() {
        return new MyStoreAdapter();
    }
}
```

### 3. 단위 테스트 기본 스택
- **JUnit 5**: Platform & Jupiter
- **AssertJ**: Fluent assertions
- **Mockito**: Mocking framework

---

## 중요 제약사항

### 1. Core 모듈 순수성
- `orchestrator-core`는 **순수 Java 21만 사용**
- Spring, JPA, Kafka 등 **어떤 프레임워크도 의존 금지**
- 외부 라이브러리는 `org.jetbrains:annotations` (compileOnly) 만 허용

### 2. 헥사고날 경계 준수
- Core → Adapter 의존 **절대 금지**
- Adapter → Core 의존만 허용
- 모든 구체 기술은 **어댑터에서만** 구현

### 3. 버전 관리
- Java: **21** (toolchain 고정)
- Gradle: **8.5**
- JUnit: **5.10.1**

---

## 참고 문서

### 설계 문서
- [Orchestrator 전체 설계 가이드](../Orchestrator_guide.md)
- [PRD 문서](../docs/prd/) - Epic별 상세 요구사항

### 가이드
- [Quick Start 가이드](../docs/guides/01-quick-start.md) - 30분 내 첫 Operation 실행
- [어댑터 구현 가이드](../docs/guides/02-adapter-implementation.md) - Store/Bus 어댑터 구현

### Jira 프로젝트
- **프로젝트 키**: OR
- **주요 에픽**:
  - OR-1: Core API 및 계약 구현
  - OR-6: 테스트킷 (Contract Tests) 구현
  - OR-9: 문서 및 개발자 가이드
  - OR-12: 레퍼런스 어댑터 구현

---

## 개발 워크플로우

### 1. 새로운 SPI 추가 시
```bash
# 1. Core에 인터페이스 정의 (orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/spi/)
# 2. Contract Test 작성 (orchestrator-testkit/src/main/java/com/ryuqq/orchestrator/testkit/contracts/)
# 3. NoOp 구현 제공 (선택적)
# 4. 레퍼런스 어댑터 구현 (orchestrator-adapter-inmemory/)
# 5. Contract Test 통과 확인
./gradlew :orchestrator-adapter-inmemory:test
```

### 2. 어댑터 구현 시
```bash
# 1. Core 의존성 추가
# 2. SPI 인터페이스 구현
# 3. Contract Test 상속 및 실행
# 4. 통합 테스트 작성
```

### 3. PR 전 체크리스트
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 모두 통과
- [ ] `./gradlew jacocoTestReport` - 커버리지 확인
- [ ] Javadoc 누락 없음 (public API/SPI)
- [ ] Lombok 미사용 확인
- [ ] 상태 전이 규칙 준수

---

**✅ 이 프로젝트의 모든 코드는 위 표준을 따라야 합니다.**
