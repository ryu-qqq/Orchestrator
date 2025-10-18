# Application UseCase 생성 커맨드

당신은 헥사고날 아키텍처의 Application UseCase를 생성하는 전문가입니다.

## 🎯 컨텍스트 주입 (자동)

---

## 🎯 APPLICATION 레이어 규칙 (자동 주입됨)

### ❌ 금지 규칙 (Zero-Tolerance)

- **`@Transactional` 내 외부 API 호출 금지**: RestTemplate, WebClient, FeignClient 호출은 트랜잭션 밖에서
- **Private/Final 메서드에 `@Transactional` 금지**: Spring 프록시 제약사항
- **같은 클래스 내부 호출 금지**: `this.method()`는 트랜잭션이 작동하지 않음
- **Domain 객체 직접 반환 금지**: UseCase는 DTO만 반환
- **Lombok 금지**: UseCase에서도 Plain Java 사용

### ✅ 필수 규칙

- **트랜잭션은 짧게**: 트랜잭션 내에서는 DB 작업만, 외부 호출은 트랜잭션 밖에서
- **DTO 변환**: Input/Output DTO 사용, `from()` / `toXxx()` 매퍼 패턴
- **Assembler 패턴**: Domain ↔ DTO 변환은 별도 Assembler로 분리
- **Javadoc 필수**: `@author`, `@since` 포함
- **UseCase 네이밍**: Command/Query 구분 (`RegisterOrderUseCase`, `GetOrderQuery`)

### 📋 상세 문서

- [Transaction Boundaries](docs/coding_convention/03-application-layer/transaction-management/01_transaction-boundaries.md)
- [Spring Proxy Limitations](docs/coding_convention/03-application-layer/transaction-management/02_spring-proxy-limitations.md)
- [DTO Patterns](docs/coding_convention/03-application-layer/dto-patterns/01_request-response-dto.md)
- [Assembler Pattern](docs/coding_convention/03-application-layer/assembler-pattern/01_assembler-responsibility.md)

**이 규칙들은 실시간으로 검증됩니다.**

---

## 📋 작업 지시

### 1. 입력 분석

- **UseCase 이름**: 첫 번째 인자 (예: `PlaceOrder`, `CancelOrder`, `GetOrderDetails`)
- **PRD 파일** (선택): 두 번째 인자로 PRD 문서 경로

### 2. 생성할 파일

다음 파일을 `application/src/main/java/com/company/template/application/` 경로에 생성:

```
application/src/main/java/com/company/template/application/
├── usecase/
│   ├── {UseCaseName}UseCase.java      # Command UseCase
│   └── {UseCaseName}Query.java        # Query UseCase (읽기 전용)
├── port/in/
│   ├── {UseCaseName}Command.java      # Input DTO (record)
│   └── {UseCaseName}Result.java       # Output DTO (record)
└── assembler/
    └── {Aggregate}Assembler.java      # Domain ↔ DTO 변환
```

### 3. 필수 준수 규칙

#### Command UseCase 패턴 (쓰기 작업)

```java
package com.company.template.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {UseCaseName} UseCase
 *
 * <p>{간단한 설명}</p>
 *
 * <p><strong>트랜잭션 관리:</strong></p>
 * <ul>
 *   <li>✅ 트랜잭션 내: Domain 로직 + DB 저장</li>
 *   <li>❌ 트랜잭션 밖: 외부 API 호출, 이벤트 발행</li>
 * </ul>
 *
 * @author Claude
 * @since {현재 날짜}
 */
@Service
public class {UseCaseName}UseCase {

    private final {Aggregate}Repository repository;
    private final {Aggregate}Assembler assembler;
    // 외부 Port는 생성자 주입
    private final ExternalApiPort externalApiPort;  // ← 트랜잭션 밖에서 호출

    /**
     * 생성자
     *
     * @param repository {Aggregate} Repository
     * @param assembler {Aggregate} Assembler
     * @param externalApiPort 외부 API Port
     * @author Claude
     * @since {현재 날짜}
     */
    public {UseCaseName}UseCase(
        {Aggregate}Repository repository,
        {Aggregate}Assembler assembler,
        ExternalApiPort externalApiPort
    ) {
        this.repository = repository;
        this.assembler = assembler;
        this.externalApiPort = externalApiPort;
    }

    /**
     * {UseCase 설명}
     *
     * <p><strong>트랜잭션 경계:</strong></p>
     * <ol>
     *   <li>외부 API 호출 (트랜잭션 밖)</li>
     *   <li>트랜잭션 시작</li>
     *   <li>Domain 로직 실행</li>
     *   <li>DB 저장</li>
     *   <li>트랜잭션 커밋</li>
     *   <li>이벤트 발행 (트랜잭션 밖)</li>
     * </ol>
     *
     * @param command Input Command
     * @return Output Result
     * @author Claude
     * @since {현재 날짜}
     */
    public {UseCaseName}Result execute({UseCaseName}Command command) {
        // 1. 외부 API 호출 (트랜잭션 밖)
        ExternalData externalData = externalApiPort.fetchData(command.externalId());

        // 2. 트랜잭션 내 Domain 로직 실행
        {Aggregate} aggregate = executeInTransaction(command, externalData);

        // 3. DTO 변환 및 반환
        return assembler.toResult(aggregate);
    }

    /**
     * 트랜잭션 내 Domain 로직 실행
     *
     * <p>⚠️ 중요: 외부 API 호출 금지</p>
     *
     * @param command Input Command
     * @param externalData 외부 데이터
     * @return {Aggregate}
     * @author Claude
     * @since {현재 날짜}
     */
    @Transactional
    protected {Aggregate} executeInTransaction(
        {UseCaseName}Command command,
        ExternalData externalData
    ) {
        // Domain 로직
        {Aggregate} aggregate = assembler.toDomain(command);
        aggregate.doBusinessAction(externalData);

        // DB 저장
        return repository.save(aggregate);
    }
}
```

#### Input Command (record)

```java
package com.company.template.application.port.in;

/**
 * {UseCaseName} Command (Input DTO)
 *
 * @param aggregateId {Aggregate} ID
 * @param externalId 외부 시스템 ID
 * @author Claude
 * @since {현재 날짜}
 */
public record {UseCaseName}Command(
    String aggregateId,
    String externalId
) {
    public {UseCaseName}Command {
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("Aggregate ID는 필수입니다");
        }
    }
}
```

#### Output Result (record)

```java
package com.company.template.application.port.in;

/**
 * {UseCaseName} Result (Output DTO)
 *
 * @param aggregateId {Aggregate} ID
 * @param status 상태
 * @param message 메시지
 * @author Claude
 * @since {현재 날짜}
 */
public record {UseCaseName}Result(
    String aggregateId,
    String status,
    String message
) {}
```

#### Assembler 패턴

```java
package com.company.template.application.assembler;

import org.springframework.stereotype.Component;

/**
 * {Aggregate} Assembler
 *
 * <p>Domain ↔ DTO 변환 책임</p>
 *
 * @author Claude
 * @since {현재 날짜}
 */
@Component
public class {Aggregate}Assembler {

    /**
     * Command → Domain 변환
     *
     * @param command Input Command
     * @return {Aggregate}
     * @author Claude
     * @since {현재 날짜}
     */
    public {Aggregate} toDomain({UseCaseName}Command command) {
        {Aggregate}Id id = new {Aggregate}Id(command.aggregateId());
        return new {Aggregate}(id, command.customerId());
    }

    /**
     * Domain → Result 변환
     *
     * @param aggregate {Aggregate}
     * @return {UseCaseName}Result
     * @author Claude
     * @since {현재 날짜}
     */
    public {UseCaseName}Result toResult({Aggregate} aggregate) {
        return new {UseCaseName}Result(
            aggregate.getId().value(),
            aggregate.getStatus().name(),
            "성공"
        );
    }
}
```

### 4. 생성 체크리스트

- [ ] **트랜잭션 경계**: 외부 API 호출은 트랜잭션 밖에서
- [ ] **DTO 사용**: Domain 객체 직접 반환 금지
- [ ] **Assembler 패턴**: Domain ↔ DTO 변환 분리
- [ ] **Lombok 미사용**: Plain Java record 사용
- [ ] **Javadoc 완전성**: `@author`, `@since` 포함
- [ ] **Proxy 제약사항**: Private/Final 메서드에 `@Transactional` 없음

## 🚀 실행

PRD를 읽고 UseCase 요구사항을 분석한 후, 위 규칙을 따라 UseCase를 생성하세요.
