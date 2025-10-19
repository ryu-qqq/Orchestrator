# Quick Start 가이드

> 🎯 목표: 30분 내에 첫 Orchestrator Operation 실행 완료하기

이 가이드는 Orchestrator SDK를 처음 사용하는 개발자가 빠르게 시작할 수 있도록 **Hello World 수준의 간단한 예제**를 제공합니다.

---

## 📋 사전 요구사항

- **Java**: 21+
- **Build Tool**: Gradle 8.5+ 또는 Maven 3.9+
- **IDE**: IntelliJ IDEA 또는 Eclipse

---

## 🚀 1단계: 프로젝트 설정

### 1.1 의존성 추가

**Gradle** (`build.gradle`):
```gradle
dependencies {
    implementation 'com.ryuqq:orchestrator-core:1.0.0-SNAPSHOT'

    // 테스트용 In-memory 어댑터 (빠른 시작용)
    implementation 'com.ryuqq:orchestrator-testkit:1.0.0-SNAPSHOT'
}
```

**Maven** (`pom.xml`):
```xml
<dependencies>
    <dependency>
        <groupId>com.ryuqq</groupId>
        <artifactId>orchestrator-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- 테스트용 In-memory 어댑터 (빠른 시작용) -->
    <dependency>
        <groupId>com.ryuqq</groupId>
        <artifactId>orchestrator-testkit</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

> ⚠️ **주의**: `orchestrator-testkit`의 In-memory 어댑터는 **학습 및 테스트 목적**으로만 사용하세요.
> 프로덕션 환경에서는 실제 DB/Message Queue 어댑터를 구현해야 합니다.

---

## 🎬 2단계: Hello World 예제

### 2.1 핵심 개념

Orchestrator는 외부 API 호출을 수반하는 업무를 **3단계 수명주기**로 관리합니다:

```
S1: 수락 (ACCEPT)  →  S2: 실행 (EXECUTE)  →  S3: 종결 (FINALIZE)
   OpId 생성          외부 API 호출        writeAhead → finalize
   멱등 키 확인       Outcome 생성         COMPLETED/FAILED
```

### 2.2 첫 번째 Operation: "결제 취소"

다음은 결제 취소 요청을 처리하는 가장 간단한 예제입니다.

#### 전체 코드 (복사-붙여넣기 가능)

```java
package com.example.quickstart;

import com.ryuqq.orchestrator.core.contract.Command;
import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.*;
import com.ryuqq.orchestrator.core.outcome.*;
import com.ryuqq.orchestrator.core.spi.Store;
import com.ryuqq.orchestrator.core.spi.Bus;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import com.ryuqq.orchestrator.testkit.contract.InMemoryStore;
import com.ryuqq.orchestrator.testkit.contract.InMemoryBus;
import com.ryuqq.orchestrator.testkit.contract.InMemoryIdempotencyManager;

import java.util.UUID;

public class QuickStartExample {

    public static void main(String[] args) {
        // ===== 1. 어댑터 준비 (In-memory 구현) =====
        Store store = new InMemoryStore();
        Bus bus = new InMemoryBus();
        InMemoryIdempotencyManager idempotencyManager = new InMemoryIdempotencyManager();

        System.out.println("✅ In-memory 어댑터 준비 완료\n");

        // ===== 2. Command 생성 =====
        Command command = new Command(
            new Domain("payments"),
            new EventType("PAYMENT.CANCEL.REQUEST"),
            new BizKey("payment-12345"),
            new Payload("{\"reason\": \"customer_request\"}"),
            new IdemKey("idem-abc-123")
        );
        System.out.println("📝 Command 생성: " + command.eventType() + "\n");

        // ===== 3. OpId 생성 (멱등성 보장) =====
        IdempotencyKey idemKey = new IdempotencyKey(command.idemKey().value());
        OpId opId = idempotencyManager.getOrCreate(idemKey);
        System.out.println("🔑 OpId 생성: " + opId + "\n");

        // ===== 4. Envelope 생성 및 저장 =====
        Envelope envelope = new Envelope(opId, command, 1L);
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        System.out.println("📦 Envelope 저장 완료\n");

        // ===== 5. 외부 API 호출 시뮬레이션 =====
        System.out.println("📡 외부 PSP API 호출 중...");
        Outcome outcome = callExternalPaymentAPI(opId);
        System.out.println("✅ 외부 API 응답: " + outcome.getClass().getSimpleName() + "\n");

        // ===== 6. Write-Ahead Log 저장 =====
        store.writeAhead(opId, outcome);
        System.out.println("📝 WAL 저장 완료\n");

        // ===== 7. 상태 종결 (Finalize) =====
        if (outcome instanceof Ok) {
            store.finalize(opId, OperationState.COMPLETED);
            System.out.println("🎉 Operation 완료! 상태: COMPLETED\n");
        } else if (outcome instanceof Fail) {
            store.finalize(opId, OperationState.FAILED);
            System.out.println("❌ Operation 실패! 상태: FAILED\n");
        } else if (outcome instanceof Retry) {
            // 실제 애플리케이션에서는 이 로직이 Bus를 통해 비동기적으로 처리됩니다.
            System.out.println("⏳ Operation 재시도 필요! 상태: IN_PROGRESS (재시도 예정)\n");
        }

        // ===== 8. 최종 상태 확인 =====
        OperationState finalState = store.getState(opId);
        Outcome finalOutcome = store.getWriteAheadOutcome(opId);

        System.out.println("=== 최종 결과 ===");
        System.out.println("OpId: " + opId);
        System.out.println("상태: " + finalState);
        System.out.println("Outcome: " + finalOutcome);

        if (finalOutcome instanceof Ok ok) {
            System.out.println("Provider TxnId: " + ok.providerTxnId());
            System.out.println("Result: " + ok.result().json());
        }
    }

    /**
     * 외부 결제 API 호출 시뮬레이션.
     * 실제로는 RestTemplate, WebClient 등을 사용하여 PSP API를 호출합니다.
     */
    private static Outcome callExternalPaymentAPI(OpId opId) {
        try {
            // 네트워크 지연 시뮬레이션
            Thread.sleep(100);

            // 성공 시나리오 (90% 성공률로 시뮬레이션)
            if (Math.random() < 0.9) {
                return new Ok(
                    "provider-txn-" + UUID.randomUUID().toString().substring(0, 8),
                    new Payload("{\"status\": \"cancelled\", \"refund_amount\": 10000}")
                );
            } else {
                // 실패 시나리오
                return new Fail("PSP_UNAVAILABLE", 503);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 스레드 중단 상태 복원
            // 일시적 오류 (재시도 가능)
            return new Retry(
                java.time.Duration.ofSeconds(5),
                "Network timeout",
                true
            );
        }
    }
}
```

**실행 결과 (성공 시)**:
```
✅ In-memory 어댑터 준비 완료

📝 Command 생성: PAYMENT.CANCEL.REQUEST

🔑 OpId 생성: OpId[value=a1b2c3d4-e5f6-7890-abcd-ef1234567890]

📦 Envelope 저장 완료

📡 외부 PSP API 호출 중...
✅ 외부 API 응답: Ok

📝 WAL 저장 완료

🎉 Operation 완료! 상태: COMPLETED

=== 최종 결과 ===
OpId: OpId[value=a1b2c3d4-e5f6-7890-abcd-ef1234567890]
상태: COMPLETED
Outcome: Ok[providerTxnId=provider-txn-a1b2c3d4, result=Payload[json={"status": "cancelled", "refund_amount": 10000}]]
Provider TxnId: provider-txn-a1b2c3d4
Result: {"status": "cancelled", "refund_amount": 10000}
```

---

## 📊 3단계: 상태 확인 및 로그

### 3.1 Operation 상태 조회

```java
import com.ryuqq.orchestrator.core.statemachine.OperationState;

// Operation 상태 확인 (이전 단계에서 생성된 OpId를 사용합니다)
OpId opId = ...; // 2단계에서 생성한 OpId
OperationState state = store.getState(opId);

System.out.println("📌 현재 상태: " + state);
// 출력: 📌 현재 상태: COMPLETED
```

**가능한 상태**:
- `PENDING`: 수락됨, 실행 대기 중
- `IN_PROGRESS`: 실행 중
- `COMPLETED`: 성공적으로 완료
- `FAILED`: 영구 실패

### 3.2 Write-Ahead Log (WAL) 확인

```java
import com.ryuqq.orchestrator.core.spi.WriteAheadState;

// WAL 엔트리 조회
List<OpId> pendingWAL = store.scanWA(WriteAheadState.PENDING, 10);
System.out.println("📝 미처리 WAL 엔트리: " + pendingWAL.size());

// 특정 Operation의 Outcome 확인
Outcome outcome = store.getWriteAheadOutcome(opId);
System.out.println("🎯 최종 Outcome: " + outcome);
```

---

## 🎉 축하합니다!

첫 Orchestrator Operation을 성공적으로 실행했습니다! 이제 다음 단계로 진행할 수 있습니다:

### 다음 학습 내용

1. **[어댑터 구현 가이드](./02-adapter-implementation.md)**: 실제 DB/Message Queue 어댑터 구현 방법
2. **정책 설정 가이드 (작성 예정)**: Retry, Idempotency, Transition, TimeBudget 설정
3. **운영 가이드 (작성 예정)**: 관측성, 알람, 백프레셔 설정

---

## 🔧 트러블슈팅

### Q1: `ClassNotFoundException: com.ryuqq.orchestrator.core.api.Orchestrator`

**원인**: 의존성이 제대로 추가되지 않음
**해결**:
```bash
# Gradle
./gradlew clean build --refresh-dependencies

# Maven
mvn clean install -U
```

### Q2: `IllegalStateException: Operation already finalized`

**원인**: 같은 OpId로 중복 finalize 시도
**해결**: 멱등 키(`IdemKey`)를 새로 생성하거나, 상태 확인 후 처리
```java
OperationState state = store.getState(opId);
if (!state.isTerminal()) {
    store.finalize(opId, OperationState.COMPLETED);
}
```

### Q3: 비동기 처리 중 응답을 받으려면?

**해결**: 상태 조회 API 구현 (Spring Boot 예시)
```java
@GetMapping("/operations/{opId}")
public ResponseEntity<?> getOperationStatus(@PathVariable String opId) {
    OpId id;
    try {
        id = new OpId(UUID.fromString(opId));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body("Invalid OpId format");
    }

    OperationState state = store.getState(id);

    if (state == OperationState.COMPLETED) {
        Outcome outcome = store.getWriteAheadOutcome(id);
        return ResponseEntity.ok(outcome);
    } else if (state == OperationState.FAILED) {
        return ResponseEntity.status(500).body("Operation failed");
    } else {
        return ResponseEntity.status(202).body("Processing...");
    }
}
```

---

## 📚 참고 자료

- [Orchestrator 설계 문서](../Orchestrator_guide.md)
- [Core API Reference](../../orchestrator-core/README.md)
- [Contract Tests](../../orchestrator-testkit/README.md)

---

**🎓 학습 시간**: 약 15-20분
**✅ 체크리스트**:
- [ ] 의존성 추가 완료
- [ ] Command 생성 및 실행 성공
- [ ] Operation 상태 확인 성공
- [ ] WAL 엔트리 조회 성공
