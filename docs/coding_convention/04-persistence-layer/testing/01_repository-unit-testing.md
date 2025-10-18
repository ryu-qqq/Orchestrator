# Repository Unit Testing - @DataJpaTest + H2
`04-persistence-layer/testing/01_repository-unit-testing.md`

> Repository의 **빠른 단위 테스트**를 위한 **@DataJpaTest + H2 In-Memory** 전략입니다.
> CI/CD에서 필수로 실행되며, 개발 중 빠른 피드백을 제공합니다.

---

## 📌 핵심 원칙

### @DataJpaTest 특징

1. **Spring Data JPA 슬라이스 테스트**: JPA 관련 빈만 로드 (빠름)
2. **In-Memory H2**: Real DB 없이 테스트 (Docker 불필요)
3. **트랜잭션 자동 롤백**: `@Transactional` 기본 적용
4. **TestEntityManager 제공**: JPA 직접 조작 가능

---

## ❌ 금지 패턴

### Anti-Pattern 1: @SpringBootTest로 Repository 테스트

```java
// ❌ 전체 Application Context 로드 (느림)
@SpringBootTest
class OrderRepositoryTest {
    @Autowired
    private OrderRepository repository;

    @Test
    void save_ShouldPersistOrder() {
        // 모든 빈이 로드되어 5초 이상 소요
    }
}
```

**문제점:**
- 전체 Spring Context 로드 (5-10초)
- Web, Service 레이어까지 불필요하게 로드
- CI/CD에서 수백 개 테스트 실행 시 시간 낭비

### Anti-Pattern 2: Real DB 사용 (단위 테스트에서)

```java
// ❌ Real PostgreSQL 필요 (Docker 의존성)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {
    // PostgreSQL이 없으면 실패
}
```

**문제점:**
- Docker/DB 설치 필요 → 로컬 개발 환경 복잡
- CI/CD에서 Testcontainers 필요 → 느림
- 단위 테스트 목적에 과도한 설정

---

## ✅ Repository 단위 테스트 패턴

### 패턴 1: 기본 CRUD 테스트

```java
package com.company.adapter.out.persistence.repository;

import com.company.adapter.out.persistence.entity.OrderEntity;
import com.company.adapter.out.persistence.entity.OrderStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderRepository 단위 테스트 (H2 In-Memory)
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@Tag("unit")
class OrderRepositoryUnitTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_WithValidOrder_ShouldPersist() {
        // Given
        OrderEntity order = OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(10000L)
            .build();

        // When
        OrderEntity saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear(); // 1차 캐시 초기화

        // Then
        OrderEntity found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCustomerId()).isEqualTo(1L);
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void findByCustomerId_WithExistingOrders_ShouldReturnList() {
        // Given
        entityManager.persist(OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(10000L)
            .build());
        entityManager.persist(OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.APPROVED)
            .totalAmount(20000L)
            .build());
        entityManager.flush();

        // When
        var orders = orderRepository.findByCustomerId(1L);

        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(OrderEntity::getCustomerId)
            .containsOnly(1L);
    }

    @Test
    void delete_WithExistingOrder_ShouldRemove() {
        // Given
        OrderEntity order = entityManager.persist(OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(10000L)
            .build());
        Long orderId = order.getId();
        entityManager.flush();

        // When
        orderRepository.deleteById(orderId);
        entityManager.flush();

        // Then
        assertThat(orderRepository.findById(orderId)).isEmpty();
    }
}
```

---

### 패턴 2: QueryDSL 동적 쿼리 테스트

```java
package com.company.adapter.out.persistence.repository;

import com.company.adapter.out.persistence.entity.OrderEntity;
import com.company.adapter.out.persistence.entity.OrderStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static com.company.adapter.out.persistence.entity.QOrderEntity.orderEntity;
import static org.assertj.core.api.Assertions.*;

/**
 * OrderQueryRepository 단위 테스트 (QueryDSL)
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@Tag("unit")
class OrderQueryRepositoryUnitTest {

    @Autowired
    private TestEntityManager entityManager;

    private JPAQueryFactory queryFactory;

    @BeforeEach
    void setUp() {
        EntityManager em = entityManager.getEntityManager();
        queryFactory = new JPAQueryFactory(em);
    }

    @Test
    void findByStatusAndCustomer_WithMultipleConditions_ShouldFilter() {
        // Given
        entityManager.persist(OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(10000L)
            .build());
        entityManager.persist(OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.APPROVED)
            .totalAmount(20000L)
            .build());
        entityManager.persist(OrderEntity.builder()
            .customerId(2L)
            .status(OrderStatus.PENDING)
            .totalAmount(30000L)
            .build());
        entityManager.flush();

        // When
        var result = queryFactory
            .selectFrom(orderEntity)
            .where(
                orderEntity.customerId.eq(1L),
                orderEntity.status.eq(OrderStatus.PENDING)
            )
            .fetch();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalAmount()).isEqualTo(10000L);
    }

    @Test
    void findByAmountRange_WithMinMax_ShouldFilterByRange() {
        // Given
        entityManager.persist(OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(5000L)
            .build());
        entityManager.persist(OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(15000L)
            .build());
        entityManager.persist(OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(25000L)
            .build());
        entityManager.flush();

        // When
        var result = queryFactory
            .selectFrom(orderEntity)
            .where(
                orderEntity.totalAmount.between(10000L, 20000L)
            )
            .fetch();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalAmount()).isEqualTo(15000L);
    }
}
```

---

### 패턴 3: JPA 연관관계 테스트

```java
/**
 * Order-OrderLine 연관관계 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@DataJpaTest
@Tag("unit")
class OrderRelationshipTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void orderWithLines_WhenSaved_ShouldCascadePersist() {
        // Given
        OrderEntity order = OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(30000L)
            .build();

        OrderLineEntity line1 = OrderLineEntity.builder()
            .productId(101L)
            .quantity(2)
            .price(10000L)
            .build();

        OrderLineEntity line2 = OrderLineEntity.builder()
            .productId(102L)
            .quantity(1)
            .price(10000L)
            .build();

        order.addLine(line1);
        order.addLine(line2);

        // When
        entityManager.persist(order);
        entityManager.flush();
        entityManager.clear();

        // Then
        OrderEntity found = entityManager.find(OrderEntity.class, order.getId());
        assertThat(found.getLines()).hasSize(2);
        assertThat(found.getLines())
            .extracting(OrderLineEntity::getProductId)
            .containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    void deleteOrder_WithCascade_ShouldRemoveLines() {
        // Given
        OrderEntity order = OrderEntity.builder()
            .customerId(1L)
            .status(OrderStatus.PENDING)
            .totalAmount(20000L)
            .build();

        order.addLine(OrderLineEntity.builder()
            .productId(101L)
            .quantity(2)
            .price(10000L)
            .build());

        entityManager.persist(order);
        Long orderId = order.getId();
        entityManager.flush();
        entityManager.clear();

        // When
        OrderEntity toDelete = entityManager.find(OrderEntity.class, orderId);
        entityManager.remove(toDelete);
        entityManager.flush();
        entityManager.clear();

        // Then
        OrderEntity deleted = entityManager.find(OrderEntity.class, orderId);
        assertThat(deleted).isNull();
    }
}
```

---

## 🎯 TestEntityManager 활용 팁

### 1. 1차 캐시 초기화 (중요!)

```java
@Test
void test() {
    entityManager.persist(order);
    entityManager.flush();
    entityManager.clear(); // ✅ 1차 캐시 초기화 → DB에서 다시 조회

    OrderEntity found = orderRepository.findById(order.getId()).orElseThrow();
    // 실제 DB 조회 검증
}
```

**이유:**
- `entityManager.persist()` 후 `findById()`는 1차 캐시에서 조회
- `clear()` 없이 테스트하면 실제 SQL 실행 검증 안 됨

### 2. 테스트 데이터 준비

```java
@Test
void test() {
    // ✅ entityManager로 데이터 준비 (Repository 사용 안 함)
    OrderEntity order = entityManager.persist(OrderEntity.builder()
        .customerId(1L)
        .status(OrderStatus.PENDING)
        .build());
    entityManager.flush();

    // Repository 메서드 테스트
    var result = orderRepository.findByCustomerId(1L);
    assertThat(result).hasSize(1);
}
```

### 3. SQL 로그 확인

```yaml
# src/test/resources/application-test.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

---

## 📋 단위 테스트 체크리스트

- [ ] `@DataJpaTest` 어노테이션 사용
- [ ] `@Tag("unit")` 태그 추가
- [ ] `TestEntityManager.flush()` + `clear()` 사용
- [ ] 1차 캐시 초기화 확인
- [ ] 트랜잭션 자동 롤백 활용
- [ ] H2 호환성 검증 (PostgreSQL 전용 함수 제외)

---

## 🚫 단위 테스트에서 피해야 할 것

- ❌ `@SpringBootTest` 사용 (느림)
- ❌ Real DB 의존성 (Docker 필요)
- ❌ 외부 API 호출
- ❌ DB-Specific 함수 (PostgreSQL JSON, MySQL Full-Text)
- ❌ Flyway Migration 검증 (통합 테스트에서)

---

## 📚 다음 문서

- [02. Testcontainers 통합 테스트](./02_testcontainers-integration.md) - Real DB 검증
- [03. 테스트 태그 전략](./03_test-tags-strategy.md) - CI/CD 통합

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
