# Testcontainers Setup - 통합 테스트 환경 격리

**목적**: Testcontainers를 활용하여 PostgreSQL, Redis 등 외부 의존성을 격리된 Docker 환경에서 테스트

**관련 문서**:
- [API Integration Tests](./02_api-integration-tests.md)
- [Persistence Tests](./03_persistence-tests.md)

**검증 도구**: Testcontainers 1.19.0+, Docker

---

## 📌 핵심 원칙

### Testcontainers의 장점

1. **환경 격리**: 각 테스트 실행 시 독립적인 DB/Redis 컨테이너 생성
2. **일관성**: 로컬/CI 환경 동일한 테스트 결과
3. **클린업 자동화**: 테스트 완료 시 컨테이너 자동 제거
4. **버전 고정**: Production과 동일한 DB 버전 사용

---

## ❌ 금지 패턴 (Anti-Patterns)

### Anti-Pattern 1: H2 In-Memory DB로 PostgreSQL 대체

```java
// ❌ H2 In-Memory DB - PostgreSQL과 호환성 문제 발생!
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb", // ❌ H2는 PostgreSQL과 SQL 문법 차이
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OrderServiceTest {
    @Test
    void createOrder() {
        // H2에서는 성공하지만 PostgreSQL에서 실패하는 경우 발생!
    }
}
```

**문제점**:
- PostgreSQL 고유 기능 (JSON, Array 타입 등) 테스트 불가
- SQL 문법 차이로 인한 False Positive
- Production 환경과 다른 동작 가능성

---

### Anti-Pattern 2: 로컬 DB 서버 직접 사용

```java
// ❌ 로컬 PostgreSQL 서버 직접 사용
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/testdb" // ❌ 로컬 서버 의존
})
class OrderServiceTest {
    // 로컬 환경마다 DB 상태가 다를 수 있음
}
```

**문제점**:
- 로컬 환경마다 DB 상태 불일치
- 병렬 테스트 실행 시 충돌
- CI 환경 설정 복잡도 증가

---

## ✅ Testcontainers 설정

### 단계 1: Gradle 의존성 추가

```gradle
// build.gradle
dependencies {
    // Testcontainers BOM
    testImplementation platform('org.testcontainers:testcontainers-bom:1.19.3')

    // Testcontainers 모듈
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:redis' // Redis 사용 시

    // Spring Boot Testcontainers
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
}
```

---

### 단계 2: PostgreSQL Testcontainer 기본 설정

```java
package com.company.application;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers 기본 설정 (Base Test Class)
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

    /**
     * PostgreSQL 16 Container (Production과 동일 버전)
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true); // 테스트 간 컨테이너 재사용 (성능 향상)

    /**
     * 동적 프로퍼티 등록 (DataSource 설정 자동 주입)
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**핵심 기능**:
- ✅ `@Testcontainers`: JUnit 5 확장 활성화
- ✅ `@Container`: 컨테이너 라이프사이클 관리
- ✅ `@DynamicPropertySource`: Spring 설정 자동 주입
- ✅ `.withReuse(true)`: 컨테이너 재사용 (5초 → 1초)

---

### 단계 3: Redis Testcontainer 추가

```java
package com.company.application;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL + Redis Testcontainer 설정
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestWithRedis {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    /**
     * Redis 7 Container
     */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL 설정
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis 설정
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
}
```

---

## 🎯 실전 예제: Testcontainers 활용

### ✅ Example 1: Repository 통합 테스트

```java
package com.company.application.out.persistence;

import com.company.application.IntegrationTestBase;
import com.company.domain.order.Order;
import com.company.domain.order.OrderId;
import com.company.domain.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderRepository 통합 테스트 (PostgreSQL Testcontainer)
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OrderPersistenceAdapter orderPersistenceAdapter;

    @Test
    void saveAndLoadOrder_ShouldPersistCorrectly() {
        // Given
        Order order = Order.create(CustomerId.of(1L));

        // When
        orderPersistenceAdapter.saveOrder(order);

        // Then
        Optional<Order> loaded = orderPersistenceAdapter.loadOrder(order.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(order.getId());
        assertThat(loaded.get().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void updateOrderStatus_ShouldReflectChanges() {
        // Given
        Order order = Order.create(CustomerId.of(1L));
        orderPersistenceAdapter.saveOrder(order);

        // When
        order.approve();
        orderPersistenceAdapter.saveOrder(order);

        // Then
        Optional<Order> loaded = orderPersistenceAdapter.loadOrder(order.getId());
        assertThat(loaded.get().getStatus()).isEqualTo(OrderStatus.APPROVED);
    }
}
```

**테스트 결과**:
```
✅ PostgreSQL Testcontainer 자동 시작
✅ 테스트 데이터 격리 (각 테스트마다 독립 트랜잭션)
✅ 테스트 완료 후 컨테이너 자동 정리
```

---

### ✅ Example 2: Cache 통합 테스트 (Redis)

```java
package com.company.application.service;

import com.company.application.IntegrationTestWithRedis;
import com.company.domain.order.Order;
import com.company.domain.order.OrderId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache 통합 테스트 (Redis Testcontainer)
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderCacheIntegrationTest extends IntegrationTestWithRedis {

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void getOrder_ShouldCacheInRedis() {
        // Given
        OrderId orderId = OrderId.of(1L);

        // When - First call (DB hit)
        OrderResponse response1 = orderQueryService.getOrder(orderId);

        // Then - Second call (Cache hit)
        OrderResponse response2 = orderQueryService.getOrder(orderId);

        assertThat(response1).isEqualTo(response2);

        // Verify cache exists
        var cache = cacheManager.getCache("orders");
        assertThat(cache.get(orderId)).isNotNull();
    }

    @Test
    void updateOrder_ShouldEvictCache() {
        // Given
        OrderId orderId = OrderId.of(1L);
        orderQueryService.getOrder(orderId); // Cache 생성

        // When
        orderCommandService.approveOrder(orderId); // Cache Eviction

        // Then
        var cache = cacheManager.getCache("orders");
        assertThat(cache.get(orderId)).isNull();
    }
}
```

---

## 🔧 고급 Testcontainers 설정

### 설정 1: Docker Compose 통합

**`docker-compose.test.yml`**:
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

**Java 코드**:
```java
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;

/**
 * Docker Compose Testcontainer 설정
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestWithDockerCompose {

    @Container
    static DockerComposeContainer<?> environment = new DockerComposeContainer<>(
        new File("docker-compose.test.yml")
    )
    .withExposedService("postgres", 5432, Wait.forListeningPort())
    .withExposedService("redis", 6379, Wait.forListeningPort())
    .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String postgresHost = environment.getServiceHost("postgres", 5432);
        Integer postgresPort = environment.getServicePort("postgres", 5432);

        registry.add("spring.datasource.url",
            () -> String.format("jdbc:postgresql://%s:%d/testdb", postgresHost, postgresPort));
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");

        String redisHost = environment.getServiceHost("redis", 6379);
        Integer redisPort = environment.getServicePort("redis", 6379);

        registry.add("spring.data.redis.host", () -> redisHost);
        registry.add("spring.data.redis.port", () -> redisPort);
    }
}
```

---

### 설정 2: Network 공유 (Multi-Container 통신)

```java
import org.testcontainers.containers.Network;

/**
 * Network 공유 Testcontainer 설정
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestWithNetwork {

    private static final Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withNetwork(network)
        .withNetworkAliases("postgres");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withNetwork(network)
        .withNetworkAliases("redis")
        .withExposedPorts(6379);

    // 다른 컨테이너가 postgres, redis 별칭으로 접근 가능
}
```

---

## 📋 Testcontainers 체크리스트

### 기본 설정
- [ ] Gradle 의존성 추가
- [ ] `@Testcontainers` + `@Container` 사용
- [ ] `@DynamicPropertySource`로 Spring 설정 주입
- [ ] Production과 동일한 DB 버전 사용

### 성능 최적화
- [ ] `.withReuse(true)` 설정 (컨테이너 재사용)
- [ ] 공통 Base 클래스 작성
- [ ] 필요한 컨테이너만 시작

### 환경 격리
- [ ] 각 테스트 독립 트랜잭션
- [ ] `@Transactional` + `@Rollback` 활용
- [ ] 테스트 데이터 클린업 자동화

---

## 🛠️ CI/CD 통합

**`.github/workflows/integration-tests.yml`**:
```yaml
name: Integration Tests (Testcontainers)

on:
  pull_request:
    branches: [main, develop]

jobs:
  integration-tests:
    runs-on: ubuntu-latest

    services:
      docker:
        image: docker:20.10.16-dind
        options: --privileged

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Integration Tests
        run: |
          ./gradlew integrationTest

      - name: Upload Test Reports
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: integration-test-reports
          path: build/reports/tests/integrationTest
```

---

## 📚 참고 자료

- [Testcontainers Official Docs](https://www.testcontainers.org/)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.testcontainers)
- [Testcontainers Best Practices](https://www.testcontainers.org/test_framework_integration/manual_lifecycle_control/)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
