# 테스트 데이터 관리 (Test Data Management)

## 📋 목차
- [개요](#개요)
- [@Sql 스크립트 전략](#sql-스크립트-전략)
- [테스트 데이터 격리](#테스트-데이터-격리)
- [트랜잭션 롤백 전략](#트랜잭션-롤백-전략)
- [Test Fixtures vs @Sql](#test-fixtures-vs-sql)
- [실전 예제](#실전-예제)

---

## 개요

### 목적

통합 테스트에서 **일관되고 재현 가능한 테스트 데이터**를 관리하여 테스트의 신뢰성과 유지보수성을 향상시킵니다.

### 문제점: 하드코딩된 테스트 데이터

❌ **Bad: 매번 다른 데이터로 테스트**

```java
@Test
void test1() {
    // 테스트마다 다른 데이터
    Order order = new Order();
    order.setCustomerId(100L);  // 임의의 값
    orderRepository.save(order);
}

@Test
void test2() {
    Order order = new Order();
    order.setCustomerId(200L);  // 다른 값
    orderRepository.save(order);
}

// 문제점:
// - 데이터 일관성 없음
// - 재현 어려움
// - 의존성 관리 복잡 (고객 ID 100이 실제 존재하는가?)
```

---

### 해결책: 체계적 테스트 데이터 관리

✅ **Good: 3가지 접근법 조합**

```java
// 1. @Sql 스크립트: 초기 데이터 셋업
@Sql("/test-data/customers.sql")

// 2. Test Fixtures: 도메인 객체 생성
Order order = OrderMother.defaultOrder();

// 3. Factory: 영속화 + 연관 데이터
OrderFactory.createPersistedOrderWithCustomer();
```

---

## @Sql 스크립트 전략

### 1. 기본 사용법

**@Sql 어노테이션**

```java
package com.company.template.order.adapter.out.persistence;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

/**
 * @Sql로 테스트 데이터 로딩
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/test-data/schema.sql")  // 스키마 초기화
@Sql("/test-data/customers.sql")  // 고객 데이터
class OrderRepositoryTest {

    @Test
    void findByCustomerId_ShouldReturnOrders() {
        // Given - customers.sql에서 고객 ID 100 로드됨

        // When
        List<Order> orders = orderRepository.findByCustomerId(100L);

        // Then
        assertThat(orders).isNotEmpty();
    }
}
```

---

### 2. 스크립트 파일 구성

**디렉토리 구조:**

```
src/
└── test/
    └── resources/
        └── test-data/
            ├── schema.sql              # 테이블 스키마
            ├── cleanup.sql             # 데이터 정리
            ├── customers.sql           # 고객 마스터 데이터
            ├── products.sql            # 상품 마스터 데이터
            └── orders/
                ├── pending-orders.sql  # 대기 중 주문
                └── confirmed-orders.sql  # 확정 주문
```

---

**schema.sql**

```sql
-- 테이블 스키마 정의
CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
```

---

**customers.sql**

```sql
-- 테스트용 고객 데이터
INSERT INTO customers (id, name, email) VALUES
    (100, 'Test Customer 1', 'test1@example.com'),
    (200, 'Test Customer 2', 'test2@example.com'),
    (300, 'Test Customer 3', 'test3@example.com')
ON CONFLICT (id) DO NOTHING;

-- 시퀀스 리셋 (다음 ID가 400부터 시작)
ALTER SEQUENCE customers_id_seq RESTART WITH 400;
```

---

**pending-orders.sql**

```sql
-- 대기 중인 주문 데이터
INSERT INTO orders (id, customer_id, status, total_amount) VALUES
    (1, 100, 'PENDING', 50000.00),
    (2, 100, 'PENDING', 75000.00),
    (3, 200, 'PENDING', 100000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO order_items (order_id, product_id, quantity, price) VALUES
    (1, 1001, 2, 10000.00),
    (1, 1002, 1, 30000.00),
    (2, 1003, 3, 25000.00),
    (3, 1001, 10, 10000.00)
ON CONFLICT DO NOTHING;

ALTER SEQUENCE orders_id_seq RESTART WITH 100;
ALTER SEQUENCE order_items_id_seq RESTART WITH 1000;
```

---

### 3. 실행 시점 제어

**클래스 레벨 vs 메서드 레벨**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/test-data/customers.sql")  // 모든 테스트 전에 실행
class OrderRepositoryTest {

    @Test
    @Sql("/test-data/orders/pending-orders.sql")  // 이 테스트 전에 추가 실행
    void findPendingOrders_ShouldReturnPendingOnly() {
        List<Order> orders = orderRepository.findByStatus("PENDING");
        assertThat(orders).hasSize(3);
    }

    @Test
    void findByCustomerId_ShouldWork() {
        // customers.sql만 로드됨 (pending-orders.sql 없음)
        List<Order> orders = orderRepository.findByCustomerId(100L);
        assertThat(orders).isEmpty();  // 주문 데이터 없음
    }
}
```

---

**실행 순서 제어**

```java
@Sql(
    scripts = {
        "/test-data/schema.sql",
        "/test-data/customers.sql",
        "/test-data/products.sql",
        "/test-data/orders/pending-orders.sql"
    },
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
    scripts = "/test-data/cleanup.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class OrderRepositoryTest {
    // ...
}
```

---

### 4. 정리 스크립트

**cleanup.sql**

```sql
-- 테스트 후 데이터 정리
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM customers;
DELETE FROM products;

-- 시퀀스 리셋
ALTER SEQUENCE customers_id_seq RESTART WITH 1;
ALTER SEQUENCE orders_id_seq RESTART WITH 1;
ALTER SEQUENCE order_items_id_seq RESTART WITH 1;
ALTER SEQUENCE products_id_seq RESTART WITH 1;
```

---

## 테스트 데이터 격리

### 1. 트랜잭션 기반 격리 (권장)

**@Transactional 자동 롤백**

```java
@DataJpaTest  // ✅ 자동으로 @Transactional 활성화
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void test1() {
        Order order = new Order();
        order.setCustomerId(100L);
        orderRepository.save(order);  // 저장

        // 테스트 종료 후 자동 롤백 → DB 깨끗한 상태 유지
    }

    @Test
    void test2() {
        // test1의 데이터가 없음 (롤백되었음)
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).isEmpty();
    }
}
```

---

### 2. 명시적 롤백 제어

**@Rollback 어노테이션**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Test
    @Rollback(false)  // ❌ 롤백 비활성화 (주의: 다음 테스트에 영향)
    void test_NoRollback() {
        Order order = new Order();
        orderRepository.save(order);
        // 데이터가 DB에 남음
    }

    @Test
    @Rollback  // ✅ 명시적 롤백 (기본값)
    void test_WithRollback() {
        Order order = new Order();
        orderRepository.save(order);
        // 테스트 후 롤백됨
    }
}
```

---

### 3. 테스트 간 격리 보장

**@DirtiesContext (느림, 최후의 수단)**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderRepositoryTest {
    // 각 테스트 후 Spring Context 재생성 (매우 느림)
    // 트랜잭션 롤백으로 해결 불가능한 경우에만 사용
}
```

---

**대안: @Sql cleanup 스크립트**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/test-data/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OrderRepositoryTest {
    // 각 테스트 후 데이터만 정리 (Context 유지 → 빠름)
}
```

---

## 트랜잭션 롤백 전략

### 1. @DataJpaTest의 트랜잭션 동작

**기본 동작:**

```java
@DataJpaTest  // @Transactional이 자동으로 적용됨
class OrderRepositoryTest {

    @Test
    void test() {
        // 1. 트랜잭션 시작
        Order order = new Order();
        orderRepository.save(order);

        // 2. 테스트 실행
        Order found = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(found).isNotNull();

        // 3. 트랜잭션 롤백 (자동)
    }
}
```

---

### 2. 중첩 트랜잭션 문제

**문제 상황:**

```java
@DataJpaTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;  // @Transactional 메서드 포함

    @Test
    void test() {
        // 외부 트랜잭션: @DataJpaTest의 @Transactional
        // 내부 트랜잭션: OrderService.createOrder()의 @Transactional

        orderService.createOrder(...);  // 중첩 트랜잭션

        // 문제: 내부 트랜잭션이 커밋되어도 외부 트랜잭션이 롤백됨
    }
}
```

---

**해결책: @Transactional 전파 설정**

```java
// OrderService.java
@Transactional(propagation = Propagation.REQUIRES_NEW)  // 새로운 트랜잭션 생성
public OrderId createOrder(CreateOrderCommand command) {
    // ...
}

// 또는 테스트에서 @Transactional 제거
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)  // 트랜잭션 비활성화
class OrderServiceTest {
    // ...
}
```

---

### 3. 수동 플러시 및 클리어

**테스트 정확성 향상**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAndLoad_ShouldWork() {
        // Given
        Order order = new Order();
        order.setCustomerId(100L);

        // When - Save
        Order saved = orderRepository.save(order);

        entityManager.flush();  // ✅ 강제로 DB에 반영
        entityManager.clear();  // ✅ 1차 캐시 비우기

        // Then - Load (DB에서 직접 조회)
        Order loaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
    }
}
```

---

## Test Fixtures vs @Sql

### 비교표

| 항목 | Test Fixtures (Java) | @Sql (SQL 스크립트) |
|------|---------------------|-------------------|
| **언어** | Java | SQL |
| **타입 안정성** | ✅ 컴파일 타임 체크 | ❌ 런타임 오류 |
| **도메인 로직** | ✅ 도메인 규칙 적용 | ❌ 직접 DB 삽입 (검증 우회) |
| **재사용성** | ✅ 메서드 조합 가능 | ⚠️ 스크립트 조합 복잡 |
| **성능** | ⚠️ JPA 오버헤드 | ✅ 빠른 Bulk Insert |
| **복잡한 관계** | ⚠️ 코드 길어짐 | ✅ SQL로 간결 |
| **리팩토링** | ✅ IDE 지원 | ❌ 수동 수정 |

---

### 사용 시나리오

#### Fixtures 사용 (권장)

```java
// ✅ 도메인 로직 검증 필요
@Test
void createOrder_ShouldValidateBusinessRules() {
    Order order = OrderMother.defaultOrder();  // 도메인 규칙 적용
    order.addItem(invalidItem);  // 예외 발생 검증
}

// ✅ 다양한 변형 필요
@Test
void differentScenarios() {
    Order pendingOrder = OrderMother.pendingOrder();
    Order confirmedOrder = OrderMother.confirmedOrder();
    Order cancelledOrder = OrderMother.cancelledOrder();
}
```

---

#### @Sql 사용

```java
// ✅ 대량 데이터 셋업
@Sql("/test-data/1000-orders.sql")  // 1000개 주문 빠르게 로드
@Test
void performanceTest() {
    List<Order> orders = orderRepository.findAll();
    assertThat(orders).hasSize(1000);
}

// ✅ 복잡한 연관 관계
@Sql("/test-data/complex-order-hierarchy.sql")  // 주문 > 아이템 > 옵션
@Test
void complexQueryTest() {
    // SQL로 복잡한 구조 빠르게 셋업
}
```

---

### 조합 전략 (Best Practice)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/test-data/customers.sql")  // ✅ 마스터 데이터는 @Sql
@Sql("/test-data/products.sql")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void saveOrder_ShouldWork() {
        // ✅ 트랜잭션 데이터는 Fixtures
        Order order = OrderMother.orderForCustomer(new CustomerId(100L));

        Order saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
    }
}
```

---

## 실전 예제

### 시나리오: 고객별 주문 조회 테스트

#### 요구사항
- 고객 3명 (ID: 100, 200, 300)
- 고객 100: 주문 2개
- 고객 200: 주문 1개
- 고객 300: 주문 없음

---

#### 방법 1: @Sql 스크립트

**test-data/customers-with-orders.sql**

```sql
-- 고객 데이터
INSERT INTO customers (id, name, email) VALUES
    (100, 'Customer 100', 'c100@example.com'),
    (200, 'Customer 200', 'c200@example.com'),
    (300, 'Customer 300', 'c300@example.com');

-- 주문 데이터
INSERT INTO orders (id, customer_id, status, total_amount) VALUES
    (1, 100, 'PENDING', 50000.00),
    (2, 100, 'CONFIRMED', 75000.00),
    (3, 200, 'PENDING', 100000.00);
```

**테스트:**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/test-data/customers-with-orders.sql")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void findByCustomerId_Customer100_ShouldReturn2Orders() {
        List<Order> orders = orderRepository.findByCustomerId(100L);
        assertThat(orders).hasSize(2);
    }

    @Test
    void findByCustomerId_Customer200_ShouldReturn1Order() {
        List<Order> orders = orderRepository.findByCustomerId(200L);
        assertThat(orders).hasSize(1);
    }

    @Test
    void findByCustomerId_Customer300_ShouldReturnEmpty() {
        List<Order> orders = orderRepository.findByCustomerId(300L);
        assertThat(orders).isEmpty();
    }
}
```

---

#### 방법 2: Test Fixtures + Factory

**OrderFactory.java**

```java
public class OrderFactory {
    private final TestEntityManager entityManager;

    public OrderFactory(TestEntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void createOrdersForCustomer(Long customerId, int count) {
        for (int i = 0; i < count; i++) {
            OrderJpaEntity order = new OrderJpaEntity();
            order.setCustomerId(customerId);
            order.setStatus("PENDING");
            order.setTotalAmount(10000.0 * (i + 1));
            entityManager.persist(order);
        }
        entityManager.flush();
    }
}
```

**테스트:**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void findByCustomerId_DifferentCustomers() {
        // Given - Factory로 데이터 생성
        OrderFactory factory = new OrderFactory(entityManager);
        factory.createOrdersForCustomer(100L, 2);
        factory.createOrdersForCustomer(200L, 1);
        // 고객 300은 주문 없음

        entityManager.clear();

        // When & Then
        assertThat(orderRepository.findByCustomerId(100L)).hasSize(2);
        assertThat(orderRepository.findByCustomerId(200L)).hasSize(1);
        assertThat(orderRepository.findByCustomerId(300L)).isEmpty();
    }
}
```

---

## 요약

### 핵심 원칙

| 전략 | 용도 | 장점 | 단점 |
|------|------|------|------|
| **@Sql** | 마스터 데이터, 대량 데이터 | 빠름, SQL 간결 | 타입 안정성 없음 |
| **Fixtures** | 트랜잭션 데이터, 도메인 검증 | 타입 안전, 도메인 규칙 | JPA 오버헤드 |
| **Factory** | 영속화 + 연관 데이터 | 편리한 관계 설정 | 복잡도 증가 |

### Best Practice

```yaml
마스터_데이터:
  방법: "@Sql 스크립트"
  예시: "customers.sql, products.sql"

트랜잭션_데이터:
  방법: "Test Fixtures (OrderMother)"
  예시: "Order order = OrderMother.defaultOrder()"

영속화_필요:
  방법: "Factory"
  예시: "OrderFactory.createPersistedOrder()"

정리:
  방법: "트랜잭션 롤백 (기본) 또는 cleanup.sql"
```

### 실행 순서

```
1. @Sql schema.sql → 스키마 초기화
2. @Sql customers.sql → 마스터 데이터
3. Test Fixtures → 트랜잭션 데이터 생성
4. 테스트 실행
5. 트랜잭션 롤백 (자동) 또는 @Sql cleanup.sql
```

---

## validation

```yaml
metadata:
  layer: "testing"
  category: "integration-testing"
  version: "1.0"

rules:
  - "@Sql은 마스터 데이터, Fixtures는 트랜잭션 데이터"
  - "트랜잭션 롤백으로 테스트 격리 (기본)"
  - "@DirtiesContext 최소화 (성능 저하)"
  - "복잡한 관계는 @Sql, 도메인 검증은 Fixtures"

validation:
  antiPatterns:
    - "@DirtiesContext.*AFTER_EACH_TEST_METHOD"  # 성능 문제
    - "@Rollback\\(false\\)"  # 테스트 격리 위반
