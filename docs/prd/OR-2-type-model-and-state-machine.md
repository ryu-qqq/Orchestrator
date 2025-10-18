# OR-2: 타입 모델 및 계약 정의, 상태머신 구현

## 문서 정보

| 항목 | 내용 |
|------|------|
| **작성자** | Claude Code |
| **작성일** | 2025-10-18 |
| **Epic** | OR-1: Core API 및 계약 구현 |
| **Jira Issue** | OR-2 |
| **버전** | 1.0.0 |
| **상태** | Draft |

## 개요

### Epic OR-1과의 연관성

OR-2는 Epic OR-1 "Core API 및 계약 구현"의 핵심 기반 작업으로, Orchestrator SDK의 타입 시스템과 상태 전이 계약을 정의합니다. 이 작업은 다음 Epic OR-1 하위 작업들의 선행 조건입니다:

- **OR-3**: Orchestrator API 인터페이스 정의
- **OR-4**: Executor 및 Runtime 인터페이스 정의
- **OR-5**: Store/Bus/Protection SPI 정의

### 비즈니스 가치

Orchestrator SDK는 외부 API 호출을 포함하는 복잡한 업무 플로우에서 **업무 원자성**과 **최종 일관성**을 보장하는 것을 목표로 합니다. OR-2는 이를 위한 타입 안전성과 상태 불변식의 기초를 제공합니다:

1. **컴파일 타임 안전성**: Sealed interface를 통해 가능한 모든 결과 케이스를 컴파일 타임에 검증
2. **상태 전이 불변식**: 잘못된 상태 전이를 런타임에 차단하여 데이터 일관성 보장
3. **멱등성 보장**: 동일한 업무 요청의 중복 실행을 방지하여 외부 API 중복 호출 차단
4. **추적 가능성**: 모든 Operation에 고유 식별자(OpId)를 부여하여 완전한 감사 추적 가능

### 기술적 목표

1. **Java 21 타입 시스템 활용**: Records, Sealed Classes, Pattern Matching을 통한 현대적 타입 설계
2. **불변성 보장**: 모든 Value Object와 계약 레코드의 불변성 보장
3. **상태머신 구현**: 엄격한 상태 전이 규칙으로 데이터 무결성 보호
4. **멱등성 로직**: 복합 키 기반 중복 요청 감지 및 처리
5. **Zero External Dependencies**: 순수 Java 21로만 구현 (헥사고날 Core 원칙)

---

## 비즈니스 요구사항

### 문제 정의

외부 API(결제, 파일 전송, 써드파티 연동 등)를 포함하는 업무 플로우에서 발생하는 공통 문제:

1. **중복 실행 문제**: 네트워크 타임아웃으로 인한 재시도 시 동일 결제가 2번 실행됨
2. **상태 불일치**: 외부 API 성공 후 DB 저장 실패 시 상태 동기화 실패
3. **추적 불가능**: 어떤 요청이 어떤 외부 호출과 연결되는지 추적 불가
4. **예외 처리 혼란**: 재시도 가능한 오류와 영구 실패의 구분 어려움

### 해결 방안

**타입 모델과 상태머신을 통한 구조적 해결:**

1. **멱등성 보장**: `(Domain, EventType, BizKey, IdemKey)` 복합 키로 동일 요청 감지
   - 예: 주문 ID 123의 결제 요청은 재시도 시 동일 OpId 재사용

2. **상태 전이 불변식**: 명확한 상태 전이 규칙으로 데이터 무결성 보장
   - 예: COMPLETED 상태는 절대 IN_PROGRESS로 되돌릴 수 없음

3. **고유 식별자**: 모든 Operation에 OpId 부여로 완전한 추적성 확보
   - 예: OpId로 외부 API 호출 로그, DB 트랜잭션, 이벤트를 모두 연결

4. **타입 안전한 결과**: Sealed interface Outcome으로 모든 결과 케이스를 컴파일 타임에 강제
   - 예: Ok/Retry/Fail 중 하나만 반환 가능, switch expression에서 누락 불가

### 비즈니스 시나리오 예시

**시나리오 1: 결제 API 중복 호출 방지**

```
1. 사용자가 주문(orderId=123) 결제 요청
2. 시스템이 OpId 생성: "pay-order-123-idem-abc"
3. 외부 결제 API 호출 (타임아웃 발생)
4. 사용자가 재시도 버튼 클릭
5. 시스템이 동일 복합 키 감지 → 기존 OpId 재사용
6. 결제 API에 멱등성 키로 OpId 전달 → 중복 결제 방지
```

**시나리오 2: 상태 전이 불변식 보호**

```
1. 파일 업로드 작업(OpId=file-upload-456) 시작 → IN_PROGRESS
2. 업로드 완료 → COMPLETED
3. 시스템 버그로 "재시작" 로직 실행 시도
4. StateTransition.validate() 호출
5. IllegalStateException 발생: "COMPLETED → IN_PROGRESS 전이 불가"
6. 데이터 무결성 보호 성공
```

---

## 기술 요구사항

### 1. Value Objects 설계

#### 1.1 OpId (Operation Identifier)

**목적**: 각 Operation의 전역적으로 고유한 식별자

**타입**: String (UUID 기반 또는 비즈니스 키 조합)

**불변성 보장**:
- `final class` 선언으로 상속 차단
- `private final String value` 필드
- 생성 후 변경 불가능 (immutable)

**유효성 검증 규칙**:
- null 또는 빈 문자열 불가
- 길이: 1~255자
- 패턴: `^[a-zA-Z0-9\-_]+$` (영숫자, 하이픈, 언더스코어만 허용)

**구현 예시**:
```java
/**
 * Operation의 전역 고유 식별자.
 *
 * <p>OpId는 시스템 내 모든 Operation을 추적하는 데 사용되며,
 * 외부 API 호출 시 멱등성 키로 활용될 수 있습니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class OpId {
    private final String value;

    private OpId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OpId cannot be null or blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("OpId length cannot exceed 255");
        }
        if (!value.matches("^[a-zA-Z0-9\\-_]+$")) {
            throw new IllegalArgumentException("OpId contains invalid characters");
        }
        this.value = value;
    }

    public static OpId of(String value) {
        return new OpId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpId opId = (OpId) o;
        return value.equals(opId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "OpId{" + value + '}';
    }
}
```

#### 1.2 BizKey (Business Key)

**목적**: 업무적으로 의미 있는 엔티티 식별자 (예: 주문번호, 사용자ID)

**타입**: String

**불변성 보장**: OpId와 동일

**유효성 검증 규칙**:
- null 또는 빈 문자열 불가
- 길이: 1~100자
- 패턴: 제약 없음 (업무 도메인에 따라 다양)

**비즈니스 의미**:
- 주문 도메인: 주문번호 (ORDER-20251018-001)
- 결제 도메인: 거래ID (TXN-123456)
- 파일 도메인: 파일명 또는 파일ID

#### 1.3 IdemKey (Idempotency Key)

**목적**: 동일 요청의 중복 실행을 방지하기 위한 클라이언트 제공 키

**타입**: String

**불변성 보장**: OpId와 동일

**유효성 검증 규칙**:
- null 또는 빈 문자열 불가
- 길이: 1~255자
- UUID 형식 권장 (강제 아님)

**비즈니스 의미**:
- 클라이언트가 생성하는 요청별 고유 키
- 네트워크 재시도 시 동일 IdemKey 사용
- 서버는 `(Domain, EventType, BizKey, IdemKey)` 조합으로 중복 요청 감지

**예시**:
```
// 첫 번째 요청
IdemKey: "550e8400-e29b-41d4-a716-446655440000"

// 네트워크 타임아웃 후 재시도
IdemKey: "550e8400-e29b-41d4-a716-446655440000" (동일)
→ 서버가 기존 OpId 반환, 재실행 안 함
```

#### 1.4 Domain

**목적**: 업무 도메인 구분 (예: ORDER, PAYMENT, FILE)

**타입**: String (Enum으로 확장 가능하나 초기엔 String 권장)

**불변성 보장**: OpId와 동일

**유효성 검증 규칙**:
- null 또는 빈 문자열 불가
- 길이: 1~50자
- 패턴: `^[A-Z_]+$` (대문자와 언더스코어만 허용)

**비즈니스 의미**:
- 멱등성 키의 일부로 사용
- 도메인별 Operation 분리 및 추적

**예시**:
```
Domain.of("ORDER")     // 주문 도메인
Domain.of("PAYMENT")   // 결제 도메인
Domain.of("FILE_UPLOAD") // 파일 업로드 도메인
```

#### 1.5 EventType

**목적**: 도메인 내 이벤트 유형 구분 (예: CREATE, UPDATE, DELETE)

**타입**: String

**불변성 보장**: OpId와 동일

**유효성 검증 규칙**:
- null 또는 빈 문자열 불가
- 길이: 1~50자
- 패턴: `^[A-Z_]+$`

**비즈니스 의미**:
- 멱등성 키의 일부로 사용
- 동일 도메인 내 서로 다른 작업 구분

**예시**:
```
EventType.of("CREATE_ORDER")
EventType.of("CANCEL_ORDER")
EventType.of("PROCESS_PAYMENT")
```

#### 1.6 Payload

**목적**: Command에 포함될 업무 데이터의 직렬화된 형태

**타입**: String (JSON, XML, Protobuf 등의 직렬화 형식)

**불변성 보장**: OpId와 동일

**유효성 검증 규칙**:
- null 허용 (빈 Payload 가능)
- 길이 제한 없음 (단, 실무에서는 1MB 권장)

**비즈니스 의미**:
- 실제 업무 로직에 필요한 데이터 전달
- 직렬화 형식은 사용자가 선택 (Core는 String으로만 처리)

**예시**:
```java
// JSON 형식
Payload.of("{\"orderId\":123,\"amount\":50000}")

// 빈 Payload
Payload.of("")

// XML 형식
Payload.of("<order><id>123</id><amount>50000</amount></order>")
```

---

### 2. 계약 레코드 (Command, Envelope)

#### 2.1 Command 레코드

**목적**: Operation 실행을 위한 입력 계약

**불변성 보장**: Java Record 사용 → 자동 불변성 보장

**필드 구성**:
```java
/**
 * Operation 실행 명령.
 *
 * <p>Command는 외부 API 호출을 포함한 업무 플로우를 실행하기 위한
 * 모든 필요 정보를 담고 있습니다.</p>
 *
 * @param domain 업무 도메인 (예: ORDER, PAYMENT)
 * @param eventType 이벤트 유형 (예: CREATE, UPDATE)
 * @param bizKey 업무 엔티티 식별자
 * @param idemKey 멱등성 키 (클라이언트 제공)
 * @param payload 업무 데이터 (직렬화된 형태)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Command(
    Domain domain,
    EventType eventType,
    BizKey bizKey,
    IdemKey idemKey,
    Payload payload
) {
    /**
     * Command 생성자 (Compact Constructor).
     *
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public Command {
        if (domain == null) {
            throw new IllegalArgumentException("domain cannot be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType cannot be null");
        }
        if (bizKey == null) {
            throw new IllegalArgumentException("bizKey cannot be null");
        }
        if (idemKey == null) {
            throw new IllegalArgumentException("idemKey cannot be null");
        }
        // payload는 null 허용
    }
}
```

#### 2.2 Envelope 레코드

**목적**: Command + OpId + 메타데이터를 포함하는 실행 봉투

**불변성 보장**: Java Record 사용

**필드 구성**:
```java
/**
 * Command 실행을 위한 봉투 (Envelope).
 *
 * <p>Envelope은 Command에 OpId와 실행 시각 등의 메타데이터를 추가한
 * 완전한 실행 컨텍스트입니다.</p>
 *
 * @param opId Operation 고유 식별자
 * @param command 실행할 명령
 * @param acceptedAt 요청 수락 시각 (epoch millis)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Envelope(
    OpId opId,
    Command command,
    long acceptedAt
) {
    /**
     * Envelope 생성자 (Compact Constructor).
     *
     * @throws IllegalArgumentException 필수 필드가 null이거나 acceptedAt이 음수인 경우
     */
    public Envelope {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        if (acceptedAt < 0) {
            throw new IllegalArgumentException("acceptedAt must be non-negative");
        }
    }

    /**
     * 현재 시각으로 Envelope 생성.
     *
     * @param opId Operation ID
     * @param command Command
     * @return 생성된 Envelope
     */
    public static Envelope now(OpId opId, Command command) {
        return new Envelope(opId, command, System.currentTimeMillis());
    }
}
```

#### 2.3 직렬화/역직렬화 요구사항

**원칙**: Core 모듈은 직렬화 라이브러리에 의존하지 않음

- Record의 자동 생성 메서드(`opId()`, `command()` 등)를 통한 데이터 접근
- 사용자가 Jackson, Gson 등 원하는 라이브러리로 직렬화
- Adapter 레이어에서 직렬화 책임 분리

**예시 (Jackson 사용 시)**:
```java
// Adapter 레이어에서 구현
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(envelope);
Envelope restored = mapper.readValue(json, Envelope.class);
```

---

### 3. Sealed Interface Outcome

#### 3.1 설계 목표

**타입 안전성**: Operation 실행 결과를 3가지 케이스로 명확히 구분
- **Ok**: 성공
- **Retry**: 재시도 가능한 일시적 실패
- **Fail**: 영구적 실패

**컴파일 타임 검증**: Switch expression에서 모든 케이스 처리 강제

#### 3.2 Outcome Sealed Interface

```java
/**
 * Operation 실행 결과.
 *
 * <p>Outcome은 세 가지 가능한 결과를 나타냅니다:</p>
 * <ul>
 *   <li>{@link Ok}: 성공적으로 완료됨</li>
 *   <li>{@link Retry}: 일시적 실패, 재시도 가능</li>
 *   <li>{@link Fail}: 영구적 실패, 재시도 불가</li>
 * </ul>
 *
 * <p>Sealed interface로 정의되어 모든 케이스를 컴파일 타임에 검증합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public sealed interface Outcome permits Ok, Retry, Fail {

    /**
     * 결과가 성공인지 확인.
     *
     * @return 성공 여부
     */
    default boolean isOk() {
        return this instanceof Ok;
    }

    /**
     * 결과가 재시도 가능한지 확인.
     *
     * @return 재시도 가능 여부
     */
    default boolean isRetry() {
        return this instanceof Retry;
    }

    /**
     * 결과가 영구 실패인지 확인.
     *
     * @return 영구 실패 여부
     */
    default boolean isFail() {
        return this instanceof Fail;
    }
}
```

#### 3.3 Ok 레코드

```java
/**
 * 성공 결과.
 *
 * @param opId Operation ID
 * @param message 성공 메시지 (선택)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Ok(
    OpId opId,
    String message
) implements Outcome {

    public Ok {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
    }

    /**
     * 메시지 없이 성공 결과 생성.
     *
     * @param opId Operation ID
     * @return Ok 인스턴스
     */
    public static Ok of(OpId opId) {
        return new Ok(opId, null);
    }
}
```

#### 3.4 Retry 레코드

```java
/**
 * 재시도 가능한 일시적 실패.
 *
 * @param reason 재시도 사유
 * @param attemptCount 현재까지 시도 횟수
 * @param nextRetryAfterMillis 다음 재시도까지 대기 시간 (밀리초)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Retry(
    String reason,
    int attemptCount,
    long nextRetryAfterMillis
) implements Outcome {

    public Retry {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be null or blank");
        }
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        if (nextRetryAfterMillis < 0) {
            throw new IllegalArgumentException("nextRetryAfterMillis must be non-negative");
        }
    }
}
```

#### 3.5 Fail 레코드

```java
/**
 * 영구적 실패 (재시도 불가).
 *
 * @param errorCode 오류 코드 (예: PAY-001, FILE-404)
 * @param message 오류 메시지
 * @param cause 원인 (선택)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Fail(
    String errorCode,
    String message,
    String cause
) implements Outcome {

    public Fail {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode cannot be null or blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be null or blank");
        }
    }

    /**
     * cause 없이 Fail 생성.
     *
     * @param errorCode 오류 코드
     * @param message 오류 메시지
     * @return Fail 인스턴스
     */
    public static Fail of(String errorCode, String message) {
        return new Fail(errorCode, message, null);
    }
}
```

#### 3.6 Pattern Matching 활용 예시

```java
public void handleOutcome(Outcome outcome) {
    String result = switch (outcome) {
        case Ok ok -> "Success: " + ok.message();
        case Retry retry -> "Retry after " + retry.nextRetryAfterMillis() + "ms";
        case Fail fail -> "Failed: " + fail.errorCode() + " - " + fail.message();
        // 컴파일러가 모든 케이스 처리 강제 (sealed interface)
    };

    System.out.println(result);
}
```

---

### 4. 상태 머신 설계

#### 4.1 상태 정의 (OperationState Enum)

```java
/**
 * Operation의 생명주기 상태.
 *
 * <p>상태 전이 규칙:</p>
 * <ul>
 *   <li>PENDING → IN_PROGRESS (수락)</li>
 *   <li>IN_PROGRESS → COMPLETED (성공)</li>
 *   <li>IN_PROGRESS → FAILED (실패)</li>
 *   <li>역방향 전이 불가 (불변식)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public enum OperationState {
    /**
     * 대기 중 (아직 실행 시작 안 됨).
     */
    PENDING,

    /**
     * 실행 중.
     */
    IN_PROGRESS,

    /**
     * 완료 (성공).
     */
    COMPLETED,

    /**
     * 실패 (영구).
     */
    FAILED;

    /**
     * 종료 상태인지 확인.
     *
     * @return COMPLETED 또는 FAILED인 경우 true
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
```

#### 4.2 상태 전이 다이어그램 (텍스트)

```
PENDING
   │
   ▼ (수락: S1)
IN_PROGRESS
   │
   ├─► COMPLETED (성공: S3-Ok)
   │
   └─► FAILED (실패: S3-Fail)

금지된 전이:
- COMPLETED → IN_PROGRESS ❌
- COMPLETED → PENDING ❌
- FAILED → IN_PROGRESS ❌
- FAILED → PENDING ❌
- COMPLETED ↔ FAILED ❌
```

#### 4.3 StateTransition 클래스

**목적**: 상태 전이 검증 로직 캡슐화

```java
/**
 * 상태 전이 검증 및 실행.
 *
 * <p>이 클래스는 Operation의 상태 전이가 허용된 규칙을 따르는지
 * 검증하고, 불변식을 보장합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class StateTransition {

    /**
     * 상태 전이가 유효한지 검증.
     *
     * @param from 현재 상태
     * @param to 전이할 상태
     * @throws IllegalStateException 유효하지 않은 전이인 경우
     */
    public static void validate(OperationState from, OperationState to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("States cannot be null");
        }

        // 종료 상태에서는 어디로도 전이 불가
        if (from.isTerminal()) {
            throw new IllegalStateException(
                "Cannot transition from terminal state: " + from + " → " + to
            );
        }

        // 허용된 전이만 통과
        boolean valid = switch (from) {
            case PENDING -> to == OperationState.IN_PROGRESS;
            case IN_PROGRESS -> to == OperationState.COMPLETED
                             || to == OperationState.FAILED;
            case COMPLETED, FAILED -> false; // 종료 상태
        };

        if (!valid) {
            throw new IllegalStateException(
                "Invalid state transition: " + from + " → " + to
            );
        }
    }

    /**
     * 상태 전이 실행 (검증 후).
     *
     * @param current 현재 상태
     * @param next 다음 상태
     * @return 전이된 상태 (next)
     * @throws IllegalStateException 유효하지 않은 전이인 경우
     */
    public static OperationState transition(OperationState current, OperationState next) {
        validate(current, next);
        return next;
    }
}
```

#### 4.4 불변식 보장 메커니즘

1. **Enum 사용**: 상태 값의 타입 안전성 보장
2. **Validation 메서드**: 전이 전 반드시 검증
3. **Terminal 상태 체크**: `isTerminal()` 메서드로 종료 상태 빠른 판별
4. **Switch Expression**: 모든 케이스 명시적 처리 강제

---

### 5. 멱등성 보장 로직

#### 5.1 복합 키 설계

**복합 키 구성**: `(Domain, EventType, BizKey, IdemKey)`

**비즈니스 의미**:
- **Domain**: 업무 영역 분리 (ORDER vs PAYMENT)
- **EventType**: 작업 유형 분리 (CREATE vs UPDATE)
- **BizKey**: 엔티티 식별 (주문번호 123)
- **IdemKey**: 클라이언트 요청 구분 (UUID)

**예시**:
```java
/**
 * 멱등성 키.
 *
 * <p>동일한 복합 키는 항상 동일한 OpId와 매핑됩니다.</p>
 *
 * @param domain 도메인
 * @param eventType 이벤트 타입
 * @param bizKey 비즈니스 키
 * @param idemKey 멱등성 키
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record IdempotencyKey(
    Domain domain,
    EventType eventType,
    BizKey bizKey,
    IdemKey idemKey
) {
    public IdempotencyKey {
        if (domain == null || eventType == null
            || bizKey == null || idemKey == null) {
            throw new IllegalArgumentException("All fields are required");
        }
    }

    /**
     * Command에서 IdempotencyKey 추출.
     *
     * @param command Command
     * @return IdempotencyKey
     */
    public static IdempotencyKey from(Command command) {
        return new IdempotencyKey(
            command.domain(),
            command.eventType(),
            command.bizKey(),
            command.idemKey()
        );
    }
}
```

#### 5.2 OpId 매핑 전략

**인터페이스 정의**:
```java
/**
 * 멱등성 관리 SPI.
 *
 * <p>동일한 IdempotencyKey는 항상 동일한 OpId로 매핑되어야 합니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface IdempotencyManager {

    /**
     * IdempotencyKey에 대응하는 OpId를 조회하거나 생성.
     *
     * <p>이미 존재하는 경우 기존 OpId를 반환하고,
     * 없는 경우 새로운 OpId를 생성하여 저장 후 반환합니다.</p>
     *
     * @param key 멱등성 키
     * @return OpId (기존 또는 신규)
     */
    OpId getOrCreate(IdempotencyKey key);

    /**
     * IdempotencyKey에 대응하는 OpId 조회 (조회만).
     *
     * @param key 멱등성 키
     * @return OpId (존재하는 경우), null (없는 경우)
     */
    OpId find(IdempotencyKey key);
}
```

#### 5.3 중복 요청 처리 시나리오

**시나리오 1: 첫 요청**
```
1. 클라이언트: Command(domain=ORDER, eventType=CREATE, bizKey=123, idemKey=abc)
2. IdempotencyKey 생성: (ORDER, CREATE, 123, abc)
3. IdempotencyManager.find() → null (존재하지 않음)
4. OpId 생성: OpId.of("op-12345")
5. 매핑 저장: (ORDER, CREATE, 123, abc) → op-12345
6. Operation 실행
7. 응답: OpId=op-12345
```

**시나리오 2: 중복 요청 (재시도)**
```
1. 클라이언트: Command(domain=ORDER, eventType=CREATE, bizKey=123, idemKey=abc) (동일)
2. IdempotencyKey 생성: (ORDER, CREATE, 123, abc)
3. IdempotencyManager.find() → OpId=op-12345 (기존 존재)
4. Operation 재실행 안 함
5. 응답: OpId=op-12345 (기존 OpId 반환)
```

**시나리오 3: 다른 요청**
```
1. 클라이언트: Command(domain=ORDER, eventType=CREATE, bizKey=123, idemKey=xyz) (idemKey 다름)
2. IdempotencyKey 생성: (ORDER, CREATE, 123, xyz)
3. IdempotencyManager.find() → null (새 요청)
4. OpId 생성: OpId.of("op-67890")
5. 매핑 저장: (ORDER, CREATE, 123, xyz) → op-67890
6. Operation 실행
7. 응답: OpId=op-67890 (새 OpId)
```

#### 5.4 동시성 제어 요구사항

**문제**: 동일 IdempotencyKey로 동시 요청 발생 시 중복 OpId 생성 가능

**해결 방안** (구현은 Adapter 레이어 책임):
1. **Database Unique Constraint**: IdempotencyKey를 Unique Key로 설정
2. **Optimistic Locking**: Version 필드로 동시성 제어
3. **Distributed Lock**: Redis, Zookeeper 등으로 분산 락 구현

**Core의 역할**: 인터페이스만 정의, 구체 구현은 SPI

---

## 도메인 모델 설계

### Package Structure

```
orchestrator-core/
└── src/main/java/
    └── com/ryuqq/orchestrator/core/
        ├── model/
        │   ├── OpId.java                 # Operation ID Value Object
        │   ├── BizKey.java               # Business Key Value Object
        │   ├── IdemKey.java              # Idempotency Key Value Object
        │   ├── Domain.java               # Domain Value Object
        │   ├── EventType.java            # Event Type Value Object
        │   ├── Payload.java              # Payload Value Object
        │   └── IdempotencyKey.java       # Composite Idempotency Key
        │
        ├── contract/
        │   ├── Command.java              # Command Record
        │   └── Envelope.java             # Envelope Record
        │
        ├── outcome/
        │   ├── Outcome.java              # Sealed Interface
        │   ├── Ok.java                   # Success Record
        │   ├── Retry.java                # Retry Record
        │   └── Fail.java                 # Fail Record
        │
        └── statemachine/
            ├── OperationState.java       # State Enum
            └── StateTransition.java      # Transition Logic
```

### 클래스 다이어그램 (텍스트 형식)

```
┌─────────────────────────────────────────────────────────────┐
│                        <<Value Objects>>                     │
├─────────────────────────────────────────────────────────────┤
│ OpId                                                         │
│ ─────                                                        │
│ - value: String                                              │
│ + of(String): OpId                                           │
│ + getValue(): String                                         │
│ + equals(Object): boolean                                    │
│ + hashCode(): int                                            │
└─────────────────────────────────────────────────────────────┘
         ▲
         │ (유사 구조)
         │
┌────────┴────────┬────────────┬───────────┬────────────┐
│                 │            │           │            │
│ BizKey          │ IdemKey    │ Domain    │ EventType  │ Payload
└─────────────────┴────────────┴───────────┴────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     IdempotencyKey (Record)                  │
├─────────────────────────────────────────────────────────────┤
│ + domain: Domain                                             │
│ + eventType: EventType                                       │
│ + bizKey: BizKey                                             │
│ + idemKey: IdemKey                                           │
│ ─────────────────────────────────────────────────────────── │
│ + from(Command): IdempotencyKey                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                       Command (Record)                       │
├─────────────────────────────────────────────────────────────┤
│ + domain: Domain                                             │
│ + eventType: EventType                                       │
│ + bizKey: BizKey                                             │
│ + idemKey: IdemKey                                           │
│ + payload: Payload                                           │
└─────────────────────────────────────────────────────────────┘
                     ▲
                     │ (포함)
                     │
┌─────────────────────────────────────────────────────────────┐
│                      Envelope (Record)                       │
├─────────────────────────────────────────────────────────────┤
│ + opId: OpId                                                 │
│ + command: Command                                           │
│ + acceptedAt: long                                           │
│ ─────────────────────────────────────────────────────────── │
│ + now(OpId, Command): Envelope                               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│              <<sealed interface>> Outcome                    │
├─────────────────────────────────────────────────────────────┤
│ + isOk(): boolean                                            │
│ + isRetry(): boolean                                         │
│ + isFail(): boolean                                          │
└─────────────────────────────────────────────────────────────┘
         ▲
         │ (permits)
         │
┌────────┴────────┬────────────┬────────────┐
│                 │            │            │
│ Ok (Record)     │ Retry      │ Fail       │
│ ─────────       │ (Record)   │ (Record)   │
│ + opId: OpId    │ + reason   │ + errorCode│
│ + message       │ + attempt  │ + message  │
│                 │ + nextRetry│ + cause    │
└─────────────────┴────────────┴────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  OperationState (Enum)                       │
├─────────────────────────────────────────────────────────────┤
│ PENDING                                                      │
│ IN_PROGRESS                                                  │
│ COMPLETED                                                    │
│ FAILED                                                       │
│ ─────────────────────────────────────────────────────────── │
│ + isTerminal(): boolean                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   StateTransition                            │
├─────────────────────────────────────────────────────────────┤
│ + validate(from, to): void                                   │
│ + transition(current, next): OperationState                  │
└─────────────────────────────────────────────────────────────┘
```

---

## Acceptance Criteria 상세화

### 1. Javadoc 작성

#### 체크리스트
- [x] 모든 public 클래스에 `@author Orchestrator Team` 포함
- [x] 모든 public 클래스에 `@since 1.0.0` 포함
- [x] 모든 public 메서드에 `@param`, `@return`, `@throws` 포함
- [x] 비즈니스 의미와 사용 예시 설명 포함
- [x] Package-level Javadoc (`package-info.java`) 작성

#### 검증 방법
```bash
# Javadoc 생성
./gradlew javadoc

# Javadoc 경고 확인
./gradlew javadoc 2>&1 | grep warning

# 기대: 0 warnings
```

### 2. 타입 안전성

#### 체크리스트
- [x] Sealed interface Outcome에 모든 permits 명시
- [x] Switch expression에서 default 없이 모든 케이스 처리
- [x] Pattern matching으로 타입 안전하게 추출

#### 테스트 케이스
```java
@Test
void outcomeExhaustivePatternMatching() {
    Outcome outcome = Ok.of(OpId.of("test"));

    // default 없이 모든 케이스 처리 (컴파일 타임 검증)
    String result = switch (outcome) {
        case Ok ok -> "ok";
        case Retry retry -> "retry";
        case Fail fail -> "fail";
    };

    assertEquals("ok", result);
}
```

### 3. 상태 전이 테스트

#### 체크리스트
- [x] COMPLETED → IN_PROGRESS 시도 시 IllegalStateException
- [x] FAILED → IN_PROGRESS 시도 시 IllegalStateException
- [x] COMPLETED → FAILED 시도 시 IllegalStateException
- [x] FAILED → COMPLETED 시도 시 IllegalStateException
- [x] 정상 전이 (PENDING → IN_PROGRESS → COMPLETED) 성공
- [x] 정상 전이 (PENDING → IN_PROGRESS → FAILED) 성공

#### 테스트 케이스
```java
@Test
void cannotTransitionFromCompletedToInProgress() {
    assertThrows(IllegalStateException.class, () -> {
        StateTransition.validate(
            OperationState.COMPLETED,
            OperationState.IN_PROGRESS
        );
    });
}

@Test
void cannotTransitionFromFailedToInProgress() {
    assertThrows(IllegalStateException.class, () -> {
        StateTransition.validate(
            OperationState.FAILED,
            OperationState.IN_PROGRESS
        );
    });
}

@Test
void normalTransitionPendingToCompleted() {
    OperationState state = OperationState.PENDING;
    state = StateTransition.transition(state, OperationState.IN_PROGRESS);
    state = StateTransition.transition(state, OperationState.COMPLETED);

    assertEquals(OperationState.COMPLETED, state);
}
```

### 4. 멱등성 테스트

#### 체크리스트
- [x] 동일 복합 키로 재요청 시 동일 OpId 반환
- [x] 다른 IdemKey로 요청 시 새 OpId 생성
- [x] 동시 요청 시 하나의 OpId만 생성 (Integration Test)

#### 테스트 케이스 (Unit)
```java
@Test
void sameIdempotencyKeyReturnsSameOpId() {
    IdempotencyManager manager = new InMemoryIdempotencyManager();

    IdempotencyKey key = new IdempotencyKey(
        Domain.of("ORDER"),
        EventType.of("CREATE"),
        BizKey.of("123"),
        IdemKey.of("abc")
    );

    OpId first = manager.getOrCreate(key);
    OpId second = manager.getOrCreate(key);

    assertEquals(first, second);
}

@Test
void differentIdemKeyCreatesNewOpId() {
    IdempotencyManager manager = new InMemoryIdempotencyManager();

    IdempotencyKey key1 = new IdempotencyKey(
        Domain.of("ORDER"),
        EventType.of("CREATE"),
        BizKey.of("123"),
        IdemKey.of("abc")
    );

    IdempotencyKey key2 = new IdempotencyKey(
        Domain.of("ORDER"),
        EventType.of("CREATE"),
        BizKey.of("123"),
        IdemKey.of("xyz") // 다른 IdemKey
    );

    OpId first = manager.getOrCreate(key1);
    OpId second = manager.getOrCreate(key2);

    assertNotEquals(first, second);
}
```

#### 테스트 케이스 (Integration - 동시성)
```java
@Test
void concurrentRequestsCreateOnlyOneOpId() throws Exception {
    IdempotencyManager manager = new InMemoryIdempotencyManager();

    IdempotencyKey key = new IdempotencyKey(
        Domain.of("ORDER"),
        EventType.of("CREATE"),
        BizKey.of("123"),
        IdemKey.of("concurrent")
    );

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    Set<OpId> opIds = ConcurrentHashMap.newKeySet();

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                OpId opId = manager.getOrCreate(key);
                opIds.add(opId);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(5, TimeUnit.SECONDS);
    executor.shutdown();

    // 모든 스레드가 동일한 OpId를 받아야 함
    assertEquals(1, opIds.size());
}
```

### 5. 테스트 커버리지

#### 목표
- Line coverage ≥ 90%
- Branch coverage ≥ 85%
- Mutation testing score ≥ 80% (PIT)

#### 검증 방법
```bash
# JaCoCo 커버리지 리포트
./gradlew test jacocoTestReport

# 리포트 확인
open orchestrator-core/build/reports/jacoco/test/html/index.html

# PIT Mutation Testing (선택)
./gradlew pitest
```

#### 커버리지 제외 대상
- `toString()`, `hashCode()`, `equals()` (Record 자동 생성)
- Private constructors (static factory only)

---

## 코딩 표준 체크리스트

### Zero-Tolerance 규칙

- [x] **Lombok 미사용**: NO `@Data`, `@Builder`, `@Getter`, `@Setter`
  - Pure Java getter/setter 직접 작성
  - Record 사용 시 자동 생성 메서드 활용

- [x] **Getter 체이닝 없음**: Law of Demeter 준수
  - ❌ `envelope.command().domain().getValue()`
  - ✅ `envelope.getDomainValue()` (필요 시 메서드 추가)

- [x] **JPA 관계 어노테이션 없음**: Long FK 전략
  - ❌ `@ManyToOne`, `@OneToMany` 등
  - ✅ `private Long userId;`
  - **참고**: 이 모듈은 Core이므로 JPA 자체가 없음

- [x] **모든 public API에 Javadoc**
  - `@author Orchestrator Team`
  - `@since 1.0.0`
  - `@param`, `@return`, `@throws` 모두 포함

- [x] **Java 21 Records 활용**
  - Command, Envelope, Ok, Retry, Fail 모두 Record

- [x] **Sealed Classes 활용**
  - Outcome sealed interface

- [x] **Pattern Matching 활용**
  - Switch expression으로 Outcome 처리

### 추가 표준

- [x] **불변성 보장**
  - 모든 Value Object: `final class` + `private final fields`
  - 모든 Contract: `record`

- [x] **Null 안전성**
  - 필수 파라미터: Compact constructor에서 null 검증
  - 선택 파라미터: 명시적으로 null 허용 문서화

- [x] **명확한 예외 메시지**
  - `IllegalArgumentException`: "field cannot be null"
  - `IllegalStateException`: "Cannot transition from X to Y"

---

## 구현 가이드

### Value Object 구현 패턴

**템플릿**:
```java
/**
 * [설명].
 *
 * <p>[상세 설명]</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class [ClassName] {
    private final String value;

    private [ClassName](String value) {
        // Validation
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("[ClassName] cannot be null or blank");
        }
        // Additional validations...
        this.value = value;
    }

    /**
     * [ClassName] 생성.
     *
     * @param value 값
     * @return [ClassName] 인스턴스
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static [ClassName] of(String value) {
        return new [ClassName](value);
    }

    /**
     * 값 조회.
     *
     * @return 값
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        [ClassName] that = ([ClassName]) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "[ClassName]{" + value + '}';
    }
}
```

### Sealed Interface 구현 패턴

**인터페이스**:
```java
/**
 * [설명].
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public sealed interface [InterfaceName] permits [Case1], [Case2], [Case3] {
    // Common methods
}
```

**구현 클래스 (Record)**:
```java
/**
 * [케이스 설명].
 *
 * @param field1 필드1 설명
 * @param field2 필드2 설명
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record [Case1](
    Type1 field1,
    Type2 field2
) implements [InterfaceName] {

    public [Case1] {
        // Compact constructor validation
        if (field1 == null) {
            throw new IllegalArgumentException("field1 cannot be null");
        }
    }
}
```

### Record 구현 패턴

**기본**:
```java
/**
 * [설명].
 *
 * @param field1 필드1 설명
 * @param field2 필드2 설명
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record [RecordName](
    Type1 field1,
    Type2 field2
) {

    /**
     * Compact Constructor.
     *
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public [RecordName] {
        if (field1 == null) {
            throw new IllegalArgumentException("field1 cannot be null");
        }
        // Additional validations...
    }

    /**
     * 정적 팩토리 메서드 (선택).
     *
     * @param field1 필드1
     * @return [RecordName] 인스턴스
     */
    public static [RecordName] of(Type1 field1) {
        return new [RecordName](field1, defaultValue);
    }
}
```

---

## 테스트 전략

### Unit Tests

#### Value Object 불변성 테스트
```java
@Test
void valueObjectIsImmutable() {
    OpId opId = OpId.of("test-123");
    String originalValue = opId.getValue();

    // getValue()로 얻은 값을 변경해도 원본 불변
    // (String은 불변이므로 자동 보장)

    assertEquals(originalValue, opId.getValue());
}
```

#### 상태 전이 불변식 테스트
```java
class StateTransitionTest {

    @Test
    void validTransitions() {
        assertDoesNotThrow(() -> {
            StateTransition.validate(PENDING, IN_PROGRESS);
            StateTransition.validate(IN_PROGRESS, COMPLETED);
            StateTransition.validate(IN_PROGRESS, FAILED);
        });
    }

    @Test
    void invalidTransitionsFromCompleted() {
        assertThrows(IllegalStateException.class,
            () -> StateTransition.validate(COMPLETED, IN_PROGRESS));
        assertThrows(IllegalStateException.class,
            () -> StateTransition.validate(COMPLETED, PENDING));
        assertThrows(IllegalStateException.class,
            () -> StateTransition.validate(COMPLETED, FAILED));
    }

    @Test
    void invalidTransitionsFromFailed() {
        assertThrows(IllegalStateException.class,
            () -> StateTransition.validate(FAILED, IN_PROGRESS));
        assertThrows(IllegalStateException.class,
            () -> StateTransition.validate(FAILED, PENDING));
        assertThrows(IllegalStateException.class,
            () -> StateTransition.validate(FAILED, COMPLETED));
    }
}
```

#### 멱등성 로직 테스트
```java
class IdempotencyManagerTest {

    private IdempotencyManager manager;

    @BeforeEach
    void setUp() {
        manager = new InMemoryIdempotencyManager();
    }

    @Test
    void getOrCreateReturnsSameOpIdForSameKey() {
        IdempotencyKey key = createKey("ORDER", "CREATE", "123", "abc");

        OpId first = manager.getOrCreate(key);
        OpId second = manager.getOrCreate(key);

        assertNotNull(first);
        assertEquals(first, second);
    }

    @Test
    void findReturnsNullWhenKeyDoesNotExist() {
        IdempotencyKey key = createKey("ORDER", "CREATE", "999", "xyz");

        OpId result = manager.find(key);

        assertNull(result);
    }

    @Test
    void differentKeysProduceDifferentOpIds() {
        IdempotencyKey key1 = createKey("ORDER", "CREATE", "123", "abc");
        IdempotencyKey key2 = createKey("ORDER", "CREATE", "123", "def");

        OpId opId1 = manager.getOrCreate(key1);
        OpId opId2 = manager.getOrCreate(key2);

        assertNotEquals(opId1, opId2);
    }

    private IdempotencyKey createKey(String domain, String eventType,
                                      String bizKey, String idemKey) {
        return new IdempotencyKey(
            Domain.of(domain),
            EventType.of(eventType),
            BizKey.of(bizKey),
            IdemKey.of(idemKey)
        );
    }
}
```

#### Sealed Interface Exhaustiveness 테스트
```java
@Test
void outcomePatternMatchingIsExhaustive() {
    List<Outcome> outcomes = List.of(
        Ok.of(OpId.of("ok-1")),
        new Retry("timeout", 1, 1000),
        Fail.of("ERR-001", "error")
    );

    for (Outcome outcome : outcomes) {
        String type = switch (outcome) {
            case Ok ok -> "ok";
            case Retry retry -> "retry";
            case Fail fail -> "fail";
            // No default needed (sealed interface)
        };

        assertNotNull(type);
    }
}
```

### Integration Tests

#### 멱등성 동시성 테스트
```java
@Test
void concurrentGetOrCreateProducesSingleOpId() throws Exception {
    IdempotencyManager manager = new InMemoryIdempotencyManager();
    IdempotencyKey key = createKey("ORDER", "CREATE", "concurrent", "test");

    int threads = 20;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);

    Set<OpId> results = ConcurrentHashMap.newKeySet();

    for (int i = 0; i < threads; i++) {
        executor.submit(() -> {
            try {
                startLatch.await(); // 동시 시작
                OpId opId = manager.getOrCreate(key);
                results.add(opId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });
    }

    startLatch.countDown(); // 모든 스레드 동시 시작
    doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertEquals(1, results.size(), "모든 스레드가 동일한 OpId를 받아야 함");
}
```

#### 상태 전이 시나리오 테스트
```java
@Test
void operationLifecycleScenario() {
    // Given: 새 Operation
    OperationState state = OperationState.PENDING;

    // When: 실행 시작
    state = StateTransition.transition(state, OperationState.IN_PROGRESS);

    // Then: IN_PROGRESS
    assertEquals(OperationState.IN_PROGRESS, state);

    // When: 성공 완료
    state = StateTransition.transition(state, OperationState.COMPLETED);

    // Then: COMPLETED
    assertEquals(OperationState.COMPLETED, state);
    assertTrue(state.isTerminal());

    // When: COMPLETED에서 다시 전이 시도
    OperationState finalState = state;
    assertThrows(IllegalStateException.class,
        () -> StateTransition.transition(finalState, OperationState.IN_PROGRESS));
}
```

---

## 참고 자료

### DDD Value Object 패턴
- **Implementing Domain-Driven Design** (Vaughn Vernon)
- **Domain-Driven Design** (Eric Evans)

### Java 21 Sealed Classes
- [JEP 409: Sealed Classes](https://openjdk.org/jeps/409)
- [Oracle Java 21 Documentation - Sealed Classes](https://docs.oracle.com/en/java/javase/21/language/sealed-classes-and-interfaces.html)

### Hexagonal Architecture in Spring
- **Get Your Hands Dirty on Clean Architecture** (Tom Hombergs)
- [Hexagonal Architecture with Java and Spring](https://reflectoring.io/spring-hexagonal/)

### Law of Demeter
- [Law of Demeter - Wikipedia](https://en.wikipedia.org/wiki/Law_of_Demeter)
- **Object-Oriented Software Construction** (Bertrand Meyer)

### 멱등성 (Idempotency)
- [RESTful API Design: Idempotency](https://stripe.com/docs/api/idempotent_requests)
- [Designing Data-Intensive Applications](https://dataintensive.net/) (Martin Kleppmann) - Chapter 8

### 상태 머신 (State Machine)
- **Implementing Domain-Driven Design** - Chapter 6 (Aggregate State Transitions)
- [Spring State Machine Documentation](https://spring.io/projects/spring-statemachine)

---

## 구현 순서 권장사항

### Phase 1: Value Objects (1일차)
1. **OpId** 구현 및 테스트
2. **BizKey, IdemKey, Domain, EventType, Payload** 구현 (OpId 템플릿 재사용)
3. **IdempotencyKey** Record 구현
4. 모든 Value Object 유닛 테스트 작성

**검증**: `./gradlew :orchestrator-core:test --tests "*ValueObject*"`

### Phase 2: Sealed Interface Outcome (2일차)
1. **Outcome** sealed interface 정의
2. **Ok, Retry, Fail** Record 구현
3. Pattern matching 테스트 작성
4. Exhaustiveness 테스트 작성

**검증**: `./gradlew :orchestrator-core:test --tests "*Outcome*"`

### Phase 3: Command & Envelope (3일차)
1. **Command** Record 구현
2. **Envelope** Record 구현
3. Compact constructor validation 테스트
4. Static factory method 테스트

**검증**: `./gradlew :orchestrator-core:test --tests "*Command*" --tests "*Envelope*"`

### Phase 4: 상태 머신 (4일차)
1. **OperationState** Enum 구현
2. **StateTransition** 클래스 구현
3. 정상 전이 테스트
4. 불법 전이 테스트 (모든 조합)

**검증**: `./gradlew :orchestrator-core:test --tests "*State*"`

### Phase 5: 멱등성 로직 (5일차)
1. **IdempotencyManager** 인터페이스 정의 (SPI)
2. **InMemoryIdempotencyManager** 참조 구현 (Testkit 또는 Adapter-InMemory)
3. 멱등성 유닛 테스트
4. 동시성 Integration 테스트

**검증**: `./gradlew :orchestrator-core:test --tests "*Idempotency*"`

### Phase 6: Javadoc & Coverage (6일차)
1. 모든 클래스 Javadoc 작성
2. Package-level Javadoc (`package-info.java`) 작성
3. JaCoCo 커버리지 확인 및 보완
4. Mutation testing (PIT) 실행 및 분석

**검증**:
```bash
./gradlew :orchestrator-core:javadoc
./gradlew :orchestrator-core:test jacocoTestReport
open orchestrator-core/build/reports/jacoco/test/html/index.html
```

### Phase 7: 최종 검토 (7일차)
1. 코딩 표준 체크리스트 검토
2. Acceptance Criteria 전체 검증
3. 문서 업데이트 (README, 이 PRD 등)
4. Code Review 준비

---

## 추가 고려사항

### 확장성

1. **Domain/EventType Enum 전환**
   - 초기: String 기반으로 유연성 확보
   - 추후: 도메인이 안정화되면 Enum으로 전환 가능

2. **Payload 타입 안전성**
   - 초기: String (직렬화된 형태)
   - 추후: Generic 도입 고려 (`Payload<T>`)

3. **Outcome 확장**
   - 초기: Ok, Retry, Fail
   - 추후: Pending, Cancelled 등 추가 가능 (sealed interface 확장 용이)

### 성능 최적화

1. **Value Object 캐싱**
   - 자주 사용되는 Domain, EventType은 상수로 미리 생성 가능
   ```java
   public static final Domain ORDER_DOMAIN = Domain.of("ORDER");
   ```

2. **IdempotencyKey hashCode 최적화**
   - Record의 기본 hashCode는 모든 필드 사용
   - 필요 시 커스텀 hashCode 구현 고려

### 보안

1. **OpId 예측 불가능성**
   - UUID v4 사용 권장 (랜덤)
   - 순차 번호 사용 시 보안 리스크 고려

2. **Payload 민감 정보**
   - Payload에 민감 정보 포함 시 암호화 책임은 사용자
   - Core는 암호화 기능 제공하지 않음 (Adapter 레이어 책임)

---

## 문서 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|------|------|----------|--------|
| 1.0.0 | 2025-10-18 | 최초 작성 | Claude Code |

---

**문서 종료**
