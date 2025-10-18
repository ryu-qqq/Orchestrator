# 공유 테스트 픽스처 (Shared Test Fixtures)

## 📋 목차
- [개요](#개요)
- [Test Fixture 패턴](#test-fixture-패턴)
- [Test-Fixtures 모듈 구조](#test-fixtures-모듈-구조)
- [Object Mother 패턴](#object-mother-패턴)
- [Test Data Builder 패턴](#test-data-builder-패턴)
- [Factory 패턴](#factory-패턴)
- [Fixture 재사용 전략](#fixture-재사용-전략)
- [실전 예제](#실전-예제)

---

## 개요

### 목적

멀티모듈 프로젝트에서 **테스트 데이터 생성 로직을 중앙화**하여 일관성 있고 유지보수 가능한 테스트를 작성합니다.

### 문제점: Test Fixture 중복

❌ **Bad: 각 테스트마다 객체 생성 로직 중복**

```java
// OrderTest.java
Order order = Order.create(
    new OrderId(1L),
    new CustomerId(100L)
);
order.addItem(OrderItem.create(...));

// CreateOrderServiceTest.java
Order order = Order.create(
    new OrderId(1L),
    new CustomerId(100L)
);
order.addItem(OrderItem.create(...));

// OrderPersistenceAdapterTest.java
Order order = Order.create(
    new OrderId(1L),
    new CustomerId(100L)
);
order.addItem(OrderItem.create(...));
```

**문제점:**
- 동일한 생성 로직이 3번 중복됨
- Order 생성자 변경 시 모든 테스트 수정 필요
- 테스트마다 다른 값 사용 → 일관성 부족

---

### 해결책: Test Fixture 패턴

✅ **Good: 중앙화된 Fixture 사용**

```java
// OrderFixtures.java (공유)
public class OrderFixtures {
    public static Order defaultOrder() {
        return Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );
    }
}

// 모든 테스트에서 재사용
Order order = OrderFixtures.defaultOrder();
```

**장점:**
- 테스트 데이터 생성 로직 한 곳에서 관리
- 일관성 있는 테스트 데이터
- 변경 시 한 곳만 수정

---

## Test Fixture 패턴

### 1. 패턴 선택 가이드

| 패턴 | 용도 | 사용 모듈 | 복잡도 |
|------|------|----------|--------|
| **Object Mother** | 간단한 기본 객체 | Domain, Application | ⭐ 낮음 |
| **Test Data Builder** | 복잡한 객체, 다양한 변형 | Application, Adapter | ⭐⭐ 중간 |
| **Factory** | 영속화 필요한 객체 | Adapter (통합 테스트) | ⭐⭐⭐ 높음 |

---

### 2. 패턴별 특징

#### Object Mother (객체 어머니)

**특징:**
- 가장 단순한 패턴
- 미리 정의된 객체 반환
- 변형 적음

**예시:**
```java
public class OrderMother {
    public static Order defaultOrder() { ... }
    public static Order confirmedOrder() { ... }
    public static Order cancelledOrder() { ... }
}
```

---

#### Test Data Builder (빌더)

**특징:**
- Fluent API로 유연한 객체 생성
- 다양한 변형 가능
- 복잡한 객체 구성

**예시:**
```java
public class OrderBuilder {
    public OrderBuilder withCustomerId(Long id) { ... }
    public OrderBuilder withItem(OrderItem item) { ... }
    public Order build() { ... }
}

// 사용
Order order = new OrderBuilder()
    .withCustomerId(100L)
    .withItem(item)
    .build();
```

---

#### Factory (팩토리)

**특징:**
- 영속화까지 처리
- 통합 테스트에서 사용
- EntityManager 의존

**예시:**
```java
public class OrderFactory {
    private final TestEntityManager entityManager;

    public Order createPersistedOrder() {
        Order order = OrderMother.defaultOrder();
        return entityManager.persistAndFlush(order);
    }
}
```

---

## Test-Fixtures 모듈 구조

### 1. 디렉토리 구조

```
project-root/
├── domain/
│   └── src/
│       ├── main/java/
│       └── test/java/
│           └── fixtures/
│               ├── OrderMother.java          // Object Mother
│               ├── CustomerMother.java
│               └── ProductMother.java
│
├── application/
│   └── src/
│       ├── main/java/
│       └── test/java/
│           └── fixtures/
│               ├── CommandMother.java        // Command 객체
│               ├── OrderBuilder.java         // Builder (선택적)
│               └── QueryMother.java
│
└── adapter/
    └── out/persistence/
        └── src/
            └── test/java/
                └── fixtures/
                    ├── OrderFactory.java    // 영속화 Factory
                    └── TestDataHelper.java  // @Sql 헬퍼
```

---

### 2. 독립 test-fixtures 모듈 (선택적)

**대규모 프로젝트에서 권장**

```
project-root/
├── domain/
├── application/
├── adapter/
└── test-fixtures/                           // 독립 모듈
    ├── build.gradle
    └── src/
        └── main/java/
            └── com/company/template/fixtures/
                ├── domain/
                │   ├── OrderMother.java
                │   └── CustomerMother.java
                ├── application/
                │   └── CommandMother.java
                └── adapter/
                    └── OrderFactory.java
```

**build.gradle:**

```gradle
// test-fixtures/build.gradle
dependencies {
    // 모든 모듈의 main 코드 접근
    implementation project(':domain')
    implementation project(':application')

    // Test 라이브러리
    implementation 'org.junit.jupiter:junit-jupiter'
    implementation 'org.assertj:assertj-core'
    implementation 'org.springframework.boot:spring-boot-starter-test'
}

// domain/build.gradle
dependencies {
    testImplementation project(':test-fixtures')  // 재사용
}

// application/build.gradle
dependencies {
    testImplementation project(':test-fixtures')  // 재사용
}
```

---

## Object Mother 패턴

### 1. 기본 구조

**OrderMother.java**

```java
package com.company.template.order.domain.fixtures;

import com.company.template.order.domain.model.*;

/**
 * Order Aggregate Object Mother
 *
 * - 미리 정의된 Order 객체 반환
 * - Domain 모듈 테스트에서 사용
 */
public class OrderMother {

    /**
     * 기본 Order 객체
     * - PENDING 상태
     * - 아이템 없음
     */
    public static Order defaultOrder() {
        return Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );
    }

    /**
     * 아이템이 포함된 Order
     */
    public static Order orderWithItems() {
        Order order = defaultOrder();
        order.addItem(OrderItemMother.defaultOrderItem());
        order.addItem(OrderItemMother.expensiveOrderItem());
        return order;
    }

    /**
     * 확정된 Order
     */
    public static Order confirmedOrder() {
        Order order = orderWithItems();
        order.confirm();
        return order;
    }

    /**
     * 취소된 Order
     */
    public static Order cancelledOrder() {
        Order order = confirmedOrder();
        order.cancel();
        return order;
    }

    /**
     * 특정 고객의 Order
     */
    public static Order orderForCustomer(CustomerId customerId) {
        return Order.create(
            new OrderId(1L),
            customerId
        );
    }

    /**
     * 특정 금액의 Order
     */
    public static Order orderWithTotalAmount(Price totalAmount) {
        Order order = defaultOrder();
        // totalAmount에 맞게 아이템 추가 로직
        return order;
    }
}
```

---

### 2. Value Object Mother

**OrderItemMother.java**

```java
package com.company.template.order.domain.fixtures;

import com.company.template.order.domain.model.*;

public class OrderItemMother {

    /**
     * 기본 OrderItem
     * - 상품 ID: 1001
     * - 수량: 1
     * - 가격: 10,000원
     */
    public static OrderItem defaultOrderItem() {
        return OrderItem.create(
            new ProductId(1001L),
            new Quantity(1),
            new Price(10000.0)
        );
    }

    /**
     * 고가 상품 OrderItem
     */
    public static OrderItem expensiveOrderItem() {
        return OrderItem.create(
            new ProductId(2001L),
            new Quantity(1),
            new Price(500000.0)
        );
    }

    /**
     * 대량 구매 OrderItem
     */
    public static OrderItem bulkOrderItem() {
        return OrderItem.create(
            new ProductId(1001L),
            new Quantity(100),
            new Price(10000.0)
        );
    }

    /**
     * 커스텀 OrderItem
     */
    public static OrderItem orderItem(Long productId, int quantity, double price) {
        return OrderItem.create(
            new ProductId(productId),
            new Quantity(quantity),
            new Price(price)
        );
    }
}
```

---

### 3. 사용 예시

**OrderTest.java**

```java
package com.company.template.order.domain.model;

import com.company.template.order.domain.fixtures.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    void confirm_ShouldChangeStatusToConfirmed() {
        // Given - Fixture 사용
        Order order = OrderMother.defaultOrder();

        // When
        order.confirm();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void getTotalAmount_ShouldCalculateCorrectly() {
        // Given - 복잡한 객체도 한 줄로
        Order order = OrderMother.orderWithItems();

        // When
        Price totalAmount = order.getTotalAmount();

        // Then
        assertThat(totalAmount).isEqualTo(new Price(510000.0));
    }
}
```

---

## Test Data Builder 패턴

### 1. 기본 구조

**OrderBuilder.java**

```java
package com.company.template.order.application.fixtures;

import com.company.template.order.domain.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Order Test Data Builder
 *
 * - Fluent API로 유연한 Order 생성
 * - Application/Adapter 테스트에서 사용
 */
public class OrderBuilder {

    private OrderId orderId = new OrderId(1L);
    private CustomerId customerId = new CustomerId(100L);
    private List<OrderItem> items = new ArrayList<>();
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * 기본값으로 시작
     */
    public static OrderBuilder anOrder() {
        return new OrderBuilder();
    }

    /**
     * OrderId 설정
     */
    public OrderBuilder withId(Long id) {
        this.orderId = new OrderId(id);
        return this;
    }

    /**
     * CustomerId 설정
     */
    public OrderBuilder withCustomerId(Long customerId) {
        this.customerId = new CustomerId(customerId);
        return this;
    }

    /**
     * OrderItem 추가
     */
    public OrderBuilder withItem(OrderItem item) {
        this.items.add(item);
        return this;
    }

    /**
     * 여러 OrderItem 추가
     */
    public OrderBuilder withItems(List<OrderItem> items) {
        this.items.addAll(items);
        return this;
    }

    /**
     * Status 설정
     */
    public OrderBuilder withStatus(OrderStatus status) {
        this.status = status;
        return this;
    }

    /**
     * CONFIRMED 상태로 설정
     */
    public OrderBuilder confirmed() {
        this.status = OrderStatus.CONFIRMED;
        return this;
    }

    /**
     * CANCELLED 상태로 설정
     */
    public OrderBuilder cancelled() {
        this.status = OrderStatus.CANCELLED;
        return this;
    }

    /**
     * Order 객체 생성
     */
    public Order build() {
        Order order = Order.create(orderId, customerId);

        // Items 추가
        for (OrderItem item : items) {
            order.addItem(item);
        }

        // Status 설정 (Reflection 또는 테스트 전용 메서드 필요)
        // 프로덕션 코드에서는 불가능할 수 있음
        // 이 경우 Object Mother 패턴으로 대체

        return order;
    }
}
```

---

### 2. 사용 예시

**CreateOrderServiceTest.java**

```java
package com.company.template.order.application.service;

import com.company.template.order.application.fixtures.*;
import com.company.template.order.domain.model.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CreateOrderServiceTest {

    @Test
    void example_FluentBuilder() {
        // Given - Fluent API로 복잡한 객체 생성
        Order order = OrderBuilder.anOrder()
            .withCustomerId(200L)
            .withItem(OrderItemMother.defaultOrderItem())
            .withItem(OrderItemMother.expensiveOrderItem())
            .confirmed()
            .build();

        // Then
        assertThat(order.getCustomerId()).isEqualTo(new CustomerId(200L));
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void example_CustomValues() {
        // Given - 특정 값으로 커스터마이징
        Order order = OrderBuilder.anOrder()
            .withId(999L)
            .withCustomerId(300L)
            .build();

        // Then
        assertThat(order.getOrderId()).isEqualTo(new OrderId(999L));
    }
}
```

---

## Factory 패턴

### 1. 영속화 Factory

**OrderFactory.java**

```java
package com.company.template.order.adapter.fixtures;

import com.company.template.order.adapter.out.persistence.jpa.*;
import com.company.template.order.domain.fixtures.*;
import com.company.template.order.domain.model.*;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Order 영속화 Factory
 *
 * - Adapter 통합 테스트에서 사용
 * - TestEntityManager로 실제 DB에 저장
 */
public class OrderFactory {

    private final TestEntityManager entityManager;

    public OrderFactory(TestEntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 기본 Order 영속화
     */
    public OrderJpaEntity createPersistedOrder() {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setCustomerId(100L);
        entity.setStatus("PENDING");

        return entityManager.persistAndFlush(entity);
    }

    /**
     * 아이템 포함 Order 영속화
     */
    public OrderJpaEntity createPersistedOrderWithItems() {
        OrderJpaEntity entity = createPersistedOrder();

        OrderItemJpaEntity item1 = new OrderItemJpaEntity();
        item1.setOrder(entity);
        item1.setProductId(1001L);
        item1.setQuantity(2);
        item1.setPrice(10000.0);
        entityManager.persist(item1);

        entityManager.flush();
        return entity;
    }

    /**
     * 특정 고객의 Order 영속화
     */
    public OrderJpaEntity createPersistedOrderForCustomer(Long customerId) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setCustomerId(customerId);
        entity.setStatus("PENDING");

        return entityManager.persistAndFlush(entity);
    }

    /**
     * 확정된 Order 영속화
     */
    public OrderJpaEntity createPersistedConfirmedOrder() {
        OrderJpaEntity entity = createPersistedOrder();
        entity.setStatus("CONFIRMED");

        return entityManager.persistAndFlush(entity);
    }
}
```

---

### 2. 사용 예시

**OrderPersistenceAdapterTest.java**

```java
package com.company.template.order.adapter.out.persistence;

import com.company.template.order.adapter.fixtures.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class OrderPersistenceAdapterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderJpaRepository repository;

    @Test
    void findById_ShouldReturnPersistedOrder() {
        // Given - Factory로 영속화
        OrderFactory factory = new OrderFactory(entityManager);
        OrderJpaEntity persisted = factory.createPersistedOrder();

        entityManager.clear();  // 캐시 비우기

        // When
        OrderJpaEntity found = repository.findById(persisted.getId()).orElseThrow();

        // Then
        assertThat(found.getId()).isEqualTo(persisted.getId());
        assertThat(found.getCustomerId()).isEqualTo(100L);
    }

    @Test
    void findByCustomerId_ShouldReturnCustomerOrders() {
        // Given
        OrderFactory factory = new OrderFactory(entityManager);
        factory.createPersistedOrderForCustomer(200L);
        factory.createPersistedOrderForCustomer(200L);
        factory.createPersistedOrderForCustomer(300L);  // 다른 고객

        entityManager.clear();

        // When
        List<OrderJpaEntity> orders = repository.findByCustomerId(200L);

        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders).allMatch(o -> o.getCustomerId().equals(200L));
    }
}
```

---

## Fixture 재사용 전략

### 1. 모듈 간 Fixture 공유

#### 시나리오: Domain Fixture를 Application에서 재사용

**domain/src/test/java/fixtures/OrderMother.java**

```java
// Domain 모듈에서 정의
public class OrderMother {
    public static Order defaultOrder() { ... }
}
```

**application/build.gradle**

```gradle
dependencies {
    // ✅ Domain의 test 코드에 접근
    testImplementation project(':domain')

    // ❌ 별도 설정 없이는 domain의 test fixtures 접근 불가
    // 해결책: test-fixtures 모듈 또는 testFixtures 소스셋 사용
}
```

---

#### 해결책 1: `testFixtures` 소스셋 (Gradle 5.6+)

**domain/build.gradle**

```gradle
// Domain 모듈에서 testFixtures 플러그인 활성화
plugins {
    id 'java-library'
    id 'java-test-fixtures'  // ✅ 활성화
}

// domain/src/testFixtures/java/ 경로에 Fixture 작성
// → 다른 모듈에서 접근 가능
```

**디렉토리 구조:**

```
domain/
└── src/
    ├── main/java/
    ├── test/java/
    └── testFixtures/java/          // ✅ 공유 가능한 Fixture
        └── com/company/template/fixtures/
            ├── OrderMother.java
            └── CustomerMother.java
```

**application/build.gradle**

```gradle
dependencies {
    // ✅ Domain의 testFixtures 사용
    testImplementation(testFixtures(project(':domain')))
}
```

**사용:**

```java
// application/src/test/java/
import com.company.template.fixtures.OrderMother;  // ✅ 접근 가능

Order order = OrderMother.defaultOrder();
```

---

#### 해결책 2: 독립 test-fixtures 모듈

**프로젝트 구조:**

```
project-root/
├── domain/
├── application/
├── adapter/
└── test-fixtures/                 // ✅ 독립 모듈
    ├── build.gradle
    └── src/main/java/
        └── com/company/template/fixtures/
            ├── OrderMother.java
            └── CommandMother.java
```

**test-fixtures/build.gradle:**

```gradle
dependencies {
    implementation project(':domain')
    implementation project(':application')

    implementation 'org.junit.jupiter:junit-jupiter'
    implementation 'org.assertj:assertj-core'
}
```

**모든 모듈에서 사용:**

```gradle
// domain/build.gradle
dependencies {
    testImplementation project(':test-fixtures')
}

// application/build.gradle
dependencies {
    testImplementation project(':test-fixtures')
}

// adapter/build.gradle
dependencies {
    testImplementation project(':test-fixtures')
}
```

---

### 2. Fixture 네이밍 규칙

| 패턴 | 클래스명 | 메서드명 | 예시 |
|------|---------|---------|------|
| **Object Mother** | `{Entity}Mother` | `default{Entity}()`, `{adjective}{Entity}()` | `OrderMother.defaultOrder()` |
| **Builder** | `{Entity}Builder` | `an{Entity}()`, `with{Property}()` | `OrderBuilder.anOrder().withId(1L)` |
| **Factory** | `{Entity}Factory` | `createPersisted{Entity}()` | `OrderFactory.createPersistedOrder()` |

---

### 3. Fixture 버전 관리

**변경 시나리오: Order에 deliveryAddress 필드 추가**

❌ **Bad: 기존 Fixture 직접 수정 → 모든 테스트 영향**

```java
public class OrderMother {
    public static Order defaultOrder() {
        return Order.create(
            new OrderId(1L),
            new CustomerId(100L),
            new Address("서울시")  // ❌ 추가 → 모든 테스트 컴파일 에러
        );
    }
}
```

✅ **Good: 새로운 메서드 추가 + 기존 메서드 유지**

```java
public class OrderMother {
    /**
     * @deprecated Use {@link #defaultOrderWithAddress()} instead
     */
    @Deprecated
    public static Order defaultOrder() {
        return Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );
    }

    /**
     * deliveryAddress 포함 Order (v2)
     */
    public static Order defaultOrderWithAddress() {
        return Order.create(
            new OrderId(1L),
            new CustomerId(100L),
            new Address("서울시 강남구")
        );
    }
}
```

---

## 실전 예제

### 시나리오: 복잡한 Order 생성

#### 요구사항
- 고객 ID: 200
- 아이템 3개 (일반 상품 2개, 고가 상품 1개)
- 총 금액: 520,000원
- 상태: CONFIRMED

---

#### ❌ Bad: 테스트마다 중복 생성 로직

```java
@Test
void test1() {
    Order order = Order.create(new OrderId(1L), new CustomerId(200L));
    order.addItem(OrderItem.create(new ProductId(1001L), new Quantity(1), new Price(10000.0)));
    order.addItem(OrderItem.create(new ProductId(1002L), new Quantity(1), new Price(10000.0)));
    order.addItem(OrderItem.create(new ProductId(2001L), new Quantity(1), new Price(500000.0)));
    order.confirm();
    // 테스트 로직
}

@Test
void test2() {
    Order order = Order.create(new OrderId(1L), new CustomerId(200L));
    order.addItem(OrderItem.create(new ProductId(1001L), new Quantity(1), new Price(10000.0)));
    order.addItem(OrderItem.create(new ProductId(1002L), new Quantity(1), new Price(10000.0)));
    order.addItem(OrderItem.create(new ProductId(2001L), new Quantity(1), new Price(500000.0)));
    order.confirm();
    // 테스트 로직
}
```

---

#### ✅ Good: Object Mother 사용

```java
// OrderMother.java
public class OrderMother {
    public static Order complexOrder() {
        Order order = Order.create(
            new OrderId(1L),
            new CustomerId(200L)
        );
        order.addItem(OrderItemMother.defaultOrderItem());
        order.addItem(OrderItemMother.anotherOrderItem());
        order.addItem(OrderItemMother.expensiveOrderItem());
        order.confirm();
        return order;
    }
}

// 테스트
@Test
void test1() {
    Order order = OrderMother.complexOrder();
    // 테스트 로직 - 한 줄로 해결
}

@Test
void test2() {
    Order order = OrderMother.complexOrder();
    // 테스트 로직 - 재사용
}
```

---

#### ✅ Better: Builder 사용 (유연성 필요 시)

```java
// OrderBuilder.java
public class OrderBuilder {
    public static OrderBuilder anOrder() { ... }
    public OrderBuilder withCustomerId(Long id) { ... }
    public OrderBuilder withComplexItems() {
        this.items.add(OrderItemMother.defaultOrderItem());
        this.items.add(OrderItemMother.anotherOrderItem());
        this.items.add(OrderItemMother.expensiveOrderItem());
        return this;
    }
    public Order build() { ... }
}

// 테스트
@Test
void test_CustomerId변경() {
    Order order = OrderBuilder.anOrder()
        .withCustomerId(300L)  // ✅ 유연하게 변경
        .withComplexItems()
        .build();

    assertThat(order.getCustomerId()).isEqualTo(new CustomerId(300L));
}
```

---

## 요약

### 핵심 원칙

| 패턴 | 사용 시점 | 장점 | 단점 |
|------|----------|------|------|
| **Object Mother** | 간단한 객체, 변형 적음 | 빠름, 간결함 | 유연성 부족 |
| **Builder** | 복잡한 객체, 다양한 변형 | 유연성 높음 | 코드 많음 |
| **Factory** | 영속화 필요 | DB 상태 설정 용이 | 느림 (DB 의존) |

### Fixture 공유 전략

```
독립 모듈 (test-fixtures)
  ↓
  모든 모듈에서 testImplementation

또는

Gradle testFixtures 소스셋
  ↓
  testImplementation(testFixtures(project(':domain')))
```

### 네이밍 규칙

- Object Mother: `OrderMother.defaultOrder()`
- Builder: `OrderBuilder.anOrder().withId(1L).build()`
- Factory: `OrderFactory.createPersistedOrder()`

---

## validation

```yaml
metadata:
  layer: "testing"
  category: "multi-module-testing"
  version: "1.0"

rules:
  - "테스트 데이터 생성 로직은 중앙화된 Fixture 사용"
  - "Object Mother: 간단한 객체, Builder: 복잡한 객체, Factory: 영속화"
  - "testFixtures 소스셋 또는 독립 test-fixtures 모듈로 공유"
  - "Fixture 변경 시 기존 메서드 유지, 새로운 메서드 추가"

validation:
  antiPatterns:
    - "Order\\.create\\(.*\\).*Order\\.create\\(.*\\)"  # 중복 생성 로직
    - "new.*Entity\\(.*\\).*new.*Entity\\(.*\\)"  # Entity 직접 생성 중복
