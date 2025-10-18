# APPLICATION 패키지 가이드

> 유즈케이스 구현, 트랜잭션 경계, 포트 호출, 도메인 조립(Assembler). **헥사고날 아키텍처의 핵심 계층**.

**중요**: Application 계층은 **UseCase 구현**과 **도메인 오케스트레이션**을 담당합니다. Port(인터페이스)는 Application 계층에 위치하며, Adapter(구현체)는 별도 Adapter 계층에 위치합니다.

## 디렉터리 구조
```
application/
└─ [context]/
   ├─ port/
   │  ├─ in/          # Inbound Port (UseCase 인터페이스)
   │  │  └─ CreateOrderUseCase.java
   │  │     ├─ Command (내부 Record)
   │  │     └─ Response (내부 Record)
   │  └─ out/         # Outbound Port (외부 의존 인터페이스)
   │     ├─ SaveOrderPort.java
   │     ├─ LoadOrderPort.java
   │     └─ ExternalApiPort.java
   ├─ assembler/      # Command ↔ Domain 변환
   │  └─ OrderAssembler.java
   └─ service/
      ├─ command/     # 쓰기 UseCase 구현 (@Transactional)
      │  └─ CreateOrderService.java
      └─ query/       # 읽기 UseCase 구현 (@Transactional(readOnly=true))
         └─ GetOrderService.java
```

## 기본 템플릿 (DDD_Hexagonal_CQRS_Template.md 기반)
```
application/
├─ [context]/
│  ├─ dto/           # ❌ 사용 안 함 (UseCase 내부 Record 사용)
│  ├─ port/
│  │  ├─ in/         # UseCase 인터페이스
│  │  └─ out/        # 외부 의존 (DB, API 등)
│  ├─ assembler/     # Command → Domain 변환기
│  └─ service/
│     ├─ command/    # 쓰기 UseCase 구현 (@Transactional)
│     └─ query/      # 읽기 UseCase 구현 (@Transactional(readOnly=true))
```

**템플릿 차이점**:
- `dto/` 디렉터리는 **사용하지 않음** (UseCase 내부 Record로 대체)
- Command/Response는 **UseCase 인터페이스 내부**에 정의

## 포함할 객체 & 역할

### 1. Port (In/Out)
**Inbound Port (UseCase 인터페이스)**
- 애플리케이션 경계 인터페이스
- Command/Response를 **내부 Record**로 정의
- 비즈니스 유즈케이스의 추상화

**Outbound Port (외부 의존 인터페이스)**
- 영속성(Repository), 메시징, 외부 API 추상화
- 구현체는 **Adapter 계층**에 위치
- 의존성 역전 원칙(DIP) 적용

### 2. Service (Command/Query)
**Command Service (쓰기 유즈케이스)**
- `@Transactional` 적용
- Assembler로 Command → Domain 변환
- Outbound Port로 저장
- Domain Event 발행

**Query Service (읽기 유즈케이스)**
- `@Transactional(readOnly = true)` 적용
- Outbound Port로 조회
- Assembler로 Domain → Response 변환
- 조회 최적화 (N+1 방지, Projection 활용)

### 3. Assembler
**Command → Domain 변환**
- UseCase Command를 Domain Aggregate로 변환
- Value Object 생성 로직 포함
- 복잡한 도메인 객체 조립

**Domain → Response 변환**
- Domain Aggregate를 UseCase Response로 변환
- 여러 Aggregate 조합 (필요 시 Outbound Port 사용)
- DTO Projection 생성

### 4. Command/Response (UseCase 내부 Record)
**Command**
- 상태 변경 의도 표현
- Compact Constructor로 검증
- 불변 객체 (Record)

**Response**
- UseCase 실행 결과
- 최소한의 정보만 반환
- 불변 객체 (Record)

## 계층 간 데이터 변환 흐름

### 전체 데이터 변환 흐름
```
[Adapter Layer - Controller]
API Request (OrderRequest)
    ↓
[Adapter Layer - Mapper]
    ↓ toCommand()
UseCase Command (CreateOrderUseCase.Command)
    ↓
[Application Layer - Service]
    ↓
[Application Layer - Assembler]
    ↓ toDomain()
Domain Object (Order)
    ↓
[Domain Layer - Business Logic]
    ↓
Domain Result (Order)
    ↓
[Application Layer - Assembler]
    ↓ toResponse()
UseCase Response (CreateOrderUseCase.Response)
    ↓
[Adapter Layer - Mapper]
    ↓ toApiResponse()
API Response (OrderApiResponse)
    ↓
[Adapter Layer - Controller]
```

### Mapper vs Assembler 비교
| 구분 | Mapper (Adapter) | Assembler (Application) |
|------|------------------|-------------------------|
| **위치** | `adapter/in/web/mapper/` | `application/[context]/assembler/` |
| **변환** | Request ↔ Command<br>Response ↔ API Response | Command → Domain<br>Domain → Response |
| **의존성** | Adapter DTO | UseCase Command/Response, Domain |
| **복잡도** | 단순 매핑 | Value Object 변환, 조립 로직 |
| **예제** | `OrderApiMapper` | `OrderAssembler` |

**중요**: Mapper와 Assembler는 **역할이 다릅니다**!
- **Mapper**: Adapter Layer에서 API DTO ↔ UseCase DTO 변환
- **Assembler**: Application Layer에서 UseCase DTO ↔ Domain 변환

## 허용/금지 의존

### 허용 의존성
- `java.*` (표준 자바 API)
- `domain..` (Domain Layer)
- `application..port.out..` (Outbound Port)
- `org.springframework.stereotype.*` (최소한의 Spring 어노테이션)
- `org.springframework.transaction.annotation.Transactional`

### 금지 의존성
- `adapter..` (Adapter Layer 직접 의존 금지)
- `jakarta.persistence.*` (JPA는 Adapter Layer에서만)
- `org.springframework.web.*` (REST는 Adapter Layer에서만)
- `com.fasterxml.jackson.*` (JSON 직렬화는 Adapter Layer에서만)
- `lombok.*` (Lombok 사용 금지)

### 의존성 방향
```
Adapter (in) → Application → Domain
         ↑         ↓
         └─── Port (out) ←─ Adapter (out)
```

**핵심**: 의존성은 항상 **밖 → 안** (Adapter → Application → Domain)

## 네이밍 규약

### UseCase (Inbound Port)
- `CreateXxxUseCase`, `UpdateXxxUseCase`, `DeleteXxxUseCase` (Command)
- `GetXxxUseCase`, `FindXxxUseCase`, `SearchXxxUseCase` (Query)
- **규칙**: 동사 + Aggregate + `UseCase`

### Service (UseCase 구현체)
- `XxxCommandService` (Command UseCase 구현)
- `XxxQueryService` (Query UseCase 구현)
- **규칙**: Aggregate + Command/Query + `Service`

### Port (Outbound Port)
- `SaveXxxPort`, `LoadXxxPort`, `DeleteXxxPort` (Repository)
- `XxxCommandPort`, `XxxQueryPort` (CQRS 스타일)
- `ExternalXxxPort` (외부 API)
- **규칙**: 동사 + Aggregate + `Port` 또는 `외부시스템 + Port`

### Assembler
- `XxxAssembler` (도메인별 단일 Assembler)
- **규칙**: Aggregate + `Assembler`
- **예시**: `OrderAssembler`, `PaymentAssembler`

### Command/Response (내부 Record)
- `XxxUseCase.Command` (UseCase 내부 Record)
- `XxxUseCase.Response` (UseCase 내부 Record)
- **규칙**: UseCase 인터페이스 내부에 정의

## CQRS 명명 규칙 (템플릿 기반)

### Command Side (쓰기)
| 타입 | 인터페이스 | 구현체 | 포트(out) |
|------|-------------|----------|------------|
| 생성 | `CreateXxxUseCase` | `XxxCommandService` | `SaveXxxPort` |
| 수정 | `UpdateXxxUseCase` | `XxxCommandService` | `LoadXxxPort`, `SaveXxxPort` |
| 삭제 | `DeleteXxxUseCase` | `XxxCommandService` | `DeleteXxxPort` |

### Query Side (읽기)
| 타입 | 인터페이스 | 구현체 | 포트(out) |
|------|-------------|----------|------------|
| 단건 조회 | `GetXxxUseCase` | `XxxQueryService` | `LoadXxxPort` |
| 목록 조회 | `FindXxxUseCase` | `XxxQueryService` | `FindXxxPort` |
| 검색 | `SearchXxxUseCase` | `XxxQueryService` | `SearchXxxPort` |

## Do / Don't

### Do ✅
- UseCase에 Command/Response를 **내부 Record**로 정의
- Service에서 **Assembler**로 Command → Domain, Domain → Response 변환
- 트랜잭션 경계 명확히 설정 (`@Transactional`)
- 외부 I/O는 트랜잭션 **밖**에서 실행
- Command UseCase가 조회 필요 시 **QueryPort** 사용 (다른 서비스 직접 호출 금지)
- Spring Proxy 제약사항 고려 (Private/Final/Self-invocation 금지)
- Aggregate 단위 트랜잭션 유지

### Don't ❌
- Adapter DTO를 Application Layer에서 직접 사용 금지
- Mapper와 Assembler 혼동 금지 (역할이 다름!)
- 다른 Application 서비스 직접 호출 금지 (순환 의존 위험)
- REST/JPA 어노테이션 사용 금지 (Adapter Layer로 이동)
- 도메인 규칙을 Application Layer에서 구현 금지 (Domain Layer로 이동)
- Self-invocation (`this.method()`) 금지 (프록시 우회)
- Private/Final 메서드에 `@Transactional` 사용 금지
- `@Transactional` 메서드 내에서 외부 API 호출 금지

## Spring Proxy 제약사항 주의

### ❌ 작동하지 않는 경우
```java
// ❌ Private 메서드 - 프록시 무시
@Transactional
private void saveOrder(Order order) { ... }

// ❌ Final 클래스/메서드 - 프록시 생성 불가
@Transactional
public final void createOrder(Command cmd) { ... }

// ❌ Self-invocation - 프록시 우회
@Service
public class OrderService {
    @Transactional
    public void processOrder() {
        this.saveOrder(); // ❌ @Transactional 무시됨!
    }

    @Transactional
    void saveOrder() { ... }
}
```

### ✅ 올바른 방법
```java
// ✅ Public 메서드
@Transactional
public void saveOrder(Order order) { ... }

// ✅ 별도 빈으로 분리
@Service
public class OrderService {
    private final OrderPersistenceService persistenceService;

    public void processOrder() {
        persistenceService.saveOrder(); // ✅ 프록시 정상 작동
    }
}

@Service
public class OrderPersistenceService {
    @Transactional
    public void saveOrder() { ... }
}
```

## 트랜잭션 경계 및 외부 호출 규칙

### ❌ 트랜잭션 내 외부 API 호출
```java
// ❌ Bad - 트랜잭션 내 외부 API 호출
@Transactional
public void processOrder(Command cmd) {
    Order order = orderAssembler.toDomain(cmd);
    saveOrderPort.save(order);

    s3Client.uploadFile(); // ❌ 외부 API - 트랜잭션 길어짐
    emailService.send();   // ❌ 외부 API - 실패 시 롤백 문제
}
```

### ✅ 트랜잭션 분리
```java
// ✅ Good - 트랜잭션과 외부 호출 분리
@Service
public class OrderFacadeService {
    private final CreateOrderService createOrderService;
    private final FileUploadPort fileUploadPort;
    private final EmailPort emailPort;

    public void processOrder(Command cmd) {
        // ✅ 1. 트랜잭션 내에서 DB 저장
        Response response = createOrderService.createOrder(cmd);

        // ✅ 2. 트랜잭션 밖에서 외부 호출
        fileUploadPort.upload(response.orderId());
        emailPort.sendConfirmation(response.orderId());
    }
}

@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {
    @Override
    public Response createOrder(Command cmd) {
        // ✅ 짧은 트랜잭션 (DB 저장만)
        Order order = orderAssembler.toDomain(cmd);
        Order savedOrder = saveOrderPort.save(order);
        return orderAssembler.toResponse(savedOrder);
    }
}
```

## Application 서비스 간 호출 규칙

### ❌ 서비스 직접 호출
```java
// ❌ Bad - 순환 의존 위험
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {
    private final GetCustomerService getCustomerService; // ❌

    @Override
    public Response createOrder(Command cmd) {
        // ❌ 다른 서비스 직접 호출
        CustomerResponse customer = getCustomerService.getCustomer(cmd.customerId());

        Order order = orderAssembler.toDomain(cmd, customer);
        return orderAssembler.toResponse(saveOrderPort.save(order));
    }
}
```

### ✅ QueryPort 사용
```java
// ✅ Good - QueryPort 사용
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {
    private final LoadCustomerPort loadCustomerPort; // ✅ Port 사용

    @Override
    public Response createOrder(Command cmd) {
        // ✅ QueryPort로 조회
        Customer customer = loadCustomerPort.load(cmd.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(cmd.customerId()));

        Order order = orderAssembler.toDomain(cmd, customer);
        return orderAssembler.toResponse(saveOrderPort.save(order));
    }
}
```

## ArchUnit 룰 스니펫

```java
// Application Layer는 Adapter Layer에 의존하지 않음
noClasses().that().resideInAPackage("..application..")
    .should().dependOnClassesThat().resideInAnyPackage("..adapter..");

// Inbound Port는 UseCase로 끝나야 함
classes().that().resideInAPackage("..application..port.in..")
    .should().haveSimpleNameEndingWith("UseCase");

// Outbound Port는 Port로 끝나야 함
classes().that().resideInAPackage("..application..port.out..")
    .should().haveSimpleNameEndingWith("Port");

// Service는 Service로 끝나야 함
classes().that().resideInAPackage("..application..service..")
    .should().haveSimpleNameEndingWith("Service");

// Assembler는 Assembler로 끝나야 함
classes().that().resideInAPackage("..application..assembler..")
    .should().haveSimpleNameEndingWith("Assembler");

// Command Service는 @Transactional 필수
classes().that().resideInAPackage("..application..service.command..")
    .should().beAnnotatedWith(Transactional.class);

// Query Service는 @Transactional(readOnly=true) 권장
classes().that().resideInAPackage("..application..service.query..")
    .should().beAnnotatedWith(Transactional.class);
```

## 관련 문서

### UseCase 설계
- [Command UseCase](../usecase-design/01_command-usecase.md) - 상태 변경 유즈케이스
- [Query UseCase](../usecase-design/02_query-usecase.md) - 조회 유즈케이스
- [UseCase Inner DTO](../assembler-pattern/02_usecase-inner-dto.md) - 내부 Record 패턴

### Assembler 패턴
- [Assembler Responsibility](../assembler-pattern/01_assembler-responsibility.md) - Assembler 역할
- [Mapper vs Assembler](../assembler-pattern/02_mapper-vs-assembler.md) - 차이점

### DTO 패턴
- [Request/Response DTO](../dto-patterns/01_request-response-dto.md) - Adapter Layer DTO
- [Command/Query DTO](../dto-patterns/02_command-query-dto.md) - Application Layer DTO
- [DTO Validation](../dto-patterns/03_dto-validation.md) - 검증 전략

### 트랜잭션 관리
- [Transaction Boundaries](../transaction-management/01_transaction-boundaries.md) - 트랜잭션 경계
- [Spring Proxy Limitations](../transaction-management/02_spring-proxy-limitations.md) - 프록시 제약사항
- [Transaction Best Practices](../transaction-management/03_transaction-best-practices.md) - 모범 사례

### 테스트
- [Application Service Testing](../testing/01_application-service-testing.md) - 서비스 테스트

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
