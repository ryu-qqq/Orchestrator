# Persistence Tests - Repository 계층 통합 테스트

**목적**: `@DataJpaTest`를 활용하여 Repository, JPA Entity, QueryDSL 쿼리를 실제 DB 환경에서 테스트

**관련 문서**:
- [Testcontainers Setup](./01_testcontainers-setup.md)
- [Long FK Strategy](../../04-persistence-layer/jpa-entity-design/01_long-fk-strategy.md)

**검증 도구**: @DataJpaTest, Testcontainers, QueryDSL

---

## 📌 핵심 원칙

### Persistence 테스트의 목표

1. **JPA 매핑 검증**: Entity ↔ DB 테이블 매핑 정확성
2. **쿼리 정확성**: JPQL, QueryDSL, Native Query 동작 확인
3. **트랜잭션 검증**: 영속성 컨텍스트, Dirty Checking, Cascade
4. **성능 검증**: N+1 문제, Index 활용, 쿼리 카운트

---

## ❌ 금지 패턴 (Anti-Patterns)

### Anti-Pattern 1: H2 In-Memory DB로 테스트

```java
// ❌ H2 In-Memory DB - PostgreSQL과 SQL 문법 차이!
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderJpaRepository repository;

    @Test
    void saveOrder() {
        OrderJpaEntity order = new OrderJpaEntity(/* ... */);
        repository.save(order); // H2에서는 성공하지만 PostgreSQL에서 실패 가능
    }
}
```

**문제점**:
- PostgreSQL JSON, Array 타입 미지원
- Index 생성 문법 차이
- Window Function, CTE 미지원

---

### Anti-Pattern 2: @SpringBootTest로 Repository 테스트

```java
// ❌ @SpringBootTest - 전체 Context 로드 (느림)
@SpringBootTest
class OrderRepositoryTest {

    @Autowired
    private OrderJpaRepository repository;

    @Test
    void saveOrder() {
        // 전체 Spring Context 로드 → 느림 (5초+)
    }
}
```

**문제점**:
- 불필요한 Bean 로드 (Controller, Service 등)
- 테스트 실행 시간 증가 (5배 이상)
- Persistence 계층만 테스트하는 것이 목적

---

## ✅ 올바른 Persistence 테스트 패턴

### 패턴 1: @DataJpaTest + Testcontainers

```java
package com.company.application.out.persistence;

import com.company.application.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderRepository Persistence 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // Testcontainers 사용
class OrderRepositoryPersistenceTest extends IntegrationTestBase {

    @Autowired
    private OrderJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager; // JPA 테스트 유틸리티

    @Test
    void saveOrder_ShouldPersistCorrectly() {
        // Given
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(1L);
        order.setStatus("PENDING");

        // When
        OrderJpaEntity saved = repository.save(order);
        entityManager.flush(); // 강제 DB 반영
        entityManager.clear(); // 영속성 컨텍스트 초기화

        // Then
        OrderJpaEntity found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void findByCustomerId_ShouldReturnOrders() {
        // Given
        OrderJpaEntity order1 = createOrder(1L, "PENDING");
        OrderJpaEntity order2 = createOrder(1L, "APPROVED");
        OrderJpaEntity order3 = createOrder(2L, "PENDING");

        repository.saveAll(List.of(order1, order2, order3));
        entityManager.flush();
        entityManager.clear();

        // When
        List<OrderJpaEntity> orders = repository.findByCustomerId(1L);

        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(OrderJpaEntity::getCustomerId)
            .containsOnly(1L);
    }

    private OrderJpaEntity createOrder(Long customerId, String status) {
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(customerId);
        order.setStatus(status);
        return order;
    }
}
```

**핵심 기능**:
- ✅ `@DataJpaTest`: JPA 관련 Bean만 로드 (빠름)
- ✅ `@AutoConfigureTestDatabase(replace = NONE)`: Testcontainers 사용
- ✅ `TestEntityManager`: 영속성 컨텍스트 제어
- ✅ `flush()` + `clear()`: 실제 DB 반영 검증

---

### 패턴 2: QueryDSL 쿼리 테스트

```java
package com.company.application.out.persistence;

import com.company.application.IntegrationTestBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static com.company.application.out.persistence.QOrderJpaEntity.orderJpaEntity;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryDSL 쿼리 Persistence 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QueryDslConfig.class) // QueryDSL 설정 Import
class OrderQueryDslTest extends IntegrationTestBase {

    @Autowired
    private JPAQueryFactory queryFactory;

    @Autowired
    private OrderJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findOrdersByCustomerIdAndStatus_ShouldReturnFilteredResults() {
        // Given
        repository.saveAll(List.of(
            createOrder(1L, "PENDING"),
            createOrder(1L, "APPROVED"),
            createOrder(2L, "PENDING")
        ));
        entityManager.flush();
        entityManager.clear();

        // When
        List<OrderJpaEntity> results = queryFactory
            .selectFrom(orderJpaEntity)
            .where(
                orderJpaEntity.customerId.eq(1L)
                .and(orderJpaEntity.status.eq("PENDING"))
            )
            .fetch();

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCustomerId()).isEqualTo(1L);
        assertThat(results.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void countOrdersByStatus_ShouldReturnCorrectCount() {
        // Given
        repository.saveAll(List.of(
            createOrder(1L, "PENDING"),
            createOrder(2L, "PENDING"),
            createOrder(3L, "APPROVED")
        ));
        entityManager.flush();

        // When
        Long count = queryFactory
            .select(orderJpaEntity.count())
            .from(orderJpaEntity)
            .where(orderJpaEntity.status.eq("PENDING"))
            .fetchOne();

        // Then
        assertThat(count).isEqualTo(2);
    }

    private OrderJpaEntity createOrder(Long customerId, String status) {
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(customerId);
        order.setStatus(status);
        return order;
    }
}
```

---

## 🎯 실전 예제: Advanced Persistence Tests

### ✅ Example 1: N+1 문제 검증

```java
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import jakarta.persistence.EntityManager;

/**
 * N+1 문제 검증 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderNPlusOneTest extends IntegrationTestBase {

    @Autowired
    private OrderJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findAllOrders_WithLazyLoading_ShouldCauseNPlusOne() {
        // Given
        createOrderWithItems(5); // 5개 Order, 각 3개 Items
        entityManager.flush();
        entityManager.clear();

        // When
        Statistics stats = getStatistics();
        stats.clear();

        List<OrderJpaEntity> orders = repository.findAll(); // 1번 쿼리
        for (OrderJpaEntity order : orders) {
            order.getItems().size(); // N번 쿼리 (Lazy Loading)
        }

        // Then - N+1 문제 발생
        assertThat(stats.getPrepareStatementCount()).isGreaterThan(1);
    }

    @Test
    void findAllOrdersWithItems_WithFetchJoin_ShouldResolveNPlusOne() {
        // Given
        createOrderWithItems(5);
        entityManager.flush();
        entityManager.clear();

        // When
        Statistics stats = getStatistics();
        stats.clear();

        List<OrderJpaEntity> orders = repository.findAllWithItems(); // Fetch Join
        for (OrderJpaEntity order : orders) {
            order.getItems().size(); // 추가 쿼리 없음
        }

        // Then - 1번 쿼리만 실행
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    private void createOrderWithItems(int count) {
        for (int i = 0; i < count; i++) {
            OrderJpaEntity order = new OrderJpaEntity();
            order.setCustomerId((long) i);
            order.setItems(List.of(
                createItem("Item1"),
                createItem("Item2"),
                createItem("Item3")
            ));
            repository.save(order);
        }
    }

    private OrderItemJpaEntity createItem(String productName) {
        OrderItemJpaEntity item = new OrderItemJpaEntity();
        item.setProductName(productName);
        return item;
    }

    private Statistics getStatistics() {
        return entityManager.getEntityManager()
            .getEntityManagerFactory()
            .unwrap(org.hibernate.SessionFactory.class)
            .getStatistics();
    }
}
```

---

### ✅ Example 2: Cascade 및 OrphanRemoval 검증

```java
/**
 * Cascade 및 OrphanRemoval 검증 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderCascadeTest extends IntegrationTestBase {

    @Autowired
    private OrderJpaRepository repository;

    @Autowired
    private OrderItemJpaRepository itemRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveOrder_WithCascade_ShouldPersistItems() {
        // Given
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(1L);

        OrderItemJpaEntity item1 = createItem("Item1", order);
        OrderItemJpaEntity item2 = createItem("Item2", order);
        order.setItems(List.of(item1, item2));

        // When
        OrderJpaEntity saved = repository.save(order); // Cascade.PERSIST
        entityManager.flush();
        entityManager.clear();

        // Then - Items도 함께 저장됨
        OrderJpaEntity found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getItems()).hasSize(2);
    }

    @Test
    void removeItemFromOrder_WithOrphanRemoval_ShouldDeleteItem() {
        // Given
        OrderJpaEntity order = createOrderWithItems(2);
        entityManager.flush();
        Long itemIdToRemove = order.getItems().get(0).getId();

        // When
        order.getItems().remove(0); // OrphanRemoval = true
        repository.save(order);
        entityManager.flush();
        entityManager.clear();

        // Then - Item이 DB에서 삭제됨
        assertThat(itemRepository.findById(itemIdToRemove)).isEmpty();
    }

    private OrderJpaEntity createOrderWithItems(int itemCount) {
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(1L);

        List<OrderItemJpaEntity> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            items.add(createItem("Item" + i, order));
        }
        order.setItems(items);

        return repository.save(order);
    }

    private OrderItemJpaEntity createItem(String name, OrderJpaEntity order) {
        OrderItemJpaEntity item = new OrderItemJpaEntity();
        item.setProductName(name);
        item.setOrder(order);
        return item;
    }
}
```

---

### ✅ Example 3: Optimistic Locking 검증

```java
import jakarta.persistence.OptimisticLockException;

/**
 * Optimistic Locking 검증 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderOptimisticLockingTest extends IntegrationTestBase {

    @Autowired
    private OrderJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void updateOrder_WithStaleVersion_ShouldThrowOptimisticLockException() {
        // Given
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(1L);
        order.setStatus("PENDING");
        OrderJpaEntity saved = repository.save(order);
        entityManager.flush();

        // Load order in two separate transactions
        OrderJpaEntity order1 = repository.findById(saved.getId()).orElseThrow();
        OrderJpaEntity order2 = repository.findById(saved.getId()).orElseThrow();

        // When - Transaction 1 updates first
        order1.setStatus("APPROVED");
        repository.save(order1);
        entityManager.flush();

        // Then - Transaction 2 fails with OptimisticLockException
        order2.setStatus("REJECTED");
        assertThatThrownBy(() -> {
            repository.save(order2);
            entityManager.flush();
        }).isInstanceOf(OptimisticLockException.class);
    }
}
```

---

## 🔧 고급 Persistence 테스트 패턴

### 패턴 1: Custom Repository 구현 테스트

```java
/**
 * Custom Repository 구현 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderRepositoryImpl.class) // Custom Repository 구현체
class OrderCustomRepositoryTest extends IntegrationTestBase {

    @Autowired
    private OrderRepository repository; // Custom Repository

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findOrdersWithDynamicFilters_ShouldApplyAllFilters() {
        // Given
        repository.saveAll(List.of(
            createOrder(1L, "PENDING", LocalDate.now()),
            createOrder(1L, "APPROVED", LocalDate.now().minusDays(1)),
            createOrder(2L, "PENDING", LocalDate.now())
        ));
        entityManager.flush();
        entityManager.clear();

        // When
        OrderSearchCriteria criteria = OrderSearchCriteria.builder()
            .customerId(1L)
            .status("PENDING")
            .startDate(LocalDate.now().minusDays(1))
            .build();

        List<OrderDto> results = repository.findOrdersWithFilters(criteria);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).customerId()).isEqualTo(1L);
        assertThat(results.get(0).status()).isEqualTo("PENDING");
    }
}
```

---

### 패턴 2: Batch Insert/Update 성능 테스트

```java
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Batch Insert 성능 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderBatchInsertTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderJpaRepository repository;

    @Test
    void batchInsert_ShouldBeFasterThanIndividualInserts() {
        int count = 1000;

        // Measure individual inserts
        long individualStart = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            repository.save(createOrder((long) i, "PENDING"));
        }
        long individualTime = System.currentTimeMillis() - individualStart;

        // Measure batch insert
        long batchStart = System.currentTimeMillis();
        batchInsertOrders(count);
        long batchTime = System.currentTimeMillis() - batchStart;

        // Then - Batch insert should be significantly faster
        System.out.println("Individual: " + individualTime + "ms, Batch: " + batchTime + "ms");
        assertThat(batchTime).isLessThan(individualTime / 2);
    }

    private void batchInsertOrders(int count) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO orders (customer_id, status) VALUES (?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, (long) i);
                    ps.setString(2, "PENDING");
                }

                @Override
                public int getBatchSize() {
                    return count;
                }
            }
        );
    }
}
```

---

## 📋 Persistence 테스트 체크리스트

### 기본 검증
- [ ] CRUD 작동 (Create, Read, Update, Delete)
- [ ] Entity 매핑 (Column, Table, Constraint)
- [ ] 트랜잭션 경계 (Commit, Rollback)

### 성능 검증
- [ ] N+1 문제 (Fetch Join, DTO Projection)
- [ ] Batch Insert/Update
- [ ] Index 활용 (EXPLAIN ANALYZE)

### 고급 검증
- [ ] Cascade, OrphanRemoval
- [ ] Optimistic Locking
- [ ] Custom Repository 구현
- [ ] QueryDSL 동적 쿼리

---

## 🛠️ Gradle 설정

**`build.gradle`**:
```gradle
dependencies {
    // JPA & QueryDSL
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'com.querydsl:querydsl-jpa:5.0.0:jakarta'
    annotationProcessor 'com.querydsl:querydsl-apt:5.0.0:jakarta'

    // Testcontainers
    testImplementation 'org.testcontainers:postgresql'

    // Test Utils
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

## 📚 참고 자료

- [Spring Data JPA Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.spring-boot-applications.autoconfigured-spring-data-jpa)
- [QueryDSL Reference](http://querydsl.com/static/querydsl/latest/reference/html/)
- [Hibernate Statistics](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#statistics)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
