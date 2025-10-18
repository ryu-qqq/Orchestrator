# DOMAIN 패키지 가이드

> 순수 도메인 모델 레이어. **프레임워크 의존 금지**, **불변성·명세 중심**.
> **중요**: Domain 모듈에는 **순수 도메인 객체만** 포함됩니다. Port(인터페이스), Service, Repository는 **Application 모듈**에 위치합니다.

## 디렉터리 구조
```
domain/
├─ shared/
│  ├─ id/           # 식별자 VO (예: OrderId, SessionId)
│  ├─ vo/           # 값 객체 (FileSize, PolicyKey, Email 등)
│  ├─ event/        # DomainEvent 인터페이스, 공통 이벤트
│  ├─ exception/    # DomainException 및 공통 예외
│  └─ policy/       # 범용 정책(도메인 규칙)
└─ [boundedContext]/
   └─ [aggregateName]/
      ├─ [AggregateRoot].java
      ├─ vo/
      ├─ event/
      ├─ exception/
      ├─ policy/
      └─ factory/
```

## 포함할 객체 & 역할
- **Aggregate Root / Entity**: 트랜잭션 경계, 상태 전이, 불변성 보장
- **Value Object(VO)**: 값 동등성/불변/검증/행동 포함(가능하면 `record`)
- **Policy**: 제약/허용 규칙 모음
- **Domain Event**: 상태 전이 결과(발생 시각, 관련 식별자 포함)
- **Factory**: 복잡한 도메인 객체 생성 로직

## Application 모듈에 위치하는 것들
- **Port (인터페이스)**: Inbound Port (UseCase), Outbound Port (Repository, External API)
- **Application Service**: UseCase 구현, 트랜잭션 경계, 오케스트레이션
- **DTO/Mapper**: 계층 간 데이터 변환

## 허용/금지 의존
- **허용**: `java.*`, 내부 도메인 코드
- **금지**: `org.springframework.*`, `jakarta.persistence.*`, `com.fasterxml.jackson.*`, `lombok.*`
- 외부 시스템(I/O) 접근 금지

## 네이밍 규약
- Aggregate: 단수 명사 (`Order`, `Session`)
- VO: 의미 있는 명사 (`PolicyKey`, `FileSize`)
- Event: `<Aggregate><PastTense>Event` (예: `OrderConfirmed`, `PaymentCompleted`)
- Exception: `<Aggregate><Condition>Exception` (예: `OrderNotFoundException`, `InsufficientStockException`)
- Factory: `<Aggregate>Factory` (예: `OrderFactory`)

## Do / Don't
**Do**
- 정적 팩토리 `of()/parse()/create()` 사용
- 생성 시 검증(불변성 위반시 도메인 예외)
- 값 기반 equals/hashCode 구현 (record 권장)

**Don't**
- public setter, 가변 컬렉션 노출, 프레임워크 어노테이션 사용 금지
- JPA/Jackson/Lombok 의존 금지
- 외부 API 호출 금지

## ArchUnit 룰 스니펫
```java
noClasses().that().resideInAPackage("..domain..")
  .should().dependOnClassesThat().resideInAnyPackage(
    "org.springframework..","jakarta.persistence..","com.fasterxml.jackson..","lombok..");
fields().that().areDeclaredInClassesThat().resideInAPackage("..domain..")
  .and().areNotStatic().should().bePrivate().andShould().beFinal();
noMethods().that().areDeclaredInClassesThat().resideInAPackage("..domain..")
  .and().arePublic().and().haveNameMatching("set[A-Z].*").should().beDeclared();
```
