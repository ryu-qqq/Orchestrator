# Test Tags Strategy - CI/CD Integration
`04-persistence-layer/testing/03_test-tags-strategy.md`

> JUnit 5 **@Tag**를 활용한 테스트 분류 및 **CI/CD 파이프라인 통합** 전략입니다.
> 빠른 피드백을 위한 단위 테스트와 안정성 검증을 위한 통합 테스트를 분리 실행합니다.

---

## 📌 핵심 원칙

### 테스트 태그의 목적

1. **실행 시점 분리**: 개발 중 vs PR 전 vs 배포 전
2. **속도 최적화**: 빠른 단위 테스트 우선 실행
3. **리소스 관리**: Docker 필요 여부로 분리
4. **CI/CD 효율화**: 필수 테스트만 실행하여 빌드 시간 단축

---

## 🏷️ 표준 테스트 태그 정의

### 1. 태그 상수 클래스

```java
package com.company.common.test;

/**
 * 프로젝트 표준 테스트 태그 정의
 *
 * @author development-team
 * @since 1.0.0
 */
public final class TestTags {

    private TestTags() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========================================
    // 기본 분류
    // ========================================

    /**
     * 단위 테스트 (In-Memory, 빠름, CI/CD 필수)
     * - @DataJpaTest + H2
     * - Domain 순수 테스트
     * - 실행 시간: < 1초
     */
    public static final String UNIT = "unit";

    /**
     * 통합 테스트 (Testcontainers, 느림, PR 전 실행)
     * - Real Database (PostgreSQL, MySQL)
     * - 실행 시간: 5-10초
     */
    public static final String INTEGRATION = "integration";

    /**
     * E2E 테스트 (전체 시스템, 매우 느림, 배포 전 실행)
     * - API → Service → DB 전체 흐름
     * - 실행 시간: 10-30초
     */
    public static final String E2E = "e2e";

    // ========================================
    // 기술별 분류
    // ========================================

    /**
     * Database 특화 테스트
     * - DB 제약조건 (FK, Unique)
     * - DB 전용 함수 (PostgreSQL JSON, MySQL Full-Text)
     */
    public static final String DATABASE = "database";

    /**
     * Web Layer 테스트 (@WebMvcTest)
     */
    public static final String WEB = "web";

    /**
     * Security 테스트
     */
    public static final String SECURITY = "security";

    /**
     * 성능 테스트 (N+1, Pagination)
     */
    public static final String PERFORMANCE = "performance";

    // ========================================
    // 환경별 분류
    // ========================================

    /**
     * Docker 필요 (Testcontainers)
     */
    public static final String DOCKER_REQUIRED = "docker-required";

    /**
     * 외부 API 의존성 (Mocking 안 됨)
     */
    public static final String EXTERNAL_API = "external-api";
}
```

---

## 🎯 태그 사용 패턴

### 패턴 1: 단위 테스트 (기본)

```java
package com.company.adapter.out.persistence.repository;

import com.company.common.test.TestTags;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * OrderRepository 단위 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@Tag(TestTags.UNIT)
class OrderRepositoryUnitTest {

    @Test
    void save_ShouldPersist() {
        // H2 In-Memory 테스트
    }
}
```

### 패턴 2: 통합 테스트 (Testcontainers)

```java
/**
 * OrderRepository 통합 테스트 (PostgreSQL Testcontainers)
 *
 * @author development-team
 * @since 1.0.0
 */
@Tag(TestTags.INTEGRATION)
@Tag(TestTags.DATABASE)
@Tag(TestTags.DOCKER_REQUIRED)
class OrderRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Test
    void save_WithUniqueConstraint_ShouldThrowException() {
        // Real PostgreSQL 테스트
    }
}
```

### 패턴 3: 성능 테스트

```java
/**
 * Order N+1 쿼리 성능 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@Tag(TestTags.INTEGRATION)
@Tag(TestTags.PERFORMANCE)
@Tag(TestTags.DOCKER_REQUIRED)
class OrderPerformanceTest extends PostgresIntegrationTest {

    @Test
    void findOrdersWithLines_ShouldAvoidNPlusOne() {
        // N+1 쿼리 검증
    }
}
```

### 패턴 4: Web Layer 테스트

```java
/**
 * OrderController 웹 레이어 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@WebMvcTest(OrderController.class)
@Tag(TestTags.UNIT)
@Tag(TestTags.WEB)
class OrderControllerTest {

    @Test
    void createOrder_ShouldReturnCreated() {
        // MockMvc 테스트
    }
}
```

---

## 🛠️ Gradle 통합 설정

### build.gradle.kts (Kotlin DSL)

```kotlin
tasks.test {
    useJUnitPlatform {
        // ✅ CI/CD 기본: 단위 테스트만 실행
        includeTags("unit")
        excludeTags("integration", "e2e")
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }

    // 병렬 실행 (속도 향상)
    maxParallelForks = Runtime.getRuntime().availableProcessors().div(2).coerceAtLeast(1)
}

// ========================================
// 통합 테스트 Task (별도 실행)
// ========================================
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs integration tests with Testcontainers"

    useJUnitPlatform {
        includeTags("integration")
    }

    // 통합 테스트는 단위 테스트 성공 후 실행
    shouldRunAfter(tasks.test)

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ========================================
// 모든 테스트 Task
// ========================================
tasks.register<Test>("allTests") {
    group = "verification"
    description = "Runs all tests (unit + integration + e2e)"

    useJUnitPlatform()

    shouldRunAfter(tasks.test, tasks.named("integrationTest"))
}

// ========================================
// 성능 테스트 Task
// ========================================
tasks.register<Test>("performanceTest") {
    group = "verification"
    description = "Runs performance tests"

    useJUnitPlatform {
        includeTags("performance")
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true // 성능 로그 출력
    }
}
```

### build.gradle (Groovy DSL)

```groovy
test {
    useJUnitPlatform {
        includeTags 'unit'
        excludeTags 'integration', 'e2e'
    }
}

task integrationTest(type: Test) {
    group = 'verification'
    description = 'Runs integration tests with Testcontainers'

    useJUnitPlatform {
        includeTags 'integration'
    }

    shouldRunAfter test
}

task allTests(type: Test) {
    useJUnitPlatform()
    shouldRunAfter test, integrationTest
}
```

---

## 🚀 실행 명령어

### 로컬 개발

```bash
# 단위 테스트만 (빠름, 기본)
./gradlew test

# 통합 테스트만 (Docker 필요)
./gradlew integrationTest

# 모든 테스트
./gradlew allTests

# 성능 테스트만
./gradlew performanceTest

# 특정 태그만 실행 (CLI)
./gradlew test --tests "*Test" -Dtest.tags=unit,web
```

### CI/CD 파이프라인

```bash
# PR 검증: 단위 테스트 + 통합 테스트
./gradlew test integrationTest

# 배포 전: 모든 테스트
./gradlew allTests

# 성능 모니터링: 성능 테스트만
./gradlew performanceTest
```

---

## 🔄 CI/CD 통합 예시

### GitHub Actions

```yaml
name: CI Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  # ========================================
  # Step 1: 단위 테스트 (빠름, 필수)
  # ========================================
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Unit Tests
        run: ./gradlew test

      - name: Upload Test Report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-report
          path: build/reports/tests/test/

  # ========================================
  # Step 2: 통합 테스트 (느림, PR 필수)
  # ========================================
  integration-test:
    runs-on: ubuntu-latest
    needs: unit-test
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Integration Tests (Testcontainers)
        run: ./gradlew integrationTest

      - name: Upload Test Report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: integration-test-report
          path: build/reports/tests/integrationTest/

  # ========================================
  # Step 3: 코드 커버리지 (선택)
  # ========================================
  coverage:
    runs-on: ubuntu-latest
    needs: [unit-test, integration-test]
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Generate Coverage Report
        run: ./gradlew jacocoTestReport

      - name: Upload Coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          files: build/reports/jacoco/test/jacocoTestReport.xml
```

---

## 📊 테스트 실행 전략 매트릭스

| 환경 | 실행 테스트 | 실행 시점 | 소요 시간 |
|------|-----------|----------|----------|
| **로컬 개발** | `unit` | 코드 수정 시 | < 10초 |
| **PR 검증** | `unit` + `integration` | PR 생성 시 | < 2분 |
| **배포 전** | `unit` + `integration` + `e2e` | main 병합 전 | < 5분 |
| **Nightly Build** | `all` + `performance` | 매일 자정 | < 10분 |

---

## 🎯 태그 조합 예시

### 추천 조합

```java
// ✅ 단위 테스트 (기본)
@Tag(TestTags.UNIT)

// ✅ Repository 통합 테스트
@Tag(TestTags.INTEGRATION)
@Tag(TestTags.DATABASE)
@Tag(TestTags.DOCKER_REQUIRED)

// ✅ Web Layer 단위 테스트
@Tag(TestTags.UNIT)
@Tag(TestTags.WEB)

// ✅ 성능 테스트
@Tag(TestTags.INTEGRATION)
@Tag(TestTags.PERFORMANCE)
@Tag(TestTags.DOCKER_REQUIRED)

// ✅ E2E 테스트
@Tag(TestTags.E2E)
@Tag(TestTags.DOCKER_REQUIRED)
```

---

## 📋 테스트 태그 체크리스트

- [ ] 모든 테스트에 최소 1개 이상의 태그 추가
- [ ] 단위 테스트는 `@Tag("unit")` 필수
- [ ] Testcontainers 사용 시 `@Tag("docker-required")` 추가
- [ ] DB 전용 기능 테스트는 `@Tag("database")` 추가
- [ ] Gradle Task 설정 완료 (test, integrationTest, allTests)
- [ ] CI/CD 파이프라인에 태그 기반 실행 적용

---

## 🚫 피해야 할 패턴

- ❌ 모든 테스트를 항상 실행 → CI/CD 느려짐
- ❌ 태그 없이 테스트 작성 → 분류 불가
- ❌ 통합 테스트를 기본 `./gradlew test`에 포함 → 개발 피드백 느려짐
- ❌ 태그 이름 불일치 (`"unit"` vs `"Unit"` vs `"UNIT"`)

---

## 📚 다음 문서

- [04. Entity 테스트 패턴](./04_entity-testing.md)
- [05. QueryDSL 테스트](./05_querydsl-testing.md)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
