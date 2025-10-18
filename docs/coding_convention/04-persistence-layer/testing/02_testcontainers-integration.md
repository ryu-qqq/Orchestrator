# Testcontainers Integration Testing - Real DB
`04-persistence-layer/testing/02_testcontainers-integration.md`

> **Real Database** 환경에서 검증이 필요한 경우를 위한 **Testcontainers** 통합 테스트 가이드입니다.
> Docker를 활용하여 PostgreSQL, MySQL, Redis 등을 테스트합니다.

---

## 📌 핵심 원칙

### Testcontainers란?

- **Docker 기반 통합 테스트**: Real DB를 Docker Container로 자동 실행
- **격리된 환경**: 각 테스트마다 깨끗한 DB 환경
- **CI/CD 호환**: GitHub Actions, Jenkins 등 Docker 환경에서 실행
- **다양한 DB 지원**: PostgreSQL, MySQL, Redis, MongoDB 등

---

## ⚖️ 언제 Testcontainers를 사용할까?

### ✅ Testcontainers 필수 시나리오

| 검증 대상 | 이유 | 예시 |
|----------|------|------|
| **DB 제약조건** | H2는 완벽히 재현 안 됨 | FK, Unique Key, Check Constraint |
| **DB 전용 함수** | PostgreSQL JSON, MySQL Full-Text | `jsonb_array_elements()`, `MATCH()` |
| **Flyway Migration** | 실제 Schema 변경 검증 | DDL 스크립트 실행 검증 |
| **트랜잭션 격리** | DB별 동작 차이 | `READ_COMMITTED` vs `REPEATABLE_READ` |
| **성능 테스트** | Real DB 성능 측정 | Pagination, N+1 쿼리 검증 |

### ❌ Testcontainers 불필요 시나리오

- 단순 CRUD 테스트 → `@DataJpaTest` + H2로 충분
- JPA Entity 매핑 검증 → H2로 검증 가능
- QueryDSL 문법 검증 → H2로 검증 가능

---

## 🏗️ Testcontainers 설정

### 1. 의존성 추가 (build.gradle.kts)

```kotlin
dependencies {
    // Testcontainers Core
    testImplementation("org.testcontainers:testcontainers:1.19.0")
    testImplementation("org.testcontainers:junit-jupiter:1.19.0")

    // Database Containers
    testImplementation("org.testcontainers:postgresql:1.19.0")
    testImplementation("org.testcontainers:mysql:1.19.0")
    testImplementation("org.testcontainers:redis:1.19.0")
}
```

### 2. Base Configuration 클래스

```java
package com.company.adapter.out.persistence.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers 기반 통합 테스트 설정
 * PostgreSQL 15 Container 자동 실행
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@Testcontainers
public abstract class PostgresIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true); // ✅ Container 재사용 (빠른 실행)

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**핵심 설정:**
- `@Testcontainers`: JUnit 5 자동 Container 관리
- `@Container`: Container 필드 자동 시작/종료
- `@DynamicPropertySource`: Spring Property 동적 주입
- `withReuse(true)`: 여러 테스트에서 Container 재사용 (속도 향상)

---

## ✅ Testcontainers 테스트 패턴

### 패턴 1: DB 제약조건 검증 (FK, Unique)

```java
package com.company.adapter.out.persistence.repository;

import com.company.adapter.out.persistence.entity.OrderEntity;
import com.company.adapter.out.persistence.entity.OrderStatus;
import com.company.adapter.out.persistence.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;

/**
 * Order Repository 통합 테스트 (PostgreSQL Testcontainers)
 * DB 제약조건 검증
 *
 * @author development-team
 * @since 1.0.0
 */
@Tag("integration")
class OrderRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void save_WithDuplicateOrderNumber_ShouldThrowUniqueConstraintException() {
        // Given
        OrderEntity order1 = OrderEntity.builder()
            .orderNumber("ORDER-001")
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(10000L)
            .build();
        orderRepository.save(order1);

        // When & Then
        OrderEntity order2 = OrderEntity.builder()
            .orderNumber("ORDER-001") // 중복 주문번호
            .customerId(2L)
            .status(OrderStatus.PENDING)
            .totalAmount(20000L)
            .build();

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order2))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("unique constraint")
            .hasMessageContaining("order_number");
    }

    @Test
    void delete_WithExistingOrderLines_ShouldThrowForeignKeyException() {
        // Given
        OrderEntity order = OrderEntity.builder()
            .orderNumber("ORDER-002")
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(10000L)
            .build();
        order.addLine(OrderLineEntity.builder()
            .productId(101L)
            .quantity(1)
            .price(10000L)
            .build());
        orderRepository.save(order);

        // When & Then
        // FK 제약조건: OrderLine이 있으면 Order 삭제 불가 (CASCADE 없는 경우)
        assertThatThrownBy(() -> {
            // Order만 삭제 시도 (OrderLine은 그대로)
            orderRepository.deleteById(order.getId());
            orderRepository.flush();
        })
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("foreign key");
    }
}
```

---

### 패턴 2: DB 전용 함수 테스트 (PostgreSQL JSON)

```java
/**
 * PostgreSQL JSON 함수 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@Tag("integration")
@Tag("database")
class OrderJsonQueryTest extends PostgresIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void queryJsonField_WithPostgreSQLJsonbFunction_ShouldExtractValue() {
        // Given: JSON 컬럼에 메타데이터 저장
        OrderEntity order = OrderEntity.builder()
            .orderNumber("ORDER-003")
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(10000L)
            .metadata("{\"source\": \"mobile\", \"campaign\": \"summer-sale\"}")
            .build();
        entityManager.persist(order);
        entityManager.flush();

        // When: PostgreSQL jsonb 함수로 쿼리
        String jpql = """
            SELECT o FROM OrderEntity o
            WHERE jsonb_extract_path_text(o.metadata, 'source') = :source
        """;

        List<OrderEntity> results = entityManager.createQuery(jpql, OrderEntity.class)
            .setParameter("source", "mobile")
            .getResultList();

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrderNumber()).isEqualTo("ORDER-003");
    }
}
```

---

### 패턴 3: Flyway Migration 검증

```java
/**
 * Flyway Migration 통합 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@Tag("integration")
@Tag("database")
class FlywayMigrationTest extends PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayMigration_ShouldApplyAllScripts() throws Exception {
        // When: Flyway가 자동으로 Migration 실행됨 (Spring Boot 기본 동작)

        // Then: 테이블 존재 검증
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery(
                 "SELECT table_name FROM information_schema.tables " +
                 "WHERE table_schema = 'public' AND table_name = 'orders'"
             )) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("table_name")).isEqualTo("orders");
        }
    }

    @Test
    void flywayMigration_ShouldCreateIndexes() throws Exception {
        // Then: Index 생성 검증
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery(
                 "SELECT indexname FROM pg_indexes " +
                 "WHERE tablename = 'orders' AND indexname = 'idx_orders_customer_id'"
             )) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("indexname")).isEqualTo("idx_orders_customer_id");
        }
    }
}
```

---

### 패턴 4: 트랜잭션 격리 수준 테스트

```java
/**
 * 트랜잭션 격리 수준 테스트 (Dirty Read, Phantom Read)
 *
 * @author development-team
 * @since 1.0.0
 */
@Tag("integration")
class TransactionIsolationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void readCommitted_ShouldPreventDirtyRead() throws Exception {
        // Given
        OrderEntity order = OrderEntity.builder()
            .orderNumber("ORDER-004")
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(10000L)
            .build();
        orderRepository.save(order);

        // When: Transaction 1에서 수정 (커밋 안 함)
        CompletableFuture<Void> tx1 = CompletableFuture.runAsync(() -> {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            template.execute(status -> {
                OrderEntity found = orderRepository.findById(order.getId()).orElseThrow();
                found.updateStatus(OrderStatus.APPROVED);
                // 여기서 sleep으로 커밋 지연
                Thread.sleep(1000);
                return null;
            });
        });

        Thread.sleep(500); // Transaction 1이 수정 중

        // Then: Transaction 2에서 조회 시 Dirty Read 방지 (PENDING 상태 유지)
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        OrderEntity found = template.execute(status ->
            orderRepository.findById(order.getId()).orElseThrow()
        );

        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING); // Dirty Read 방지

        tx1.join(); // Transaction 1 커밋 대기
    }
}
```

---

## 🚀 성능 최적화 팁

### 1. Container 재사용 (중요!)

```java
@Container
protected static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:15-alpine")
        .withReuse(true); // ✅ 여러 테스트에서 재사용
```

**효과:**
- Container 재사용 OFF: 각 테스트 클래스마다 5-10초 소요
- Container 재사용 ON: 첫 테스트만 5초, 이후 테스트는 즉시 실행

### 2. Testcontainers 설정 파일

```properties
# src/test/resources/.testcontainers.properties
testcontainers.reuse.enable=true
```

### 3. 경량 Docker 이미지 사용

```java
// ✅ Alpine 이미지 사용 (100MB vs 300MB)
new PostgreSQLContainer<>("postgres:15-alpine")

// ❌ 일반 이미지 (느림)
new PostgreSQLContainer<>("postgres:15")
```

---

## 🔄 MySQL, Redis Testcontainers

### MySQL Container

```java
@Container
protected static final MySQLContainer<?> mysql =
    new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
}
```

### Redis Container

```java
@Container
protected static final GenericContainer<?> redis =
    new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withReuse(true);

@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
}
```

---

## 📋 통합 테스트 체크리스트

- [ ] `@Tag("integration")` 태그 추가
- [ ] Base Configuration 클래스 상속
- [ ] Container 재사용 활성화 (`withReuse(true)`)
- [ ] DB 제약조건 검증
- [ ] DB 전용 함수 테스트
- [ ] Flyway Migration 검증
- [ ] 트랜잭션 격리 수준 검증

---

## 🚫 통합 테스트에서 피해야 할 것

- ❌ 모든 Repository 메서드를 통합 테스트로 검증 (단위 테스트로 충분)
- ❌ Container 재사용 비활성화 (느림)
- ❌ 무거운 Docker 이미지 사용 (Alpine 권장)

---

## 📚 다음 문서

- [03. 테스트 태그 전략](./03_test-tags-strategy.md) - CI/CD 통합
- [04. Entity 테스트 패턴](./04_entity-testing.md)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
