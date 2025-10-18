# 성능 및 벤치마크 테스트 (Performance and Benchmark Testing)

## 📋 목차
- [개요](#개요)
- [응답 시간 임계값 테스트](#응답-시간-임계값-테스트)
- [쿼리 카운트 검증](#쿼리-카운트-검증)
- [N+1 문제 탐지](#n1-문제-탐지)
- [JMH 벤치마크](#jmh-벤치마크)
- [부하 테스트](#부하-테스트)
- [실전 예제](#실전-예제)

---

## 개요

### 목적

통합 테스트 단계에서 **성능 회귀를 조기에 탐지**하고 **성능 기준을 자동으로 검증**합니다.

### 성능 테스트 레벨

```
Level 1: 응답 시간 임계값
  → @Timeout으로 최대 응답 시간 검증

Level 2: 쿼리 카운트 검증
  → Hibernate Statistics로 SQL 실행 횟수 검증

Level 3: N+1 문제 탐지
  → 자동 N+1 패턴 감지

Level 4: JMH 벤치마크
  → 마이크로 벤치마크로 정밀 성능 측정

Level 5: 부하 테스트
  → Gatling/JMeter로 시스템 부하 테스트
```

---

## 응답 시간 임계값 테스트

### 1. @Timeout 어노테이션

**기본 사용법**

```java
package com.company.template.order.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * 응답 시간 임계값 테스트
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryPerformanceTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)  // ✅ 100ms 이내
    void findById_ShouldResponseWithin100ms() {
        // Given
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(100L);
        OrderJpaEntity saved = orderRepository.save(order);

        // When - 100ms 이내에 완료되어야 함
        Optional<OrderJpaEntity> found = orderRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
    }

    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)  // ✅ 500ms 이내
    void findByCustomerId_ShouldResponseWithin500ms() {
        // Given - 100개 주문 생성
        for (int i = 0; i < 100; i++) {
            OrderJpaEntity order = new OrderJpaEntity();
            order.setCustomerId(100L);
            orderRepository.save(order);
        }

        // When
        List<OrderJpaEntity> orders = orderRepository.findByCustomerId(100L);

        // Then
        assertThat(orders).hasSize(100);
    }
}
```

---

### 2. 클래스 레벨 Timeout

**모든 테스트에 기본 임계값 적용**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Timeout(value = 1, unit = TimeUnit.SECONDS)  // 모든 테스트: 1초 이내
class OrderRepositoryPerformanceTest {

    @Test
    void test1() {
        // 1초 이내에 완료되어야 함
    }

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)  // ✅ 오버라이드: 100ms
    void fastTest() {
        // 100ms 이내에 완료되어야 함
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)  // ✅ 오버라이드: 5초
    void slowTest() {
        // 복잡한 쿼리 테스트: 5초 이내
    }
}
```

---

### 3. 수동 시간 측정

**StopWatch 사용**

```java
import org.springframework.util.StopWatch;

@Test
void findByCustomerId_PerformanceMeasurement() {
    // Given
    createTestOrders(100);

    // When
    StopWatch stopWatch = new StopWatch();
    stopWatch.start("findByCustomerId");

    List<OrderJpaEntity> orders = orderRepository.findByCustomerId(100L);

    stopWatch.stop();

    // Then
    assertThat(orders).hasSize(100);

    // 성능 검증
    long executionTimeMs = stopWatch.getLastTaskTimeMillis();
    assertThat(executionTimeMs).isLessThan(500L);  // 500ms 이내

    System.out.println("Execution time: " + executionTimeMs + "ms");
}
```

---

## 쿼리 카운트 검증

### 1. Hibernate Statistics 활성화

**application-test.yml**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        # ✅ Statistics 활성화
        generate_statistics: true
        # SQL 로그 (선택적)
        show_sql: true
        format_sql: true
```

---

### 2. 쿼리 카운트 Helper

**QueryCounterHelper.java**

```java
package com.company.template.common.test;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Hibernate Statistics 기반 쿼리 카운터
 */
@Component
public class QueryCounterHelper {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Statistics 객체 반환
     */
    public Statistics getStatistics() {
        SessionFactory sessionFactory = entityManager
            .getEntityManagerFactory()
            .unwrap(SessionFactory.class);
        return sessionFactory.getStatistics();
    }

    /**
     * Statistics 초기화
     */
    public void reset() {
        getStatistics().clear();
    }

    /**
     * 실행된 쿼리 수 반환
     */
    public long getQueryCount() {
        return getStatistics().getPrepareStatementCount();
    }

    /**
     * 엔티티 로드 횟수
     */
    public long getEntityLoadCount() {
        return getStatistics().getEntityLoadCount();
    }

    /**
     * 쿼리 캐시 히트 횟수
     */
    public long getQueryCacheHitCount() {
        return getStatistics().getQueryCacheHitCount();
    }

    /**
     * 2차 캐시 히트 횟수
     */
    public long getSecondLevelCacheHitCount() {
        return getStatistics().getSecondLevelCacheHitCount();
    }
}
```

---

### 3. 쿼리 카운트 검증 테스트

**OrderRepositoryQueryCountTest.java**

```java
package com.company.template.order.adapter.out.persistence;

import com.company.template.common.test.QueryCounterHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.*;

/**
 * 쿼리 카운트 검증 테스트
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryQueryCountTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private QueryCounterHelper queryCounter;

    @BeforeEach
    void setUp() {
        queryCounter.reset();  // ✅ 통계 초기화
    }

    @Test
    void findById_ShouldExecuteOnlyOneQuery() {
        // Given
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(100L);
        OrderJpaEntity saved = orderRepository.save(order);

        queryCounter.reset();  // INSERT 쿼리 제외

        // When
        orderRepository.findById(saved.getId());

        // Then - 정확히 1개의 SELECT 쿼리만 실행
        assertThat(queryCounter.getQueryCount()).isEqualTo(1L);
    }

    @Test
    void findByCustomerId_ShouldExecuteOnlyOneQuery() {
        // Given - 10개 주문 생성
        for (int i = 0; i < 10; i++) {
            OrderJpaEntity order = new OrderJpaEntity();
            order.setCustomerId(100L);
            orderRepository.save(order);
        }

        queryCounter.reset();

        // When - 고객 ID로 조회
        List<OrderJpaEntity> orders = orderRepository.findByCustomerId(100L);

        // Then
        assertThat(orders).hasSize(10);
        assertThat(queryCounter.getQueryCount()).isEqualTo(1L);  // ✅ 1개 쿼리만
    }
}
```

---

## N+1 문제 탐지

### 1. N+1 문제란?

**나쁜 예시: N+1 쿼리 발생**

```java
// ❌ Bad: N+1 문제 발생
@Test
void loadOrdersWithItems_NPlusOneProblem() {
    // Given - Order 10개 생성, 각각 아이템 5개
    for (int i = 0; i < 10; i++) {
        OrderJpaEntity order = new OrderJpaEntity();
        order.setCustomerId(100L);
        orderRepository.save(order);

        for (int j = 0; j < 5; j++) {
            OrderItemJpaEntity item = new OrderItemJpaEntity();
            item.setOrder(order);
            item.setProductId(1000L + j);
            orderItemRepository.save(item);
        }
    }

    queryCounter.reset();

    // When - Order 조회 후 아이템 접근
    List<OrderJpaEntity> orders = orderRepository.findByCustomerId(100L);

    for (OrderJpaEntity order : orders) {
        List<OrderItemJpaEntity> items = order.getItems();  // ❌ 각 Order마다 쿼리 실행
        System.out.println("Order " + order.getId() + " has " + items.size() + " items");
    }

    // Then - 11개 쿼리 실행 (1 + 10)
    // 1: SELECT orders
    // 10: SELECT items WHERE order_id = ?
    assertThat(queryCounter.getQueryCount()).isEqualTo(11L);  // ❌ N+1 문제!
}
```

---

### 2. Fetch Join으로 해결

**좋은 예시: Fetch Join 사용**

```java
// OrderJpaRepository.java
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    @Query("SELECT o FROM OrderJpaEntity o " +
           "LEFT JOIN FETCH o.items " +  // ✅ Fetch Join
           "WHERE o.customerId = :customerId")
    List<OrderJpaEntity> findByCustomerIdWithItems(@Param("customerId") Long customerId);
}

// 테스트
@Test
void loadOrdersWithItems_NoNPlusOne() {
    // Given - Order 10개, 각각 아이템 5개
    createOrdersWithItems(10, 5);

    queryCounter.reset();

    // When - Fetch Join으로 조회
    List<OrderJpaEntity> orders = orderRepository.findByCustomerIdWithItems(100L);

    for (OrderJpaEntity order : orders) {
        List<OrderItemJpaEntity> items = order.getItems();  // ✅ 추가 쿼리 없음
        System.out.println("Order " + order.getId() + " has " + items.size() + " items");
    }

    // Then - 1개 쿼리만 실행
    assertThat(queryCounter.getQueryCount()).isEqualTo(1L);  // ✅ 해결!
}
```

---

### 3. EntityGraph로 해결

**@EntityGraph 사용**

```java
// OrderJpaRepository.java
@EntityGraph(attributePaths = {"items"})  // ✅ EntityGraph
@Query("SELECT o FROM OrderJpaEntity o WHERE o.customerId = :customerId")
List<OrderJpaEntity> findByCustomerIdWithItemsGraph(@Param("customerId") Long customerId);

// 테스트
@Test
void loadOrdersWithItems_UsingEntityGraph() {
    createOrdersWithItems(10, 5);
    queryCounter.reset();

    List<OrderJpaEntity> orders = orderRepository.findByCustomerIdWithItemsGraph(100L);

    for (OrderJpaEntity order : orders) {
        order.getItems().size();  // ✅ 추가 쿼리 없음
    }

    assertThat(queryCounter.getQueryCount()).isEqualTo(1L);  // ✅ 1개 쿼리
}
```

---

### 4. 자동 N+1 탐지 어노테이션

**커스텀 어노테이션**

```java
package com.company.template.common.test;

import java.lang.annotation.*;

/**
 * N+1 문제 탐지 어노테이션
 *
 * maxQueries를 초과하면 테스트 실패
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DetectNPlusOne {
    int maxQueries() default 1;
}
```

**JUnit Extension**

```java
package com.company.template.common.test;

import org.junit.jupiter.api.extension.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class NPlusOneDetectionExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        DetectNPlusOne annotation = context.getRequiredTestMethod()
            .getAnnotation(DetectNPlusOne.class);

        if (annotation != null) {
            QueryCounterHelper queryCounter = SpringExtension
                .getApplicationContext(context)
                .getBean(QueryCounterHelper.class);

            long queryCount = queryCounter.getQueryCount();
            int maxQueries = annotation.maxQueries();

            if (queryCount > maxQueries) {
                throw new AssertionError(
                    String.format("N+1 detected! Expected max %d queries, but %d executed",
                        maxQueries, queryCount)
                );
            }
        }
    }
}
```

**사용:**

```java
@ExtendWith(NPlusOneDetectionExtension.class)
class OrderRepositoryNPlusOneTest {

    @Test
    @DetectNPlusOne(maxQueries = 1)  // ✅ 1개 초과 시 실패
    void findOrdersWithItems_ShouldNotHaveNPlusOne() {
        List<OrderJpaEntity> orders = orderRepository.findByCustomerIdWithItems(100L);

        for (OrderJpaEntity order : orders) {
            order.getItems().size();
        }

        // 2개 이상 쿼리 실행 시 자동으로 실패
    }
}
```

---

## JMH 벤치마크

### 1. JMH 설정

**build.gradle**

```gradle
dependencies {
    // JMH (Java Microbenchmark Harness)
    testImplementation 'org.openjdk.jmh:jmh-core:1.37'
    testAnnotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.37'
}

// JMH 벤치마크 실행 태스크
task jmh(type: JavaExec) {
    classpath = sourceSets.test.runtimeClasspath
    main = 'org.openjdk.jmh.Main'
    args = ['-rf', 'json', '-rff', 'build/reports/jmh-result.json']
}
```

---

### 2. 도메인 로직 벤치마크

**OrderPriceCalculatorBenchmark.java**

```java
package com.company.template.order.domain.service;

import com.company.template.order.domain.model.*;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * OrderPriceCalculator 성능 벤치마크
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OrderPriceCalculatorBenchmark {

    private OrderPriceCalculator calculator;
    private Order orderWith10Items;
    private Order orderWith100Items;

    @Setup
    public void setup() {
        calculator = new OrderPriceCalculator();

        orderWith10Items = createOrderWithItems(10);
        orderWith100Items = createOrderWithItems(100);
    }

    @Benchmark
    public Price calculate_10Items() {
        return calculator.calculate(orderWith10Items);
    }

    @Benchmark
    public Price calculate_100Items() {
        return calculator.calculate(orderWith100Items);
    }

    private Order createOrderWithItems(int itemCount) {
        Order order = Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );

        for (int i = 0; i < itemCount; i++) {
            order.addItem(OrderItem.create(
                new ProductId((long) i),
                new Quantity(1),
                new Price(10000.0)
            ));
        }

        return order;
    }
}
```

**실행:**

```bash
./gradlew jmh

# 결과
Benchmark                                          Mode  Cnt   Score   Error  Units
OrderPriceCalculatorBenchmark.calculate_10Items    avgt    5  1234.5 ± 45.2  ns/op
OrderPriceCalculatorBenchmark.calculate_100Items   avgt    5 12345.6 ± 123.4 ns/op
```

---

### 3. Repository 쿼리 벤치마크

**OrderRepositoryBenchmark.java**

```java
@State(Scope.Thread)
public class OrderRepositoryBenchmark {

    private OrderRepository orderRepository;
    private ApplicationContext context;

    @Setup
    public void setup() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        orderRepository = context.getBean(OrderRepository.class);

        // 테스트 데이터 생성
        for (int i = 0; i < 1000; i++) {
            OrderJpaEntity order = new OrderJpaEntity();
            order.setCustomerId(100L);
            orderRepository.save(order);
        }
    }

    @TearDown
    public void tearDown() {
        ((AnnotationConfigApplicationContext) context).close();
    }

    @Benchmark
    public List<OrderJpaEntity> findByCustomerId() {
        return orderRepository.findByCustomerId(100L);
    }

    @Benchmark
    public List<OrderJpaEntity> findByCustomerIdWithFetchJoin() {
        return orderRepository.findByCustomerIdWithItems(100L);
    }
}
```

---

## 부하 테스트

### 1. Gatling 설정

**build.gradle**

```gradle
plugins {
    id 'io.gatling.gradle' version '3.9.5'
}

dependencies {
    gatlingImplementation 'io.gatling.highcharts:gatling-charts-highcharts:3.9.5'
}
```

---

### 2. Gatling 시나리오

**CreateOrderLoadTest.scala**

```scala
package com.company.template.order

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class CreateOrderLoadTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val createOrderScenario = scenario("Create Order Load Test")
    .exec(http("Create Order")
      .post("/api/orders")
      .body(StringBody("""{
        "customerId": 100,
        "items": [
          {"productId": 1001, "quantity": 2, "price": 10000.0}
        ]
      }"""))
      .check(status.is(201)))

  setUp(
    createOrderScenario.inject(
      rampUsers(100) during (10.seconds),  // 10초 동안 100명 유저
      constantUsersPerSec(50) during (30.seconds)  // 30초 동안 초당 50명
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.max.lt(500),  // 최대 응답 시간 500ms
     global.successfulRequests.percent.gt(95)  // 성공률 95% 이상
   )
}
```

**실행:**

```bash
./gradlew gatlingRun
```

---

## 실전 예제

### 시나리오: Order 조회 성능 최적화

#### 요구사항
- 고객별 주문 조회 API
- 응답 시간: 100ms 이내
- 쿼리 수: 1개만 허용
- N+1 문제 없음

---

#### 성능 테스트

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryPerformanceOptimizationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private QueryCounterHelper queryCounter;

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)  // ✅ 100ms 임계값
    @DetectNPlusOne(maxQueries = 1)  // ✅ 1개 쿼리만 허용
    void findByCustomerIdWithItems_ShouldMeetPerformanceRequirements() {
        // Given - 50개 주문, 각각 10개 아이템
        for (int i = 0; i < 50; i++) {
            OrderJpaEntity order = new OrderJpaEntity();
            order.setCustomerId(100L);
            orderRepository.save(order);

            for (int j = 0; j < 10; j++) {
                OrderItemJpaEntity item = new OrderItemJpaEntity();
                item.setOrder(order);
                item.setProductId(1000L + j);
                orderItemRepository.save(item);
            }
        }

        queryCounter.reset();

        // When - Fetch Join 사용
        List<OrderJpaEntity> orders = orderRepository.findByCustomerIdWithItems(100L);

        // Then
        assertThat(orders).hasSize(50);
        assertThat(orders.get(0).getItems()).hasSize(10);

        // 성능 검증
        assertThat(queryCounter.getQueryCount()).isEqualTo(1L);  // ✅ 1개 쿼리
        // @Timeout으로 100ms 검증됨
        // @DetectNPlusOne으로 N+1 검증됨
    }
}
```

---

## 요약

### 핵심 원칙

| 테스트 유형 | 도구 | 목적 | 임계값 |
|-----------|------|------|--------|
| **응답 시간** | `@Timeout` | 최대 응답 시간 검증 | < 100ms (단순), < 500ms (복잡) |
| **쿼리 카운트** | Hibernate Statistics | SQL 실행 횟수 검증 | 가능한 최소화 |
| **N+1 탐지** | `@DetectNPlusOne` | N+1 문제 자동 탐지 | 1개 쿼리 (Fetch Join) |
| **벤치마크** | JMH | 마이크로 벤치마크 | 나노초 단위 측정 |
| **부하 테스트** | Gatling | 시스템 부하 테스트 | TPS, 응답시간 |

### Best Practice

```yaml
통합_테스트:
  응답_시간: "@Timeout(100ms) for simple queries"
  쿼리_카운트: "Hibernate Statistics로 검증"
  N+1_탐지: "@DetectNPlusOne으로 자동 탐지"

도메인_로직:
  벤치마크: "JMH로 나노초 단위 측정"
  최적화: "성능 회귀 방지"

시스템_부하:
  도구: "Gatling 또는 JMeter"
  실행: "배포 전 선택적"
```

### 성능 목표

```
단순 조회: < 100ms, 1 query
복잡 조회: < 500ms, 2-3 queries
Fetch Join: N+1 방지, 1 query
대량 데이터: Batch insert, < 1s/1000 rows
```

---

## validation

```yaml
metadata:
  layer: "testing"
  category: "integration-testing"
  version: "1.0"

rules:
  - "@Timeout으로 응답 시간 임계값 검증"
  - "Hibernate Statistics로 쿼리 카운트 검증"
  - "N+1 문제는 Fetch Join 또는 EntityGraph로 해결"
  - "JMH 벤치마크로 도메인 로직 성능 측정"

validation:
  antiPatterns:
    - "SELECT.*FROM.*WHERE.*IN\\s*\\(SELECT"  # N+1 패턴
    - "@Query.*JOIN\\s+(?!FETCH)"  # Fetch Join 누락
