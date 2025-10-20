# Orchestrator Core SDK

<p align="center">
  <strong>외부 호출을 수반하는 업무 흐름을 표준화하기 위한 헥사고날 기반 코어 SDK</strong>
</p>

<p align="center">
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-핵심-개념">핵심 개념</a> •
  <a href="#-모듈-구조">모듈 구조</a> •
  <a href="#-installation">Installation</a> •
  <a href="#-문서">문서</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-blue?style=flat-square" alt="Java 21">
  <img src="https://img.shields.io/badge/Gradle-8.5-green?style=flat-square" alt="Gradle 8.5">
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="MIT License">
  <img src="https://jitpack.io/v/ryu-qqq/Orchestrator.svg?style=flat-square" alt="JitPack">
</p>

---

## 🎯 개요

Orchestrator는 **외부 API 호출(결제, 파일, 써드파티 등)**이 개입하는 플로우에서 **업무 원자성**과 **최종 일관성**을 보장하는 Core-only 프레임워크입니다.

### 주요 특징

- ✅ **헥사고날 아키텍처**: Core는 구체 기술 구현을 포함하지 않음 (JPA/Kafka/SQS 등 독립)
- ✅ **강한 계약**: 상태머신, 멱등성, 시간 예산, 재시도 예산 기반 안전성 보장
- ✅ **3단계 수명주기**: S1(수락) → S2(실행) → S3(종결)
- ✅ **Contract Tests**: 7가지 시나리오로 어댑터 적합성 자동 검증
- ✅ **Production Ready**: 100% Javadoc 커버리지, 34개 테스트 통과

### 왜 Orchestrator인가?

외부 API가 개입하는 플로우는 단일 ACID 트랜잭션으로 묶기 어렵습니다. 네트워크 경계, 재시도/중복/타임아웃/크래시 등으로 **업무 원자성**과 **최종 일관성**을 보장하려면 반복적인 설계/구현이 필요합니다.

**Orchestrator는 이 문제를 표준화된 패턴과 계약으로 해결합니다.**

```java
// Before: 수동 상태 관리, 재시도 로직, 멱등성 처리
if (checkDuplicate(idemKey)) return existing;
try {
    result = paymentApi.cancel(request);
    saveResult(result);
} catch (Exception e) {
    if (isRetryable(e)) scheduleRetry();
    else markFailed();
}

// After: Orchestrator가 모든 복잡성 처리
Command command = Command.of(domain, eventType, bizKey, idemKey, payload);
OperationHandle handle = orchestrator.start(command, timeBudget);
// → 상태머신, 멱등성, 재시도, WAL 모두 자동 처리
```

---

## 🚀 Quick Start

### 1. JitPack 의존성 추가

#### Gradle
```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Core SDK (필수)
    implementation 'com.github.ryu-qqq.Orchestrator:orchestrator-core:0.1.0'

    // TestKit - 어댑터 구현 및 테스트용 (선택)
    testImplementation 'com.github.ryu-qqq.Orchestrator:orchestrator-testkit:0.1.0'

    // In-Memory 어댑터 - 학습 및 테스트용 (선택)
    implementation 'com.github.ryu-qqq.Orchestrator:orchestrator-adapter-inmemory:0.1.0'
}
```

#### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.ryu-qqq.Orchestrator</groupId>
    <artifactId>orchestrator-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. Hello World 예제

```java
// 1. Command 생성
Command command = Command.of(
    Domain.of("payments"),
    EventType.of("PAYMENT.CANCEL.REQUEST"),
    BizKey.of("payment-12345"),
    IdemKey.of("idem-abc-123"),  // 멱등성 보장
    Payload.of("{\"reason\": \"customer_request\"}")
);

// 2. Operation 시작
OpId opId = orchestrator.start(command, Duration.ofMinutes(5));

// 3. 외부 API 호출 및 Outcome 생성
Outcome outcome = executor.execute(envelope);

// 4. Write-Ahead Log 저장 (장애 복구 보장)
store.writeAhead(opId, outcome);

// 5. 상태 종결
if (outcome instanceof Ok) {
    store.finalize(opId, OperationState.COMPLETED);
}
```

**더 자세한 예제**: [Quick Start 가이드](./docs/guides/01-quick-start.md)

---

## 💡 핵심 개념

### 3단계 수명주기

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│ S1: 수락     │  →   │ S2: 실행     │  →   │ S3: 종결     │
│ (ACCEPT)    │      │ (EXECUTE)   │      │ (FINALIZE)  │
└─────────────┘      └─────────────┘      └─────────────┘
  OpId 생성           외부 API 호출        writeAhead →
  멱등 키 확인        Outcome 생성         finalize
  PENDING 상태        IN_PROGRESS 상태     COMPLETED/FAILED
```

### 상태 전이 규칙

```
PENDING → IN_PROGRESS → COMPLETED (성공)
                     → FAILED (실패)
                     → IN_PROGRESS (재시도)
```

**핵심 제약**:
- 상태 전이는 **허용된 경로만** 가능 (StateTransition 검증)
- 모든 전이는 **멱등적**이어야 함
- 최종 상태(COMPLETED/FAILED)는 **불변**

### Outcome (실행 결과)

```java
sealed interface Outcome permits Ok, Retry, Fail {}

record Ok(String providerTxnId, Payload result) {}     // 성공
record Retry(Duration backoff, String reason) {}       // 재시도
record Fail(String reason, int code) {}                // 실패
```

### Write-Ahead Log (WAL) 패턴

```java
1. writeAhead(opId, outcome) → WAL 엔트리 생성 (PENDING)
2. finalize(opId, state)     → 상태 머신 업데이트 (COMPLETED/FAILED)
3. WAL 엔트리 마킹 (COMPLETED)
```

**장애 복구**:
- **Finalizer**: 중단된 writeAhead → finalize 시퀀스 완료
- **Reaper**: 장기 IN_PROGRESS Operation 재조정

---

## 📦 모듈 구조

Orchestrator는 **멀티모듈 프로젝트**로 구성되어 있으며, 각 모듈은 명확한 역할과 책임을 가집니다.

```
orchestrator/
├── orchestrator-core/              # 핵심 SDK (순수 Java 21, 의존성 없음)
├── orchestrator-testkit/           # Contract Tests (어댑터 적합성 검증)
├── orchestrator-application/       # Application 계층 (Runner 골격)
├── orchestrator-adapter-runner/    # Runner 구현체
└── orchestrator-adapter-inmemory/  # 레퍼런스 어댑터 (학습용)
```

### 모듈별 역할 및 사용법

<table>
<thead>
  <tr>
    <th>모듈</th>
    <th>역할</th>
    <th>의존성</th>
    <th>사용 시나리오</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td><strong>orchestrator-core</strong></td>
    <td>
      • SPI 인터페이스 정의 (Store, Bus, Protection)<br>
      • 타입 모델 (OpId, Command, Outcome)<br>
      • 상태 전이 규칙<br>
      • 순수 Java 21 (외부 의존성 <strong>없음</strong>)
    </td>
    <td>없음 (순수)</td>
    <td>
      • SDK 사용자는 <strong>필수</strong> 의존<br>
      • 모든 어댑터 구현의 기반
    </td>
  </tr>
  <tr>
    <td><strong>orchestrator-testkit</strong></td>
    <td>
      • 7가지 Contract Tests 제공<br>
      &nbsp;&nbsp;1. Atomicity (원자성)<br>
      &nbsp;&nbsp;2. Idempotency (멱등성)<br>
      &nbsp;&nbsp;3. Recovery (복구)<br>
      &nbsp;&nbsp;4. Redelivery (재전송)<br>
      &nbsp;&nbsp;5. StateTransition (상태 전이)<br>
      &nbsp;&nbsp;6. TimeBudget (시간 예산)<br>
      &nbsp;&nbsp;7. ProtectionHook (보호 정책)<br>
      • 어댑터 적합성 자동 검증
    </td>
    <td>core</td>
    <td>
      • 어댑터 구현 시 <strong>필수</strong><br>
      • Contract Test 상속하여 구현 검증<br>
      <br>
      <strong>예시:</strong><br>
      <code>class MyStoreTest extends StoreContractTest {<br>
      &nbsp;&nbsp;@Override<br>
      &nbsp;&nbsp;protected Store createStore() {<br>
      &nbsp;&nbsp;&nbsp;&nbsp;return new MyStore();<br>
      &nbsp;&nbsp;}<br>
      }</code>
    </td>
  </tr>
  <tr>
    <td><strong>orchestrator-application</strong></td>
    <td>
      • Application 계층 로직<br>
      • OperationHandle 생성<br>
      • Runner 골격 정의
    </td>
    <td>core</td>
    <td>
      • 고급 사용자용<br>
      • 커스텀 Runner 구현 시 참조
    </td>
  </tr>
  <tr>
    <td><strong>orchestrator-adapter-runner</strong></td>
    <td>
      • InlineFastPathRunner<br>
      • QueueWorkerRunner<br>
      • Finalizer, Reaper 구현
    </td>
    <td>core, application</td>
    <td>
      • 프로덕션 Runner 사용 시<br>
      • 비동기 큐 처리 필요 시
    </td>
  </tr>
  <tr>
    <td><strong>orchestrator-adapter-inmemory</strong></td>
    <td>
      • InMemoryStore 구현<br>
      • InMemoryBus 구현<br>
      • Resilience4j, Guava 기반 Protection<br>
      • <strong>학습 및 테스트 전용</strong>
    </td>
    <td>
      core, testkit,<br>
      resilience4j, guava
    </td>
    <td>
      • <strong>학습 목적</strong>: Quick Start, 튜토리얼<br>
      • <strong>테스트 목적</strong>: 통합 테스트, 로컬 개발<br>
      • ⚠️ <strong>프로덕션 사용 금지</strong>
    </td>
  </tr>
</tbody>
</table>

### 어댑터 구현 가이드

실제 인프라(DB, Message Queue)로 어댑터를 구현하는 방법:

**1. Store 어댑터 (영속성)**
```java
@Component
public class JpaStore implements Store {
    @Override
    @Transactional
    public void writeAhead(OpId opId, Outcome outcome) {
        // PostgreSQL/MySQL에 WAL 저장
    }

    @Override
    @Transactional
    public void finalize(OpId opId, OperationState state) {
        // Operation 상태 업데이트 + WAL 마킹
    }

    // scanWA(), scanInProgress(), getState() 등 구현
}
```

**2. Bus 어댑터 (메시징)**
```java
@Component
public class SqsBus implements Bus {
    @Override
    public void publish(Envelope envelope, long delayMs) {
        // AWS SQS에 메시지 발행
    }

    @Override
    public List<Envelope> dequeue(int batchSize) {
        // SQS에서 메시지 수신
    }

    // ack(), nack(), publishToDLQ() 구현
}
```

**3. Contract Tests로 검증**
```java
class JpaStoreContractTest extends StoreContractTest {
    @Override
    protected Store createStore() {
        return new JpaStore(/* ... */);
    }
    // 7가지 시나리오 자동 검증
}
```

**더 자세한 구현 가이드**: [어댑터 구현 가이드](./docs/guides/02-adapter-implementation.md)

---

## 🏗️ 아키텍처

### 헥사고날 아키텍처 (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                     │
│  ┌──────────────────────────────────────────────────┐   │
│  │          Orchestrator (Entry Point)              │   │
│  │  start(Command) → OpId                           │   │
│  └──────────────────────────────────────────────────┘   │
│                          ↓                               │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Executor (Business Logic)           │   │
│  │  execute(Envelope) → Outcome                     │   │
│  └──────────────────────────────────────────────────┘   │
│                          ↓                               │
│  ┌──────────────────────────────────────────────────┐   │
│  │          Runtime (Queue Worker, Finalizer)       │   │
│  │  pump() → 큐 → 실행 → 종결 루프                    │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    Core SDK (Ports)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Store SPI    │  │ Bus SPI      │  │ Protection   │  │
│  │ (영속성)      │  │ (메시징)      │  │ SPI          │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│              Adapters (Infrastructure)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ JpaStore     │  │ SqsBus       │  │ Resilience4j │  │
│  │ MongoStore   │  │ KafkaBus     │  │ CircuitBkr   │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**핵심 원칙**:
- Core → Adapter 의존 **절대 금지**
- Adapter → Core 의존만 허용
- 모든 구체 기술은 **어댑터에서만** 구현

---

## 📚 문서

### 시작하기
- **[Quick Start 가이드](./docs/guides/01-quick-start.md)**: 30분 내에 첫 Operation 실행 (Hello World 예제)
- **[어댑터 구현 가이드](./docs/guides/02-adapter-implementation.md)**: Store, Bus, Protection 어댑터 구현 방법

### 설계 문서
- **[Orchestrator 설계 문서](./Orchestrator_guide.md)**: 전체 아키텍처 및 설계 철학
- **[PRD 문서](./docs/prd/)**: Epic별 상세 요구사항 문서

### API Reference
- **[Core API Javadoc](https://jitpack.io/com/github/ryu-qqq/Orchestrator/orchestrator-core/0.1.0/javadoc/)**: 100% Javadoc 커버리지
- **[SPI 계약 문서](./orchestrator-core/src/main/java/com/ryuqq/orchestrator/core/spi/)**: Store, Bus, Protection 인터페이스 상세

---

## 🔧 빌드 및 테스트

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

# 테스트 커버리지 리포트
./gradlew jacocoTestReport
# 결과: build/reports/jacoco/test/html/index.html
```

### 단일 테스트 실행
```bash
# 클래스명으로 실행
./gradlew test --tests "OpIdTest"

# 패턴으로 실행
./gradlew test --tests "*Contract*"
```

---

## 🧪 테스트 전략

### Contract Tests (7가지 시나리오)

Orchestrator TestKit은 어댑터 구현의 적합성을 자동으로 검증하는 **7가지 Contract Tests**를 제공합니다.

| Contract Test | 검증 내용 | 실패 시 의미 |
|--------------|----------|-------------|
| **AtomicityContractTest** | 원자성 보장 (writeAhead + finalize 트랜잭션) | 장애 시 부분 커밋 발생 가능 |
| **IdempotencyContractTest** | 멱등성 보장 (동일 OpId 재입력 안전) | 중복 처리 위험 |
| **RecoveryContractTest** | 복구 메커니즘 (Finalizer 정상 동작) | 크래시 후 데이터 불일치 |
| **RedeliveryContractTest** | 재전송 처리 (Bus nack 후 재처리) | 재시도 실패 |
| **StateTransitionContractTest** | 상태 전이 규칙 준수 | 허용되지 않은 전이 발생 |
| **TimeBudgetContractTest** | 시간 예산 준수 (Reaper 동작) | 장기 IN_PROGRESS 방치 |
| **ProtectionHookContractTest** | 보호 정책 적용 (Circuit Breaker 등) | 장애 전파 |

### 어댑터 테스트 패턴
```java
// 1. Contract Test 상속
class JpaStoreContractTest extends StoreContractTest {

    @Override
    protected Store createStore() {
        return new JpaStore(/* ... */);
    }

    // 7가지 시나리오 자동 실행
}

// 2. 테스트 실행
./gradlew test
// ✅ AtomicityContractTest PASSED
// ✅ IdempotencyContractTest PASSED
// ✅ RecoveryContractTest PASSED
// ... (7개 모두 통과 확인)
```

---

## 🤝 기여 가이드

이 프로젝트는 헥사고날 아키텍처와 강한 계약을 엄격히 준수합니다.

### 기여 전 체크리스트
- [ ] Core 모듈에 외부 의존성 추가 **금지**
- [ ] 모든 public 인터페이스/클래스에 Javadoc 작성
- [ ] Contract Tests 통과 확인
- [ ] `./gradlew build` 성공
- [ ] State 전이 규칙 준수

### PR 프로세스
1. Fork → Feature Branch 생성
2. 코드 작성 + 테스트 추가
3. `./gradlew build` 성공 확인
4. PR 생성 (상세한 설명 포함)

---

## 📊 프로젝트 현황

- **테스트**: 34개 (100% 통과)
- **Javadoc 커버리지**: 100% (40/40 파일)
- **Contract Tests**: 7가지 시나리오
- **빌드 상태**: ✅ BUILD SUCCESSFUL
- **라이센스**: MIT

---

## 🎓 학습 로드맵

### Day 1: SDK 이해
1. [Quick Start 가이드](./docs/guides/01-quick-start.md) 따라하기 (30분)
2. 핵심 개념 숙지 (3단계 수명주기, Outcome, WAL)

### Week 1: 어댑터 구현
1. [어댑터 구현 가이드](./docs/guides/02-adapter-implementation.md) 학습
2. Store 어댑터 구현 (JPA + PostgreSQL)
3. Bus 어댑터 구현 (AWS SQS 또는 Kafka)
4. Contract Tests 통과 확인

### Month 1: 프로덕션 적용
1. Protection 정책 설정 (Circuit Breaker, Rate Limiter)
2. 관측성 설정 (로깅, 메트릭, 알람)
3. 운영 가이드 숙지 (Finalizer, Reaper 운영)

---

## 📞 지원 및 문의

- **이슈 트래커**: [GitHub Issues](https://github.com/ryu-qqq/Orchestrator/issues)
- **Jira 프로젝트**: OR (프로젝트 키)

---

## 📜 라이센스

이 프로젝트는 [MIT License](./LICENSE) 하에 배포됩니다.

```
MIT License

Copyright (c) 2024 Orchestrator Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

---

<p align="center">
  Made with ❤️ by Orchestrator Team
</p>
