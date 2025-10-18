# 멀티모듈 테스트 격리 전략

## 📋 목차
- [개요](#개요)
- [모듈별 테스트 격리 원칙](#모듈별-테스트-격리-원칙)
- [Domain 모듈 테스트](#domain-모듈-테스트)
- [Application 모듈 테스트](#application-모듈-테스트)
- [Adapter 모듈 테스트](#adapter-모듈-테스트)
- [테스트 의존성 관리](#테스트-의존성-관리)
- [실전 예제](#실전-예제)

---

## 개요

### 목적

멀티모듈 프로젝트에서 각 모듈의 책임과 경계를 명확히 하기 위해 **테스트 격리 전략**을 정의합니다.

### 핵심 원칙

```yaml
격리_원칙:
  domain: "외부 의존성 없는 순수 단위 테스트"
  application: "Port mocking 기반 격리 테스트"
  adapter: "실제 인프라와의 통합 테스트"

의존성_방향:
  adapter → application → domain
  테스트도_같은_방향을_따름: true
```

### 왜 중요한가?

- ✅ **빠른 피드백**: Domain 테스트는 외부 의존성 없이 밀리초 단위 실행
- ✅ **명확한 책임**: 각 레이어의 테스트 책임이 명확히 분리됨
- ✅ **유지보수성**: 한 모듈의 변경이 다른 모듈 테스트에 영향 최소화
- ✅ **CI/CD 최적화**: 레이어별 선택적 테스트 실행 가능

---

## 모듈별 테스트 격리 원칙

### 1. Domain 모듈: 순수 단위 테스트

**특징:**
- ❌ Spring Context 로딩 금지
- ❌ 외부 라이브러리 의존성 금지 (JPA, Jackson 등)
- ✅ Pure Java 단위 테스트만 허용
- ✅ AssertJ, JUnit 5만 사용

**의존성 제약:**
```gradle
// domain/build.gradle
dependencies {
    // ✅ 허용
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.assertj:assertj-core'

    // ❌ 금지
    testImplementation 'org.springframework.boot:spring-boot-starter-test'  // NO
    testImplementation 'org.testcontainers:postgresql'                       // NO
}
```

---

### 2. Application 모듈: Port Mocking 테스트

**특징:**
- ✅ Spring Context 로딩 허용 (선택적)
- ✅ Outbound Port mocking (Mockito, MockK)
- ❌ 실제 Adapter 구현체 사용 금지
- ✅ `@MockBean`, `@SpyBean` 활용

**의존성 제약:**
```gradle
// application/build.gradle
dependencies {
    implementation project(':domain')

    // ✅ 허용
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.mockito:mockito-core'

    // ❌ 금지
    testImplementation project(':adapter:out:persistence')  // NO - Adapter 직접 의존 금지
    testImplementation 'org.testcontainers:postgresql'      // NO - 인프라 의존 금지
}
```

---

### 3. Adapter 모듈: 실제 인프라 통합 테스트

**특징:**
- ✅ Spring Context 전체 로딩
- ✅ Testcontainers, 실제 DB/Redis 사용
- ✅ 실제 HTTP 클라이언트, 메시징 시스템 사용
- ❌ Domain/Application 로직 재테스트 금지

**의존성 제약:**
```gradle
// adapter/out/persistence/build.gradle
dependencies {
    implementation project(':domain')
    implementation project(':application')

    // ✅ 허용 - 실제 인프라 필요
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:junit-jupiter'
}
```

---

## Domain 모듈 테스트

### 1. 순수 단위 테스트 패턴

**✅ Good: 외부 의존성 없는 테스트**

```java
package com.company.template.order.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Order Aggregate 순수 단위 테스트
 *
 * - Spring Context 없음
 * - 외부 라이브러리 의존성 없음
 * - 밀리초 단위 실행 (<5ms)
 */
class OrderTest {

    @Test
    void create_ShouldInitializeOrderWithPendingStatus() {
        // Given
        OrderId orderId = new OrderId(1L);
        CustomerId customerId = new CustomerId(100L);

        // When
        Order order = Order.create(orderId, customerId);

        // Then
        assertThat(order.getOrderId()).isEqualTo(orderId);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void addItem_ShouldAddOrderItemToOrder() {
        // Given
        Order order = Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );
        OrderItem item = OrderItem.create(
            new ProductId(1001L),
            new Quantity(2),
            new Price(10000.0)
        );

        // When
        order.addItem(item);

        // Then
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotalAmount()).isEqualTo(new Price(20000.0));
    }

    @Test
    void confirm_ShouldChangeStatusToConfirmed() {
        // Given
        Order order = Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );

        // When
        order.confirm();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_WhenAlreadyConfirmed_ShouldThrowException() {
        // Given
        Order order = Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );
        order.confirm();

        // When & Then
        assertThatThrownBy(() -> order.confirm())
            .isInstanceOf(OrderAlreadyConfirmedException.class)
            .hasMessage("Order is already confirmed");
    }
}
```

**❌ Bad: Spring Context 의존**

```java
// ❌ Domain 모듈에서 금지
@SpringBootTest  // NO - Spring Context 로딩 금지
class OrderTest {

    @Autowired  // NO - Spring 의존성 금지
    private Order order;

    @Test
    void test() {
        // ...
    }
}
```

---

### 2. Value Object 테스트

**✅ Good: 불변성 및 동등성 검증**

```java
package com.company.template.order.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Price Value Object 테스트
 */
class PriceTest {

    @Test
    void constructor_WhenNegativeValue_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> new Price(-1000.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Price must be non-negative");
    }

    @Test
    void add_ShouldReturnNewPriceInstance() {
        // Given
        Price price1 = new Price(1000.0);
        Price price2 = new Price(2000.0);

        // When
        Price result = price1.add(price2);

        // Then
        assertThat(result).isEqualTo(new Price(3000.0));
        assertThat(result).isNotSameAs(price1);  // 불변성 검증
        assertThat(price1).isEqualTo(new Price(1000.0));  // 원본 불변
    }

    @Test
    void equals_WhenSameValue_ShouldReturnTrue() {
        // Given
        Price price1 = new Price(1000.0);
        Price price2 = new Price(1000.0);

        // Then
        assertThat(price1).isEqualTo(price2);
        assertThat(price1.hashCode()).isEqualTo(price2.hashCode());
    }
}
```

---

### 3. Domain Service 테스트

**✅ Good: 순수 비즈니스 로직 검증**

```java
package com.company.template.order.domain.service;

import com.company.template.order.domain.model.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * OrderPriceCalculator Domain Service 테스트
 */
class OrderPriceCalculatorTest {

    private final OrderPriceCalculator calculator = new OrderPriceCalculator();

    @Test
    void calculate_ShouldReturnSumOfAllItemPrices() {
        // Given
        Order order = Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );
        order.addItem(OrderItem.create(
            new ProductId(1L), new Quantity(2), new Price(1000.0)
        ));
        order.addItem(OrderItem.create(
            new ProductId(2L), new Quantity(1), new Price(5000.0)
        ));

        // When
        Price total = calculator.calculate(order);

        // Then
        assertThat(total).isEqualTo(new Price(7000.0));
    }

    @Test
    void calculateWithDiscount_ShouldApplyDiscountRate() {
        // Given
        Order order = Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );
        order.addItem(OrderItem.create(
            new ProductId(1L), new Quantity(1), new Price(10000.0)
        ));
        DiscountRate rate = new DiscountRate(0.1);  // 10% 할인

        // When
        Price total = calculator.calculateWithDiscount(order, rate);

        // Then
        assertThat(total).isEqualTo(new Price(9000.0));
    }
}
```

---

## Application 모듈 테스트

### 1. UseCase 테스트 (Port Mocking)

**✅ Good: Outbound Port를 Mocking하여 격리**

```java
package com.company.template.order.application.service;

import com.company.template.order.domain.model.*;
import com.company.template.order.domain.port.out.*;
import com.company.template.order.application.port.in.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CreateOrderUseCase 테스트
 *
 * - Outbound Port (LoadOrderPort, SaveOrderPort) Mocking
 * - 실제 Persistence Adapter 없이 격리 테스트
 */
@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    @Mock
    private SaveOrderPort saveOrderPort;

    @Mock
    private LoadCustomerPort loadCustomerPort;

    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateOrderService(saveOrderPort, loadCustomerPort);
    }

    @Test
    void execute_ShouldCreateOrderAndSave() {
        // Given
        CreateOrderCommand command = new CreateOrderCommand(
            100L,  // customerId
            List.of(new OrderItemRequest(1001L, 2, 10000.0))
        );

        Customer customer = mock(Customer.class);
        when(loadCustomerPort.loadById(new CustomerId(100L)))
            .thenReturn(Optional.of(customer));

        when(saveOrderPort.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        OrderId orderId = useCase.execute(command);

        // Then
        assertThat(orderId).isNotNull();
        verify(loadCustomerPort).loadById(new CustomerId(100L));
        verify(saveOrderPort).save(any(Order.class));
    }

    @Test
    void execute_WhenCustomerNotFound_ShouldThrowException() {
        // Given
        CreateOrderCommand command = new CreateOrderCommand(
            999L,  // non-existent customerId
            List.of(new OrderItemRequest(1001L, 2, 10000.0))
        );

        when(loadCustomerPort.loadById(new CustomerId(999L)))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(CustomerNotFoundException.class);

        verify(saveOrderPort, never()).save(any());
    }
}
```

**❌ Bad: 실제 Adapter 의존**

```java
// ❌ Application 모듈에서 금지
@SpringBootTest  // NO - 전체 Context 로딩 불필요
@Testcontainers  // NO - 실제 DB 의존 금지
class CreateOrderServiceTest {

    @Autowired
    private CreateOrderUseCase useCase;

    @Autowired
    private OrderRepository orderRepository;  // NO - 실제 Adapter 의존 금지

    @Container
    static PostgreSQLContainer<?> postgres = ...;  // NO - 인프라 의존 금지

    @Test
    void test() {
        // Application 레이어는 Port mocking으로만 테스트
    }
}
```

---

### 2. Spring Context를 사용하는 UseCase 테스트 (선택적)

**✅ Good: @MockBean으로 Port Mocking**

```java
package com.company.template.order.application.service;

import com.company.template.order.application.port.in.*;
import com.company.template.order.domain.port.out.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Spring Context를 사용한 UseCase 테스트
 *
 * 주의: Spring Context 로딩으로 느려짐 (수백ms)
 * 필요한 경우에만 사용 (예: Transaction 동작 검증)
 */
@SpringBootTest
@ContextConfiguration(classes = {CreateOrderService.class})
class CreateOrderServiceSpringTest {

    @Autowired
    private CreateOrderUseCase useCase;

    @MockBean
    private SaveOrderPort saveOrderPort;

    @MockBean
    private LoadCustomerPort loadCustomerPort;

    @Test
    void execute_WithSpringContext_ShouldWork() {
        // Given
        CreateOrderCommand command = new CreateOrderCommand(
            100L,
            List.of(new OrderItemRequest(1001L, 2, 10000.0))
        );

        when(loadCustomerPort.loadById(any()))
            .thenReturn(Optional.of(mock(Customer.class)));
        when(saveOrderPort.save(any()))
            .thenReturn(new OrderId(1L));

        // When
        OrderId orderId = useCase.execute(command);

        // Then
        assertThat(orderId).isNotNull();
    }
}
```

---

## Adapter 모듈 테스트

### 1. Persistence Adapter 테스트

**✅ Good: 실제 DB 통합 테스트 (Testcontainers)**

```java
package com.company.template.order.adapter.out.persistence;

import com.company.template.order.domain.model.*;
import com.company.template.order.domain.port.out.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderPersistenceAdapter 통합 테스트
 *
 * - 실제 PostgreSQL 컨테이너 사용
 * - JPA 매핑 및 쿼리 검증
 * - Application/Domain 로직은 재테스트하지 않음
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderPersistenceAdapter.class)
class OrderPersistenceAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    @Autowired
    private SaveOrderPort saveOrderPort;

    @Autowired
    private LoadOrderPort loadOrderPort;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_ShouldPersistOrderCorrectly() {
        // Given
        Order order = Order.create(
            new OrderId(null),  // null for new entity
            new CustomerId(100L)
        );
        order.addItem(OrderItem.create(
            new ProductId(1001L),
            new Quantity(2),
            new Price(10000.0)
        ));

        // When
        OrderId savedId = saveOrderPort.save(order);
        entityManager.flush();
        entityManager.clear();

        // Then
        Order loaded = loadOrderPort.loadById(savedId).orElseThrow();
        assertThat(loaded.getOrderId()).isEqualTo(savedId);
        assertThat(loaded.getCustomerId()).isEqualTo(new CustomerId(100L));
        assertThat(loaded.getItems()).hasSize(1);
    }

    @Test
    void loadById_WhenNotExists_ShouldReturnEmpty() {
        // When
        Optional<Order> result = loadOrderPort.loadById(new OrderId(999L));

        // Then
        assertThat(result).isEmpty();
    }
}
```

---

### 2. REST Adapter 테스트

**✅ Good: MockMvc를 사용한 REST API 테스트**

```java
package com.company.template.order.adapter.in.web;

import com.company.template.order.application.port.in.*;
import com.company.template.order.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderRestController 테스트
 *
 * - @WebMvcTest로 Controller 레이어만 로딩
 * - UseCase는 @MockBean으로 격리
 */
@WebMvcTest(OrderRestController.class)
class OrderRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void createOrder_ShouldReturnCreatedStatus() throws Exception {
        // Given
        OrderId orderId = new OrderId(1L);
        when(createOrderUseCase.execute(any(CreateOrderCommand.class)))
            .thenReturn(orderId);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "customerId": 100,
                        "items": [
                            {
                                "productId": 1001,
                                "quantity": 2,
                                "price": 10000.0
                            }
                        ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.orderId").value(1));
    }
}
```

---

## 테스트 의존성 관리

### 1. Gradle 의존성 구조

```gradle
// domain/build.gradle
dependencies {
    // ✅ Domain: 순수 테스트 라이브러리만
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.assertj:assertj-core'
}

// application/build.gradle
dependencies {
    implementation project(':domain')

    // ✅ Application: Mockito 추가
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.assertj:assertj-core'
    testImplementation 'org.mockito:mockito-core'
    testImplementation 'org.mockito:mockito-junit-jupiter'

    // ✅ 선택적: Spring Test (필요 시에만)
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

// adapter/out/persistence/build.gradle
dependencies {
    implementation project(':domain')
    implementation project(':application')

    // ✅ Adapter: 실제 인프라 테스트 도구
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:junit-jupiter'
}
```

---

### 2. 테스트 Base Class 구조

```
src/test/java/
├── domain/
│   └── (Base Class 불필요 - 순수 단위 테스트)
│
├── application/
│   └── ApplicationTestBase.java  (선택적 - Mockito Extension)
│
└── adapter/
    └── IntegrationTestBase.java  (필수 - Testcontainers)
```

**ApplicationTestBase (선택적)**

```java
package com.company.template.order.application;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Application Layer 테스트 Base Class (선택적)
 *
 * - Mockito Extension 자동 활성화
 * - @Mock, @InjectMocks 사용 가능
 */
@ExtendWith(MockitoExtension.class)
public abstract class ApplicationTestBase {
    // 공통 설정이 필요한 경우에만 사용
}
```

**IntegrationTestBase (필수)**

```java
package com.company.template.order.adapter;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Adapter Layer 통합 테스트 Base Class
 *
 * - Testcontainers 설정
 * - 모든 Adapter 통합 테스트는 이 클래스 상속
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

---

## 실전 예제

### 시나리오: Order 생성 기능 전체 테스트

#### 1. Domain Layer 테스트 (순수 단위)

```java
// domain/src/test/java/
class OrderTest {
    @Test
    void create_ShouldInitializeCorrectly() {
        Order order = Order.create(
            new OrderId(1L),
            new CustomerId(100L)
        );
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
```

**실행 시간**: ~5ms
**의존성**: 없음

---

#### 2. Application Layer 테스트 (Port Mocking)

```java
// application/src/test/java/
@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {
    @Mock
    private SaveOrderPort saveOrderPort;

    @Mock
    private LoadCustomerPort loadCustomerPort;

    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateOrderService(saveOrderPort, loadCustomerPort);
    }

    @Test
    void execute_ShouldCreateAndSaveOrder() {
        when(loadCustomerPort.loadById(any()))
            .thenReturn(Optional.of(mock(Customer.class)));
        when(saveOrderPort.save(any()))
            .thenReturn(new OrderId(1L));

        OrderId orderId = useCase.execute(new CreateOrderCommand(...));

        assertThat(orderId).isNotNull();
        verify(saveOrderPort).save(any(Order.class));
    }
}
```

**실행 시간**: ~50ms
**의존성**: Mockito만

---

#### 3. Adapter Layer 테스트 (실제 인프라)

```java
// adapter/out/persistence/src/test/java/
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderPersistenceAdapterTest extends IntegrationTestBase {

    @Autowired
    private SaveOrderPort saveOrderPort;

    @Test
    void save_ShouldPersistToDatabase() {
        Order order = Order.create(
            new OrderId(null),
            new CustomerId(100L)
        );

        OrderId savedId = saveOrderPort.save(order);

        assertThat(savedId).isNotNull();
        // 실제 DB에 저장 검증
    }
}
```

**실행 시간**: ~1-2초 (Testcontainers 재사용 시)
**의존성**: PostgreSQL 컨테이너

---

## 요약

### 핵심 원칙

| 모듈 | 테스트 격리 전략 | 허용 의존성 | 금지 사항 |
|------|-----------------|------------|-----------|
| **Domain** | 순수 단위 테스트 | JUnit, AssertJ | Spring, JPA, Testcontainers |
| **Application** | Port Mocking | + Mockito, (선택) Spring Test | 실제 Adapter, Testcontainers |
| **Adapter** | 실제 인프라 통합 | + Spring Boot Test, Testcontainers | Domain/Application 로직 재테스트 |

### 실행 속도 비교

```
Domain Test:      ~5ms     (외부 의존성 없음)
Application Test: ~50ms    (Mockito만)
Adapter Test:     ~1-2s    (Testcontainers)
```

### 의존성 방향

```
Test 의존성 방향 (아래로만 허용):
  Domain Test
      ↓
  Application Test (Domain mocking 가능)
      ↓
  Adapter Test (Domain + Application 사용)
```

---

## validation

```yaml
metadata:
  layer: "testing"
  category: "multi-module-testing"
  version: "1.0"

rules:
  - "Domain 모듈은 Spring Context 없이 순수 단위 테스트"
  - "Application 모듈은 Port mocking 기반 격리 테스트"
  - "Adapter 모듈은 실제 인프라와 통합 테스트"
  - "테스트 의존성 방향: Adapter → Application → Domain"

validation:
  antiPatterns:
    - "@SpringBootTest.*domain"  # Domain에서 Spring Context 금지
    - "@Testcontainers.*application"  # Application에서 Testcontainers 금지
    - "project\\(':adapter.*\\).*testImplementation"  # Application이 Adapter 의존 금지
