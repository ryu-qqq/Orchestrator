# Domain Testing Support Toolkit (testFixtures)
`02-domain-layer/testing/00_testing-support-toolkit.md`

> 도메인 테스트를 빠르고 결정론적으로 작성하기 위한 **테스트 유틸 표준**입니다.  
> 이 툴킷은 **testFixtures**에 두고, 모든 도메인 단위 테스트에서 재사용합니다.

## 포함 유틸
- **DomainEventsSpy** — 도메인 이벤트 캡처/검증 스파이 (프로젝트의 `DomainEvents`와 연결)
- **ClockFixtures** — 고정/이동 가능한 시계 도우미 (결정론적 테스트)
- **IdGeneratorFake** — 예측 가능한 ID 생성기(시퀀스/프리디파인드)
- **Invariants** — 합계/중복/정렬 등 공통 불변식 어설션

## Gradle 설정 (Groovy DSL)
```groovy
// domain 모듈의 build.gradle
plugins {
    id 'java'
    id 'java-test-fixtures'
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    testImplementation(testFixtures(project(':domain'))) // 다른 모듈에서 사용 시
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testImplementation 'org.assertj:assertj-core:3.26.0'
}
test {
    useJUnitPlatform()
}
```

## Gradle (Kotlin DSL)
```kotlin
plugins {
    java
    `java-test-fixtures`
}
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}
dependencies {
    testImplementation(testFixtures(project(":domain")))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.26.0")
}
tasks.test { useJUnitPlatform() }
```

## 패키지 구조(예시)
```
src/testFixtures/java/com/connectly/testing/domain/
  events/DomainEventsSpy.java
  time/ClockFixtures.java
  id/IdGenerator.java
  id/SequentialIdGeneratorFake.java
  id/DeterministicIdGeneratorFake.java
  invariants/Invariants.java
```

## 1) DomainEventsSpy — 도메인 이벤트 캡처
프로젝트의 이벤트 집계기가 `DomainEvents.pull()`/`DomainEvents.clear()` 형태라면 아래처럼 연결하세요.

```java
var spy = new DomainEventsSpy<>(DomainEvents::pull, DomainEvents::clear);
spy.reset();
// ... when domain emits events ...
assertThat(spy.ofType(OrderConfirmed.class)).anyMatch(e -> e.orderId().equals(orderId));
```

## 2) ClockFixtures — 고정/이동 시계
```java
Clock clock = ClockFixtures.fixedAt("2025-10-16T00:00:00Z");
clock = ClockFixtures.advance(clock, Duration.ofHours(2)); // 2시간 이후
```

## 3) IdGeneratorFake — 결정론적 ID
```java
IdGenerator gen = new SequentialIdGeneratorFake("order-", 1);
assertThat(gen.nextId()).isEqualTo("order-1");
```

## 4) Invariants — 합계/중복/정렬 어설션
```java
Invariants.assertSumEquals(lines, l -> l.price().amount(), order.total().amount());
Invariants.assertNoDuplicates(lines, OrderLine::sku);
Invariants.assertSorted(lines, OrderLine::position);
```

> 필요 시 유틸을 확장해 프로젝트 불변식(예: 합계=라인합계)용 도우미를 추가하세요.