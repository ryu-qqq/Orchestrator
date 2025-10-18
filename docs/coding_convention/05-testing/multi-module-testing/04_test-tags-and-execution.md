# 테스트 태깅 및 실행 전략 (Test Tags and Execution)

## 📋 목차
- [개요](#개요)
- [JUnit 5 @Tag 시스템](#junit-5-tag-시스템)
- [태그 전략](#태그-전략)
- [Gradle 테스트 태스크 구성](#gradle-테스트-태스크-구성)
- [CI/CD 파이프라인 통합](#cicd-파이프라인-통합)
- [성능 최적화](#성능-최적화)
- [실전 예제](#실전-예제)

---

## 개요

### 목적

멀티모듈 프로젝트에서 **테스트를 태깅하여 선택적으로 실행**함으로써 빌드 속도와 피드백 시간을 최적화합니다.

### 문제점: 모든 테스트를 항상 실행

❌ **Bad: PR 빌드에서 모든 테스트 실행**

```bash
# 전체 테스트 실행 (15분 소요)
./gradlew test

# 문제점:
# - 단위 테스트: 30초
# - 통합 테스트: 5분
# - E2E 테스트: 10분
# → 개발자 피드백 지연 (15분 대기)
```

---

### 해결책: 태그 기반 선택적 실행

✅ **Good: 상황별 테스트 실행**

```bash
# PR 빌드: 빠른 테스트만 (2분)
./gradlew test -Ptags=unit,boundary

# 메인 빌드: 통합 테스트 포함 (7분)
./gradlew test -Ptags=unit,boundary,integration

# 배포 전: 전체 테스트 (15분)
./gradlew test -Ptags=all
```

---

## JUnit 5 @Tag 시스템

### 1. 기본 사용법

**@Tag 어노테이션**

```java
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")  // 태그 지정
class OrderTest {

    @Test
    @Tag("fast")  // 메서드 레벨 태그
    void quickTest() {
        // ...
    }

    @Test
    @Tag("slow")
    void slowTest() {
        // ...
    }
}
```

---

### 2. 복수 태그

**여러 태그 조합**

```java
@Tag("unit")
@Tag("domain")  // 복수 태그 가능
class OrderTest {
    // ...
}

@Tag("integration")
@Tag("persistence")
@Tag("slow")
class OrderPersistenceAdapterTest {
    // ...
}
```

---

### 3. 커스텀 어노테이션

**✅ Good: 재사용 가능한 메타 어노테이션**

```java
package com.company.template.common.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.*;

/**
 * 단위 테스트 메타 어노테이션
 *
 * - @Tag("unit")
 * - @Tag("fast")
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("unit")
@Tag("fast")
@Test
public @interface UnitTest {
}
```

**사용:**

```java
import com.company.template.common.test.UnitTest;

class OrderTest {

    @UnitTest  // @Tag("unit") + @Tag("fast") + @Test
    void createOrder_ShouldWork() {
        // ...
    }
}
```

---

### 4. 전체 메타 어노테이션 세트

**UnitTest.java**

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("unit")
@Tag("fast")
@Test
public @interface UnitTest {
}
```

**IntegrationTest.java**

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@Tag("slow")
@Test
public @interface IntegrationTest {
}
```

**BoundaryTest.java**

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("boundary")
@Tag("integration")
@Test
public @interface BoundaryTest {
}
```

**E2ETest.java**

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("e2e")
@Tag("slow")
@Test
public @interface E2ETest {
}
```

**ArchUnitTest.java**

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("archunit")
@Tag("architecture")
@Test
public @interface ArchUnitTest {
}
```

**PerformanceTest.java**

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("performance")
@Tag("slow")
@Test
public @interface PerformanceTest {
}
```

---

## 태그 전략

### 1. 레이어별 태그

| 레이어 | 태그 | 설명 | 실행 시간 |
|--------|------|------|----------|
| **Domain** | `unit`, `domain` | 순수 단위 테스트 | ~5ms/테스트 |
| **Application** | `unit`, `application` | Port mocking 테스트 | ~50ms/테스트 |
| **Adapter** | `integration`, `adapter` | 실제 인프라 테스트 | ~1-2s/테스트 |
| **전체** | `e2e` | 전체 시스템 테스트 | ~3-5s/테스트 |

---

### 2. 속도 기반 태그

| 태그 | 기준 | 사용 시점 |
|------|------|----------|
| **fast** | < 100ms | PR 빌드 (빠른 피드백) |
| **medium** | 100ms ~ 1s | 메인 빌드 |
| **slow** | > 1s | 배포 전 빌드 |

---

### 3. 기능별 태그

| 태그 | 설명 | 예시 |
|------|------|------|
| **archunit** | 아키텍처 검증 | 레이어 의존성, 네이밍 규칙 |
| **performance** | 성능 테스트 | N+1 탐지, 응답 시간 |
| **security** | 보안 테스트 | 인증, 권한, SQL Injection |
| **boundary** | 모듈 경계 | Adapter ↔ Application |

---

### 4. 전체 태그 체계

```yaml
태그_계층_구조:
  속도:
    - fast      # < 100ms
    - medium    # 100ms ~ 1s
    - slow      # > 1s

  레이어:
    - unit           # 단위 테스트
    - integration    # 통합 테스트
    - boundary       # 모듈 경계
    - e2e            # End-to-End

  모듈:
    - domain
    - application
    - adapter
    - persistence
    - rest

  기능:
    - archunit
    - performance
    - security
    - contract
```

---

## Gradle 테스트 태스크 구성

### 1. build.gradle 설정

**root/build.gradle**

```gradle
// 모든 서브프로젝트에 적용
subprojects {
    apply plugin: 'java'

    test {
        useJUnitPlatform {
            // 기본: fast 태그만 실행 (단위 테스트)
            includeTags 'fast'

            // 프로퍼티로 태그 오버라이드 가능
            // ./gradlew test -Ptags=integration
            if (project.hasProperty('tags')) {
                includeTags project.property('tags').split(',')
            }
        }

        // 병렬 실행 (속도 향상)
        maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1

        // 테스트 로그
        testLogging {
            events "passed", "skipped", "failed"
            exceptionFormat "full"
        }
    }
}
```

---

### 2. 커스텀 Gradle 태스크

**build.gradle**

```gradle
// PR 빌드: 단위 + 경계 테스트 (~2분)
task prTest(type: Test) {
    useJUnitPlatform {
        includeTags 'fast', 'boundary'
    }
    group = 'verification'
    description = 'Runs fast tests for PR builds'
}

// 메인 빌드: 통합 테스트 포함 (~7분)
task mainTest(type: Test) {
    useJUnitPlatform {
        includeTags 'fast', 'boundary', 'integration'
    }
    group = 'verification'
    description = 'Runs tests for main branch builds'
}

// 배포 전: 전체 테스트 (~15분)
task fullTest(type: Test) {
    useJUnitPlatform {
        // 모든 태그 포함
    }
    group = 'verification'
    description = 'Runs all tests including E2E'
}

// ArchUnit 전용 태스크
task archTest(type: Test) {
    useJUnitPlatform {
        includeTags 'archunit'
    }
    group = 'verification'
    description = 'Runs architecture tests only'
}

// 성능 테스트 전용
task perfTest(type: Test) {
    useJUnitPlatform {
        includeTags 'performance'
    }
    group = 'verification'
    description = 'Runs performance tests only'
}
```

---

### 3. 실행 명령어

```bash
# PR 빌드 (빠른 피드백)
./gradlew prTest

# 메인 브랜치 빌드
./gradlew mainTest

# 배포 전 전체 검증
./gradlew fullTest

# ArchUnit만 실행
./gradlew archTest

# 성능 테스트만 실행
./gradlew perfTest

# 특정 태그 조합 (임시)
./gradlew test -Ptags=unit,integration
./gradlew test -Ptags=domain,application
```

---

## CI/CD 파이프라인 통합

### 1. GitHub Actions 예시

**.github/workflows/pr-build.yml**

```yaml
name: PR Build

on:
  pull_request:
    branches: [ main, develop ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}

      # PR 빌드: fast + boundary (목표 2분)
      - name: Run PR Tests
        run: ./gradlew prTest --no-daemon

      - name: Publish Test Report
        uses: mikepenz/action-junit-report@v3
        if: always()
        with:
          report_paths: '**/build/test-results/test/TEST-*.xml'
```

---

**.github/workflows/main-build.yml**

```yaml
name: Main Build

on:
  push:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}

      # 메인 빌드: fast + boundary + integration (목표 7분)
      - name: Run Main Tests
        run: ./gradlew mainTest --no-daemon

      # ArchUnit 별도 실행
      - name: Run Architecture Tests
        run: ./gradlew archTest --no-daemon

      - name: Publish Test Report
        uses: mikepenz/action-junit-report@v3
        if: always()
        with:
          report_paths: '**/build/test-results/test/TEST-*.xml'
```

---

**.github/workflows/deploy.yml**

```yaml
name: Deploy

on:
  workflow_dispatch:  # 수동 트리거

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      # 배포 전: 전체 테스트 (목표 15분)
      - name: Run Full Tests
        run: ./gradlew fullTest --no-daemon

      - name: Run Performance Tests
        run: ./gradlew perfTest --no-daemon

      - name: Publish Test Report
        uses: mikepenz/action-junit-report@v3
        if: always()
        with:
          report_paths: '**/build/test-results/test/TEST-*.xml'
```

---

### 2. GitLab CI 예시

**.gitlab-ci.yml**

```yaml
stages:
  - test
  - integration
  - deploy

# PR 단계: 빠른 테스트
pr-test:
  stage: test
  script:
    - ./gradlew prTest --no-daemon
  only:
    - merge_requests
  artifacts:
    reports:
      junit: '**/build/test-results/test/TEST-*.xml'

# 메인 브랜치: 통합 테스트 포함
main-test:
  stage: integration
  script:
    - ./gradlew mainTest --no-daemon
    - ./gradlew archTest --no-daemon
  only:
    - main
  artifacts:
    reports:
      junit: '**/build/test-results/test/TEST-*.xml'

# 배포 전: 전체 테스트
deploy-test:
  stage: deploy
  script:
    - ./gradlew fullTest --no-daemon
    - ./gradlew perfTest --no-daemon
  when: manual
  only:
    - main
  artifacts:
    reports:
      junit: '**/build/test-results/test/TEST-*.xml'
```

---

## 성능 최적화

### 1. 병렬 실행

**build.gradle**

```gradle
test {
    useJUnitPlatform()

    // ✅ 병렬 실행 활성화
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1

    // ✅ JUnit 5 병렬 실행
    systemProperty 'junit.jupiter.execution.parallel.enabled', 'true'
    systemProperty 'junit.jupiter.execution.parallel.mode.default', 'concurrent'
    systemProperty 'junit.jupiter.execution.parallel.mode.classes.default', 'concurrent'

    // ✅ 병렬 스레드 수 (CPU 코어 수의 2배)
    systemProperty 'junit.jupiter.execution.parallel.config.strategy', 'dynamic'
    systemProperty 'junit.jupiter.execution.parallel.config.dynamic.factor', '2'
}
```

**테스트 클래스에서 제어**

```java
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)  // 병렬 실행 허용
class OrderTest {
    // ...
}

@Execution(ExecutionMode.SAME_THREAD)  // 순차 실행 (DB 공유 시)
class OrderPersistenceAdapterTest {
    // ...
}
```

---

### 2. Testcontainers 재사용

**IntegrationTestBase.java**

```java
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);  // ✅ 재사용 활성화 (5s → 1s)

    static {
        postgres.start();  // ✅ 명시적 시작
    }
}
```

**~/.testcontainers.properties**

```properties
# Testcontainers 재사용 글로벌 활성화
testcontainers.reuse.enable=true
```

---

### 3. Spring Context 캐싱

**테스트 설정 공유**

```java
// ✅ Good: 동일한 설정 → Context 재사용
@SpringBootTest(classes = TestApplication.class)
class Test1 { }

@SpringBootTest(classes = TestApplication.class)  // ✅ 같은 설정
class Test2 { }

// ❌ Bad: 다른 설정 → Context 재생성
@SpringBootTest(classes = TestApplication.class)
class Test1 { }

@SpringBootTest(classes = AnotherApplication.class)  // ❌ 다른 설정
class Test2 { }
```

**@DirtiesContext 최소화**

```java
// ❌ Bad: Context 항상 재생성 (느림)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderE2ETest {
    // ...
}

// ✅ Good: Context 재사용
class OrderE2ETest {
    // @DirtiesContext 없음 → 빠름
}
```

---

### 4. 조건부 테스트 실행

**환경 기반 실행**

```java
import org.junit.jupiter.api.condition.*;

@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
@Test
void runOnlyInCI() {
    // CI 환경에서만 실행
}

@DisabledIfEnvironmentVariable(named = "SKIP_SLOW_TESTS", matches = "true")
@Test
void slowTest() {
    // 로컬 개발 시 스킵 가능
}
```

**OS 기반 실행**

```java
@EnabledOnOs(OS.LINUX)
@Test
void linuxOnlyTest() {
    // Linux에서만 실행
}

@DisabledOnOs(OS.WINDOWS)
@Test
void notOnWindows() {
    // Windows 제외
}
```

---

## 실전 예제

### 시나리오: 멀티모듈 프로젝트 테스트 전략

#### 프로젝트 구조

```
project-root/
├── domain/
│   └── src/test/java/
│       └── OrderTest.java              @UnitTest
│
├── application/
│   └── src/test/java/
│       └── CreateOrderServiceTest.java @UnitTest
│
└── adapter/
    ├── out/persistence/
    │   └── src/test/java/
    │       └── OrderPersistenceAdapterTest.java  @IntegrationTest
    │
    └── in/web/
        └── src/test/java/
            ├── OrderRestControllerTest.java      @BoundaryTest
            └── OrderE2ETest.java                 @E2ETest
```

---

#### 태그 적용

**Domain Layer**

```java
@UnitTest  // @Tag("unit") + @Tag("fast")
class OrderTest {
    @Test
    void createOrder_ShouldWork() { }
}
```

**Application Layer**

```java
@UnitTest
class CreateOrderServiceTest {
    @Test
    void execute_ShouldCreateOrder() { }
}
```

**Adapter - Persistence**

```java
@IntegrationTest  // @Tag("integration") + @Tag("slow")
@Testcontainers
class OrderPersistenceAdapterTest {
    @Test
    void save_ShouldPersist() { }
}
```

**Adapter - REST (Boundary)**

```java
@BoundaryTest  // @Tag("boundary") + @Tag("integration")
@WebMvcTest(OrderRestController.class)
class OrderRestControllerTest {
    @Test
    void createOrder_ShouldCallUseCase() { }
}
```

**E2E Test**

```java
@E2ETest  // @Tag("e2e") + @Tag("slow")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class OrderE2ETest {
    @Test
    void createOrder_FullFlow() { }
}
```

---

#### 실행 시나리오

```bash
# 로컬 개발: 단위 테스트만 (30초)
./gradlew test -Ptags=fast

# PR 빌드: 단위 + 경계 (2분)
./gradlew prTest

# 메인 빌드: + 통합 테스트 (7분)
./gradlew mainTest

# 배포 전: 전체 (15분)
./gradlew fullTest
```

---

#### 성능 측정 결과

| 실행 범위 | 태그 | 테스트 수 | 실행 시간 | 사용 시점 |
|----------|------|----------|----------|----------|
| **Fast** | `fast` | 150 | 30초 | 로컬 개발 |
| **PR** | `fast, boundary` | 200 | 2분 | PR 빌드 |
| **Main** | `fast, boundary, integration` | 280 | 7분 | 메인 빌드 |
| **Full** | 모두 | 320 | 15분 | 배포 전 |

---

## 요약

### 핵심 원칙

| 태그 | 설명 | 실행 시간 | CI/CD |
|------|------|----------|-------|
| **unit** | 단위 테스트 | < 100ms | 항상 |
| **boundary** | 모듈 경계 | ~500ms | PR 이상 |
| **integration** | 통합 테스트 | ~1-2s | 메인 이상 |
| **e2e** | 전체 시스템 | ~3-5s | 배포 전 |
| **archunit** | 아키텍처 | ~500ms | 메인 이상 |
| **performance** | 성능 테스트 | 가변 | 선택적 |

### Gradle 태스크 전략

```gradle
prTest:    fast + boundary      (2분)
mainTest:  + integration        (7분)
fullTest:  + e2e + performance  (15분)
archTest:  archunit only        (30초)
```

### 최적화 포인트

1. **병렬 실행**: `maxParallelForks` + JUnit 5 parallel
2. **Testcontainers 재사용**: `.withReuse(true)`
3. **Spring Context 캐싱**: 설정 통일, `@DirtiesContext` 최소화
4. **선택적 실행**: 태그 기반 필터링

---

## validation

```yaml
metadata:
  layer: "testing"
  category: "multi-module-testing"
  version: "1.0"

rules:
  - "모든 테스트 클래스는 적절한 @Tag 또는 메타 어노테이션 사용"
  - "fast 태그: < 100ms, slow 태그: > 1s"
  - "PR 빌드는 fast + boundary만 실행 (2분 목표)"
  - "Testcontainers는 .withReuse(true) 활성화"

validation:
  antiPatterns:
    - "@Test.*public.*void.*@Tag"  # @Test와 @Tag 순서 (메타 어노테이션 권장)
    - "@SpringBootTest.*fast"  # SpringBootTest는 fast 태그 부적합
