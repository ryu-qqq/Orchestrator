# Persistence Layer Testing Overview
`04-persistence-layer/testing/00_testing-overview.md`

> Persistence Layer 테스트 전략의 전체 개요입니다.
> **단위 테스트(In-Memory)**, **통합 테스트(Testcontainers)**, **태그 전략**을 정의합니다.

---

## 📌 Persistence Layer 테스트 핵심 원칙

### 1. 테스트 레벨 분리

| 테스트 레벨 | 도구 | 실행 환경 | 속도 | 사용 시점 |
|------------|------|----------|------|----------|
| **단위 테스트** | `@DataJpaTest` + H2 | In-Memory | 빠름 (<1초) | 개발 중, CI/CD 필수 |
| **통합 테스트** | Testcontainers | Real DB (Docker) | 느림 (5-10초) | PR 전, 배포 전 |

### 2. 테스트 피라미드 전략

```
        /\
       /  \    E2E Tests (최소)
      /____\
     /      \  Integration Tests (적정)
    /________\
   /          \ Unit Tests (대부분)
  /__________  \
```

**권장 비율:**
- 단위 테스트: 70%
- 통합 테스트: 25%
- E2E 테스트: 5%

---

## 🎯 테스트 전략 매트릭스

### 언제 어떤 테스트를 사용할까?

| 검증 대상 | 단위 테스트 | 통합 테스트 | 비고 |
|----------|-----------|-----------|------|
| JPA Entity 매핑 | ✅ | ✅ | H2로 빠르게, Testcontainers로 검증 |
| QueryDSL 동적 쿼리 | ✅ | ✅ | H2로 문법 검증, 실제 DB로 성능 검증 |
| Repository 메서드 | ✅ | - | `@DataJpaTest`로 충분 |
| DB 제약조건 (FK, Unique) | - | ✅ | Real DB 필수 |
| DB 함수 (PostgreSQL JSON) | - | ✅ | DB-Specific 기능 |
| 트랜잭션 격리 수준 | - | ✅ | Real DB 필수 |
| Flyway Migration | - | ✅ | Real DB 필수 |

---

## 🏗️ 테스트 구조

### 패키지 구조
```
adapter/out/persistence-jpa/
├─ src/main/java/
│  └─ com.company.adapter.out.persistence/
│     ├─ entity/          → JPA Entity
│     ├─ repository/      → Repository 구현체
│     └─ mapper/          → Entity ↔ Domain 변환
└─ src/test/java/
   └─ com.company.adapter.out.persistence/
      ├─ repository/
      │  ├─ OrderRepositoryUnitTest.java       (단위: @DataJpaTest + H2)
      │  └─ OrderRepositoryIntegrationTest.java (통합: Testcontainers)
      ├─ entity/
      │  └─ OrderEntityTest.java               (단위: POJO 테스트)
      └─ support/
         ├─ TestcontainersConfig.java          (Testcontainers 설정)
         └─ EntityFixtures.java                (테스트 데이터 빌더)
```

---

## 🔖 테스트 태그 전략 (JUnit 5 @Tag)

### 태그 정의

```java
public class TestTags {
    /**
     * 단위 테스트: In-Memory H2, 빠른 실행 (CI/CD 필수)
     */
    public static final String UNIT = "unit";

    /**
     * 통합 테스트: Testcontainers, 느린 실행 (PR 전)
     */
    public static final String INTEGRATION = "integration";

    /**
     * DB 특화 테스트: PostgreSQL, MySQL 전용 기능
     */
    public static final String DATABASE = "database";
}
```

### Gradle 태그 실행

```kotlin
// build.gradle.kts

tasks.test {
    useJUnitPlatform {
        // CI/CD: 단위 테스트만 실행
        includeTags("unit")
    }
}

// 통합 테스트 실행 (별도 태스크)
tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

// 모든 테스트 실행
tasks.register<Test>("allTests") {
    useJUnitPlatform()
}
```

### 실행 명령어

```bash
# 단위 테스트만 (빠름, CI/CD 기본)
./gradlew test

# 통합 테스트만 (느림, Docker 필요)
./gradlew integrationTest

# 모든 테스트
./gradlew allTests

# 특정 태그만
./gradlew test --tests "*Test" -Dtest.tags=unit
```

---

## 📦 테스트 의존성 (build.gradle.kts)

```kotlin
dependencies {
    // ========================================
    // 단위 테스트 (H2 In-Memory)
    // ========================================
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")

    // ========================================
    // 통합 테스트 (Testcontainers)
    // ========================================
    testImplementation("org.testcontainers:testcontainers:1.19.0")
    testImplementation("org.testcontainers:junit-jupiter:1.19.0")
    testImplementation("org.testcontainers:postgresql:1.19.0")
    testImplementation("org.testcontainers:mysql:1.19.0")
    testImplementation("org.testcontainers:redis:1.19.0")

    // ========================================
    // 테스트 유틸
    // ========================================
    testImplementation("org.assertj:assertj-core:3.26.0")
    testImplementation(testFixtures(project(":domain")))
}
```

---

## 🎓 테스트 작성 가이드라인

### 단위 테스트 작성 시점
- ✅ Repository 메서드 기본 CRUD
- ✅ QueryDSL 쿼리 문법 검증
- ✅ Entity 매핑 검증
- ✅ 개발 중 빠른 피드백 필요

### 통합 테스트 작성 시점
- ✅ DB 제약조건 검증 (FK, Unique Key)
- ✅ DB-Specific 함수 (PostgreSQL JSON, MySQL Full-Text)
- ✅ Flyway Migration 검증
- ✅ 트랜잭션 격리 수준 테스트
- ✅ 성능 테스트 (Pagination, N+1 검증)

---

## 🚀 CI/CD 통합

### GitHub Actions 예시

```yaml
name: CI

on: [push, pull_request]

jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run Unit Tests
        run: ./gradlew test

  integration-test:
    runs-on: ubuntu-latest
    needs: unit-test
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run Integration Tests (Testcontainers)
        run: ./gradlew integrationTest
```

---

## 📊 테스트 커버리지 목표

| 레이어 | 목표 커버리지 | 도구 |
|--------|------------|------|
| Repository | 80% | JaCoCo |
| Entity | 70% | JaCoCo |
| Mapper | 90% | JaCoCo |

```kotlin
// build.gradle.kts
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "CLASS"
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
            excludes = listOf(
                "*.entity.*",  // JPA Entity 제외
                "*.Q*"         // QueryDSL 생성 클래스 제외
            )
        }
    }
}
```

---

## 📚 다음 문서

- [01. Repository 단위 테스트](./01_repository-unit-testing.md)
- [02. Testcontainers 통합 테스트](./02_testcontainers-integration.md)
- [03. 테스트 태그 전략](./03_test-tags-strategy.md)
- [04. Entity 테스트 패턴](./04_entity-testing.md)
- [05. QueryDSL 테스트](./05_querydsl-testing.md)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
