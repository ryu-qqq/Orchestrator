# 모듈 간 통합 테스트 (Cross-Module Integration Testing)

## 📋 목차
- [개요](#개요)
- [모듈 간 통합 테스트 전략](#모듈-간-통합-테스트-전략)
- [End-to-End 테스트](#end-to-end-테스트)
- [Module Boundary 테스트](#module-boundary-테스트)
- [Contract Testing](#contract-testing)
- [테스트 범위 정의](#테스트-범위-정의)
- [실전 예제](#실전-예제)

---

## 개요

### 목적

멀티모듈 프로젝트에서 **모듈 간 경계와 통신을 검증**하여 전체 시스템의 통합성을 보장합니다.

### 문제점: 모듈 격리 테스트만으로는 부족

✅ **Domain 단위 테스트**: Order.confirm() 정상 작동
✅ **Application 단위 테스트**: CreateOrderUseCase 정상 작동
✅ **Adapter 단위 테스트**: OrderPersistenceAdapter 정상 작동

❌ **하지만 전체가 함께 작동하는가?**
- Adapter → Application → Domain 의존성 주입 실패?
- DTO 변환 로직 오류?
- Transaction 경계 문제?
- 실제 DB 스키마와 Entity 불일치?

---

### 해결책: 계층적 통합 테스트

```
Level 1: Module Boundary Test (모듈 경계)
  → Adapter ↔ Application 통신 검증

Level 2: Slice Integration Test (레이어 슬라이스)
  → Controller → UseCase → Repository (실제 DB)

Level 3: End-to-End Test (전체 시스템)
  → REST API → 전체 흐름 → DB 저장 검증
```

---

## 모듈 간 통합 테스트 전략

### 1. 테스트 레벨 정의

| 레벨 | 범위 | 로딩 컨텍스트 | 실행 속도 | 목적 |
|------|------|--------------|----------|------|
| **Module Boundary** | Adapter ↔ Application | 부분 Context | 중간 (~500ms) | 모듈 간 계약 검증 |
| **Slice Integration** | 한 레이어 전체 | 슬라이스 Context | 중간 (~1s) | 레이어 내 통합 |
| **End-to-End** | 전체 시스템 | 전체 Context | 느림 (~3-5s) | 전체 흐름 검증 |

---

### 2. 테스트 피라미드 (멀티모듈)

```
        E2E Tests (전체 시스템)
       /                      \
      /    10% - 소수의 핵심 시나리오
     /____________________________________
    /                                      \
   /   Slice Integration Tests (레이어 슬라이스)
  /                                          \
 /     30% - 레이어 내 통합 검증
/____________________________________________\
                                              \
        Module Boundary Tests (모듈 경계)      |
                                              |
        40% - 모듈 간 계약 검증               |
______________________________________________________
                                                      \
            Unit Tests (단위 테스트)                  |
                                                      |
            60% - 개별 클래스 로직                    |
______________________________________________________

```

---

## End-to-End 테스트

### 1. 전체 시스템 E2E 테스트

**특징:**
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` 사용
- 전체 Spring Context 로딩
- 실제 HTTP 요청 → 전체 레이어 → 실제 DB
- 가장 느리지만 가장 강력한 검증

---

**OrderE2ETest.java**

```java
package com.company.template.order;

import com.company.template.order.adapter.in.web.dto.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Order 전체 시스템 End-to-End 테스트
 *
 * 흐름:
 * REST API → Controller → UseCase → Repository → Database
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderE2ETest {

    @LocalServerPort
    private int port;

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

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";
    }

    @Test
    void createOrder_FullFlow_ShouldPersistToDatabase() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
            100L,  // customerId
            List.of(
                new OrderItemRequest(1001L, 2, 10000.0),
                new OrderItemRequest(1002L, 1, 50000.0)
            )
        );

        // When - REST API 호출
        Long orderId = given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/orders")
        .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("orderId", notNullValue())
            .extract()
            .path("orderId");

        // Then - DB에서 조회하여 검증
        given()
        .when()
            .get("/orders/{orderId}", orderId)
        .then()
            .statusCode(200)
            .body("orderId", equalTo(orderId.intValue()))
            .body("customerId", equalTo(100))
            .body("items", hasSize(2))
            .body("items[0].productId", equalTo(1001))
            .body("items[0].quantity", equalTo(2))
            .body("totalAmount", equalTo(70000.0))
            .body("status", equalTo("PENDING"));
    }

    @Test
    void confirmOrder_FullFlow_ShouldUpdateStatus() {
        // Given - Order 생성
        Long orderId = createOrderViaApi();

        // When - Order 확정
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/orders/{orderId}/confirm", orderId)
        .then()
            .statusCode(200);

        // Then - 상태 확인
        given()
        .when()
            .get("/orders/{orderId}", orderId)
        .then()
            .statusCode(200)
            .body("status", equalTo("CONFIRMED"));
    }

    @Test
    void createOrder_WithInvalidData_ShouldReturnValidationError() {
        // Given - 잘못된 데이터
        CreateOrderRequest request = new CreateOrderRequest(
            null,  // ❌ customerId null
            List.of()  // ❌ items 빈 리스트
        );

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/orders")
        .then()
            .statusCode(400)
            .body("errors", hasSize(greaterThan(0)))
            .body("errors[0].field", anyOf(equalTo("customerId"), equalTo("items")));
    }

    // Helper method
    private Long createOrderViaApi() {
        CreateOrderRequest request = new CreateOrderRequest(
            100L,
            List.of(new OrderItemRequest(1001L, 1, 10000.0))
        );

        return given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/orders")
            .then()
            .statusCode(201)
            .extract()
            .path("orderId");
    }
}
```

---

### 2. @SpringBootTest 최적화

**문제점: 전체 Context 로딩 시간 (3-5초)**

✅ **해결책 1: 필요한 모듈만 로딩**

```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
        OrderApplication.class,  // Application 설정만
        OrderPersistenceConfig.class,  // Persistence 설정만
        OrderWebConfig.class  // Web 설정만
    }
)
@Testcontainers
class OrderE2ETest {
    // 불필요한 모듈 제외 → 로딩 시간 감소
}
```

---

✅ **해결책 2: @TestConfiguration으로 최소 설정**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(OrderE2ETest.TestConfig.class)
class OrderE2ETest {

    @TestConfiguration
    static class TestConfig {
        // 테스트에 필요한 최소 Bean만 정의
        @Bean
        public CreateOrderUseCase createOrderUseCase(SaveOrderPort saveOrderPort) {
            return new CreateOrderService(saveOrderPort);
        }
    }
}
```

---

## Module Boundary 테스트

### 1. Adapter → Application 경계 테스트

**목적:**
- Adapter가 Application Port를 올바르게 호출하는지 검증
- DTO 변환 로직 검증
- 의존성 주입 검증

---

**OrderRestControllerIntegrationTest.java**

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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST Adapter → Application Port 경계 테스트
 *
 * 검증 항목:
 * - DTO → Command 변환
 * - UseCase 호출
 * - Domain 응답 → DTO 변환
 */
@WebMvcTest(OrderRestController.class)
class OrderRestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void createOrder_ShouldConvertDtoToCommandAndCallUseCase() throws Exception {
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

        // Verify - UseCase가 올바른 Command로 호출되었는지
        verify(createOrderUseCase).execute(argThat(command ->
            command.customerId().equals(100L) &&
            command.items().size() == 1 &&
            command.items().get(0).productId().equals(1001L)
        ));
    }

    @Test
    void createOrder_WhenUseCaseThrowsException_ShouldReturnErrorResponse() throws Exception {
        // Given
        when(createOrderUseCase.execute(any(CreateOrderCommand.class)))
            .thenThrow(new CustomerNotFoundException("Customer not found"));

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "customerId": 999,
                        "items": []
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Customer not found"));
    }
}
```

---

### 2. Application → Persistence Adapter 경계 테스트

**목적:**
- Application이 Outbound Port를 올바르게 호출하는지 검증
- Persistence Adapter가 Port 계약을 준수하는지 검증

---

**OrderPersistenceAdapterIntegrationTest.java**

```java
package com.company.template.order.adapter.out.persistence;

import com.company.template.order.domain.model.*;
import com.company.template.order.domain.port.out.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Persistence Adapter → Repository → Database 경계 테스트
 *
 * 검증 항목:
 * - Domain → JPA Entity 변환
 * - JPA Entity → Domain 변환
 * - Port 계약 준수 (Optional 반환, 예외 처리 등)
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderPersistenceAdapter.class)
class OrderPersistenceAdapterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withReuse(true);

    @Autowired
    private SaveOrderPort saveOrderPort;

    @Autowired
    private LoadOrderPort loadOrderPort;

    @Test
    void saveAndLoad_ShouldConvertDomainToEntityAndBack() {
        // Given - Domain 객체
        Order order = Order.create(
            new OrderId(null),  // null for new entity
            new CustomerId(100L)
        );
        order.addItem(OrderItem.create(
            new ProductId(1001L),
            new Quantity(2),
            new Price(10000.0)
        ));

        // When - Save (Domain → Entity 변환)
        OrderId savedId = saveOrderPort.save(order);

        // Then - Load (Entity → Domain 변환)
        Optional<Order> loaded = loadOrderPort.loadById(savedId);

        assertThat(loaded).isPresent();
        Order loadedOrder = loaded.get();
        assertThat(loadedOrder.getOrderId()).isEqualTo(savedId);
        assertThat(loadedOrder.getCustomerId()).isEqualTo(new CustomerId(100L));
        assertThat(loadedOrder.getItems()).hasSize(1);
        assertThat(loadedOrder.getItems().get(0).getProductId()).isEqualTo(new ProductId(1001L));
    }

    @Test
    void loadById_WhenNotExists_ShouldReturnEmpty() {
        // When
        Optional<Order> result = loadOrderPort.loadById(new OrderId(999L));

        // Then - Port 계약 준수: Optional.empty() 반환
        assertThat(result).isEmpty();
    }

    @Test
    void save_WithMultipleItems_ShouldPersistAllItems() {
        // Given
        Order order = Order.create(
            new OrderId(null),
            new CustomerId(100L)
        );
        order.addItem(OrderItem.create(new ProductId(1001L), new Quantity(1), new Price(10000.0)));
        order.addItem(OrderItem.create(new ProductId(1002L), new Quantity(2), new Price(20000.0)));
        order.addItem(OrderItem.create(new ProductId(1003L), new Quantity(3), new Price(30000.0)));

        // When
        OrderId savedId = saveOrderPort.save(order);

        // Then - 모든 아이템이 영속화되었는지 검증
        Order loaded = loadOrderPort.loadById(savedId).orElseThrow();
        assertThat(loaded.getItems()).hasSize(3);
    }
}
```

---

## Contract Testing

### 1. Spring Cloud Contract (선택적)

**목적:**
- Producer (UseCase)와 Consumer (Controller) 간 계약 정의
- 계약 위반 시 빌드 실패

---

**계약 정의 (Groovy DSL)**

```groovy
// application/src/test/resources/contracts/createOrder.groovy
package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Create Order Contract"

    request {
        method POST()
        url "/api/orders"
        body([
            customerId: 100,
            items: [
                [productId: 1001, quantity: 2, price: 10000.0]
            ]
        ])
        headers {
            contentType(applicationJson())
        }
    }

    response {
        status 201
        headers {
            header("Location", execute('locationHeader()'))
        }
        body([
            orderId: $(anyPositiveInt())
        ])
    }
}
```

---

**Producer 검증 테스트 (자동 생성)**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureStubRunner
public class ContractVerifierTest extends ContractVerifierBase {

    @MockBean
    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    public void setup() {
        when(createOrderUseCase.execute(any()))
            .thenReturn(new OrderId(1L));
    }
}
```

---

### 2. 간단한 Contract Test (Manual)

**CreateOrderUseCaseContractTest.java**

```java
package com.company.template.order.application.contract;

import com.company.template.order.application.port.in.*;
import com.company.template.order.domain.model.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * CreateOrderUseCase의 계약 검증
 *
 * 검증 항목:
 * - 입력: CreateOrderCommand 받음
 * - 출력: OrderId 반환
 * - 예외: CustomerNotFoundException, InvalidOrderException
 */
class CreateOrderUseCaseContractTest {

    @Test
    void contract_InputOutput_ShouldMatch() {
        // Given - 입력 타입 검증
        CreateOrderCommand command = new CreateOrderCommand(
            100L,
            List.of(new OrderItemRequest(1001L, 2, 10000.0))
        );

        // When - UseCase 호출 (Mock 또는 실제 구현)
        // CreateOrderUseCase useCase = ...;
        // OrderId orderId = useCase.execute(command);

        // Then - 출력 타입 검증
        // assertThat(orderId).isNotNull();
        // assertThat(orderId).isInstanceOf(OrderId.class);
    }

    @Test
    void contract_ExceptionCases_ShouldBeDefined() {
        // Given
        CreateOrderCommand command = new CreateOrderCommand(999L, List.of());

        // When & Then - 예외 계약 검증
        // assertThatThrownBy(() -> useCase.execute(command))
        //     .isInstanceOf(CustomerNotFoundException.class);
    }
}
```

---

## 테스트 범위 정의

### 1. 모듈별 통합 테스트 범위

| 모듈 | 단위 테스트 | 모듈 내 통합 | 모듈 간 통합 | E2E |
|------|-----------|------------|------------|-----|
| **Domain** | ✅ 100% | ❌ 없음 | ❌ 없음 | ❌ 없음 |
| **Application** | ✅ 100% | ✅ UseCase → Multiple Ports | ⚠️ 제한적 (Mock Adapter) | ❌ 없음 |
| **Adapter** | ✅ 주요 로직만 | ✅ Adapter → 실제 인프라 | ✅ Adapter → Application | ⚠️ 선택적 |
| **전체** | - | - | - | ✅ 핵심 시나리오만 |

---

### 2. 테스트 태그 전략 (다음 문서에서 상세)

```java
@Tag("unit")           // Domain, Application 단위 테스트
@Tag("integration")    // Adapter 통합 테스트
@Tag("boundary")       // 모듈 경계 테스트
@Tag("e2e")            // 전체 시스템 E2E 테스트
```

---

## 실전 예제

### 시나리오: Order 생성 전체 흐름 테스트

#### 1. 레이어별 테스트 (격리)

```java
// Domain 단위 테스트
@Test
void domain_CreateOrder_ShouldWork() {
    Order order = Order.create(new OrderId(1L), new CustomerId(100L));
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
}

// Application 단위 테스트 (Port Mocking)
@Test
void application_CreateOrderUseCase_ShouldCallPort() {
    when(saveOrderPort.save(any())).thenReturn(new OrderId(1L));
    OrderId orderId = createOrderUseCase.execute(command);
    assertThat(orderId).isNotNull();
}

// Adapter 통합 테스트 (실제 DB)
@Test
void adapter_SaveOrder_ShouldPersist() {
    OrderId savedId = saveOrderPort.save(order);
    assertThat(savedId).isNotNull();
}
```

---

#### 2. 모듈 경계 테스트

```java
// Controller → UseCase 경계
@WebMvcTest(OrderRestController.class)
@Test
void boundary_Controller_ShouldCallUseCase() {
    mockMvc.perform(post("/api/orders").content(...))
        .andExpect(status().isCreated());
    verify(createOrderUseCase).execute(any());
}

// UseCase → Repository 경계
@DataJpaTest
@Import(OrderPersistenceAdapter.class)
@Test
void boundary_Adapter_ShouldSaveAndLoad() {
    OrderId savedId = saveOrderPort.save(order);
    Order loaded = loadOrderPort.loadById(savedId).orElseThrow();
    assertThat(loaded.getOrderId()).isEqualTo(savedId);
}
```

---

#### 3. End-to-End 테스트

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@Test
void e2e_CreateOrder_FullFlow() {
    // REST API 호출
    Long orderId = given()
        .contentType(JSON)
        .body(createOrderRequest)
        .post("/api/orders")
        .then()
        .statusCode(201)
        .extract().path("orderId");

    // DB 검증
    given()
        .get("/api/orders/{orderId}", orderId)
        .then()
        .statusCode(200)
        .body("status", equalTo("PENDING"));
}
```

---

## 요약

### 핵심 원칙

| 테스트 레벨 | 범위 | 실행 빈도 | 목적 |
|-----------|------|----------|------|
| **단위 테스트** | 개별 클래스 | 항상 (PR 빌드) | 로직 정확성 |
| **Module Boundary** | 모듈 간 계약 | PR 빌드 | 통신 정확성 |
| **Slice Integration** | 레이어 슬라이스 | 메인 빌드 | 레이어 통합 |
| **E2E** | 전체 시스템 | 배포 전 | 전체 흐름 |

### 테스트 전략

```
개발 중:   단위 테스트 (빠른 피드백)
PR 빌드:   단위 + Module Boundary (5분 이내)
메인 빌드: + Slice Integration (10분 이내)
배포 전:   + E2E (15분 이내)
```

### 최적화 팁

1. **Context 재사용**: `@DirtiesContext` 최소화
2. **Testcontainers 재사용**: `.withReuse(true)` 활성화
3. **병렬 실행**: JUnit 5 parallel execution 활용
4. **선택적 실행**: `@Tag`로 테스트 그룹화

---

## validation

```yaml
metadata:
  layer: "testing"
  category: "multi-module-testing"
  version: "1.0"

rules:
  - "모듈 간 경계는 Module Boundary 테스트로 검증"
  - "전체 시스템 흐름은 E2E 테스트로 검증 (소수만)"
  - "@SpringBootTest는 E2E에만 사용, Module Boundary는 @WebMvcTest/@DataJpaTest"
  - "Contract Testing으로 Producer-Consumer 계약 정의"

validation:
  antiPatterns:
    - "@SpringBootTest.*@WebMvcTest"  # WebMvcTest와 SpringBootTest 중복
    - "@SpringBootTest.*단위.*테스트"  # SpringBootTest를 단위 테스트에 사용
