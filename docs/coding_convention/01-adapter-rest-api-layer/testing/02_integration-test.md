# REST API 통합 테스트 (Integration Test)

**목적**: @SpringBootTest를 사용하여 REST API의 전체 플로우(Controller → UseCase → Repository → DB)를 실제 환경과 유사하게 검증

**관련 문서**:
- [Controller 단위 테스트](./01_controller-unit-test.md) - @WebMvcTest 단위 테스트
- [Spring REST Docs](./03_rest-docs.md) - API 문서화
- [Application Service Testing](../../03-application-layer/testing/01_application-service-testing.md)
- [Testcontainers Integration](../../04-persistence-layer/testing/02_testcontainers-integration.md)

**검증 도구**: @SpringBootTest, MockMvc, TestRestTemplate, RestAssured, Testcontainers

---

## 📌 핵심 원칙

### 통합 테스트 vs 단위 테스트

| 비교 항목 | 단위 테스트 (@WebMvcTest) | 통합 테스트 (@SpringBootTest) |
|----------|--------------------------|----------------------------|
| **로딩 범위** | Controller Layer만 | 전체 Spring Context |
| **UseCase** | @MockBean으로 Mocking | 실제 UseCase 실행 |
| **Database** | 미연동 | 실제 DB 연동 (H2/Testcontainers) |
| **검증 범위** | HTTP 요청/응답만 | End-to-End 플로우 전체 |
| **실행 속도** | 빠름 (~100ms) | 느림 (~1-3초) |
| **목적** | Controller 계층 격리 테스트 | 전체 시스템 동작 검증 |

### 통합 테스트의 목표

1. **End-to-End 검증**: Controller → UseCase → Domain → Repository → DB 전체 플로우
2. **실제 비즈니스 로직 검증**: UseCase 실행 및 Domain 로직 작동 확인
3. **Database 연동 검증**: 실제 저장/조회/수정/삭제 동작 확인
4. **트랜잭션 경계 검증**: @Transactional 동작 및 롤백 확인
5. **HTTP 계약 검증**: Request/Response 형식, Status Code, Headers

---

## ❌ 금지 패턴 (Anti-Patterns)

### Anti-Pattern 1: MockMvc 없이 Controller 직접 호출

```java
// ❌ Controller를 일반 메서드처럼 직접 호출
@SpringBootTest
class OrderCommandControllerTest {

    @Autowired
    private OrderCommandController orderController;

    @Test
    void createOrder() {
        CreateOrderRequest request = new CreateOrderRequest(
            1L, List.of(new OrderItemRequest(101L, 2)), null
        );

        // ❌ HTTP 계층 무시
        OrderApiResponse response = orderController.createOrder(request);

        assertThat(response).isNotNull();
    }
}
```

**문제점**:
- HTTP 요청/응답 직렬화/역직렬화 미검증
- Status Code, Headers 검증 불가
- `@Valid` Bean Validation 미작동
- Spring Security, Interceptor 우회
- Content-Type, Accept 협상 미검증

---

### Anti-Pattern 2: 단위 테스트와 통합 테스트 혼용

```java
// ❌ @SpringBootTest인데 UseCase를 Mocking (이것은 통합 테스트가 아님)
@SpringBootTest
@AutoConfigureMockMvc
class OrderCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean  // ❌ 통합 테스트에서 Mocking 금지
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void createOrder() throws Exception {
        // ❌ Mocking으로 실제 비즈니스 로직 우회
        given(createOrderUseCase.createOrder(any()))
            .willReturn(new CreateOrderUseCase.Response(1L, "PENDING", "2000.00", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{...}"))
            .andExpect(status().isCreated());
    }
}
```

**문제점**:
- 실제 비즈니스 로직 미검증 (Mocking으로 우회)
- Database 연동 미검증
- False Positive (Mocking 때문에 테스트 성공)
- 통합 테스트의 목적 상실 (단위 테스트로 충분)

**올바른 방법**: UseCase를 Mocking하지 말고 실제로 실행

---

### Anti-Pattern 3: Database 검증 누락

```java
// ❌ HTTP 응답만 검증하고 DB 저장 미검증
@SpringBootTest
@AutoConfigureMockMvc
class OrderCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // orderRepository 주입 안 함 ❌

    @Test
    void createOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{...}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").exists());

        // ❌ DB 검증 누락: 실제로 저장되었는지 확인 안 함
    }
}
```

**문제점**:
- HTTP 응답만 검증 (실제 DB 저장 미확인)
- Repository 계층 동작 미검증
- 트랜잭션 롤백 여부 미확인

**올바른 방법**: Repository를 주입받아 DB 상태 검증

---

## ✅ 올바른 통합 테스트 패턴

### 패턴 1: @SpringBootTest + MockMvc (기본 패턴)

```java
package com.company.application.in.web;

import com.company.adapter.out.persistence.OrderJpaRepository;
import com.company.adapter.out.persistence.entity.OrderEntity;
import com.company.adapter.out.persistence.entity.OrderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Order Command API 통합 테스트
 *
 * 검증 범위:
 * - HTTP Request/Response
 * - UseCase 실행
 * - Domain Logic
 * - Database 저장/조회
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class OrderCommandControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderJpaRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_WithValidRequest_ShouldPersistToDatabase() throws Exception {
        // Given
        String requestJson = """
            {
              "customerId": 1,
              "items": [
                {
                  "productId": 101,
                  "quantity": 2
                }
              ],
              "notes": "Test order"
            }
            """;

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.orderId").exists())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();

        // Then - HTTP 응답 검증
        String location = result.getResponse().getHeader("Location");
        assertThat(location).contains("/api/v1/orders/");

        // Then - DB 검증 (중요!)
        Long orderId = extractOrderIdFromLocation(location);
        OrderEntity savedOrder = orderRepository.findById(orderId).orElseThrow();

        assertThat(savedOrder.getId()).isEqualTo(orderId);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getCustomerId()).isEqualTo(1L);
        assertThat(savedOrder.getOrderLines()).hasSize(1);
        assertThat(savedOrder.getOrderLines().get(0).getProductId()).isEqualTo(101L);
        assertThat(savedOrder.getOrderLines().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void createOrder_WithInvalidRequest_ShouldNotPersistToDatabase() throws Exception {
        // Given - customerId null
        String invalidRequestJson = """
            {
              "customerId": null,
              "items": []
            }
            """;

        // When
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors").isArray());

        // Then - DB 검증: 아무것도 저장되지 않음
        assertThat(orderRepository.findAll()).isEmpty();
    }

    private Long extractOrderIdFromLocation(String location) {
        // "/api/v1/orders/123" -> 123
        String[] parts = location.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
```

**핵심 포인트**:
- ✅ @SpringBootTest: 전체 Spring Context 로딩
- ✅ @AutoConfigureMockMvc: MockMvc 자동 설정
- ✅ Repository 주입: DB 상태 검증
- ✅ @AfterEach: 테스트 격리 (DB 초기화)
- ✅ HTTP + DB 검증: 두 가지 모두 확인

---

### 패턴 2: @SpringBootTest + TestRestTemplate (실제 HTTP 요청)

```java
package com.company.application.in.web;

import com.company.adapter.out.persistence.OrderJpaRepository;
import com.company.application.in.web.dto.request.CreateOrderRequest;
import com.company.application.in.web.dto.request.OrderItemRequest;
import com.company.application.in.web.dto.response.OrderApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order Command API 통합 테스트 (TestRestTemplate)
 *
 * TestRestTemplate은 실제 HTTP 요청을 보냄 (MockMvc는 시뮬레이션)
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class OrderCommandControllerRestTemplateTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderJpaRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_WithValidRequest_ShouldReturn201Created() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
            1L,
            List.of(new OrderItemRequest(101L, 2)),
            "Test order"
        );

        // When
        ResponseEntity<OrderApiResponse> response = restTemplate.postForEntity(
            "/api/v1/orders",
            request,
            OrderApiResponse.class
        );

        // Then - HTTP 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("PENDING");

        // Then - DB 검증
        Long orderId = response.getBody().orderId();
        var savedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(savedOrder.getCustomerId()).isEqualTo(1L);
    }

    @Test
    void getOrder_WithExistingId_ShouldReturn200Ok() {
        // Given - Order 생성
        CreateOrderRequest request = new CreateOrderRequest(
            1L,
            List.of(new OrderItemRequest(101L, 2)),
            null
        );
        ResponseEntity<OrderApiResponse> createResponse = restTemplate.postForEntity(
            "/api/v1/orders",
            request,
            OrderApiResponse.class
        );
        Long orderId = createResponse.getBody().orderId();

        // When - Order 조회
        ResponseEntity<OrderApiResponse> getResponse = restTemplate.getForEntity(
            "/api/v1/orders/" + orderId,
            OrderApiResponse.class
        );

        // Then
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().orderId()).isEqualTo(orderId);
        assertThat(getResponse.getBody().customerId()).isEqualTo(1L);
    }
}
```

**TestRestTemplate vs MockMvc**:
- **TestRestTemplate**: 실제 HTTP 요청 (Embedded Tomcat 사용)
- **MockMvc**: HTTP 시뮬레이션 (Servlet Container 없이 테스트)

**언제 TestRestTemplate 사용?**
- Spring Security 필터 체인 검증
- CORS 설정 검증
- Interceptor 동작 검증
- 실제 HTTP 환경 필요 시

---

### 패턴 3: RestAssured (BDD 스타일, 선택적)

```java
package com.company.application.in.web;

import com.company.adapter.out.persistence.OrderJpaRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Order Command API 통합 테스트 (RestAssured)
 *
 * RestAssured는 BDD 스타일의 가독성 높은 테스트 작성 가능
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class OrderCommandControllerRestAssuredTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrderJpaRepository orderRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_WithValidRequest_ShouldReturn201Created() {
        // Given
        String requestJson = """
            {
              "customerId": 1,
              "items": [{"productId": 101, "quantity": 2}],
              "notes": "Test order"
            }
            """;

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(requestJson)
        .when()
            .post("/api/v1/orders")
        .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("orderId", notNullValue())
            .body("status", equalTo("PENDING"))
            .body("customerId", equalTo(1));
    }

    @Test
    void getOrder_WithNonExistingId_ShouldReturn404NotFound() {
        given()
            .pathParam("orderId", 99999)
        .when()
            .get("/api/v1/orders/{orderId}")
        .then()
            .statusCode(404)
            .body("code", equalTo("ORDER_NOT_FOUND"))
            .body("message", containsString("Order not found"));
    }
}
```

**RestAssured 장점**:
- ✅ BDD 스타일 (Given-When-Then)
- ✅ 가독성 매우 높음
- ✅ JSON Path 검증 간편
- ✅ 복잡한 API 테스트에 적합

**의존성 추가** (build.gradle):
```gradle
testImplementation 'io.rest-assured:rest-assured:5.3.0'
```

---

## 🎯 실전 예제

### Example 1: CRUD Lifecycle (생성 → 조회 → 수정 → 삭제)

```java
/**
 * Order CRUD Lifecycle 통합 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class OrderCrudLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderJpaRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void orderLifecycle_CreateReadUpdateDelete() throws Exception {
        // 1. CREATE - Order 생성
        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": 1,
                      "items": [{"productId": 101, "quantity": 2}]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

        String location = createResult.getResponse().getHeader("Location");
        Long orderId = extractOrderIdFromLocation(location);

        // 2. READ - Order 조회
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.customerId").value(1));

        // 3. UPDATE - Order 승인
        mockMvc.perform(patch("/api/v1/orders/{orderId}/approve", orderId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        // Then - DB 검증: 상태 변경됨
        var updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.APPROVED);

        // 4. DELETE - Order 취소
        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isNoContent());

        // Then - DB 검증: 삭제됨
        assertThat(orderRepository.findById(orderId)).isEmpty();

        // 5. VERIFY DELETED - 삭제된 Order 조회 시 404
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private Long extractOrderIdFromLocation(String location) {
        String[] parts = location.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
```

---

### Example 2: Validation 에러 처리 통합 테스트

```java
/**
 * Validation 통합 테스트
 * Bean Validation + GlobalExceptionHandler 동작 검증
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class OrderValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderJpaRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_WithNullCustomerId_ShouldReturnValidationError() throws Exception {
        // Given - customerId null
        String invalidRequestJson = """
            {
              "customerId": null,
              "items": [{"productId": 101, "quantity": 2}]
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[?(@.field == 'customerId')]").exists())
            .andExpect(jsonPath("$.errors[?(@.field == 'customerId')].message")
                .value(containsString("must not be null")));

        // Then - DB 검증: 아무것도 저장되지 않음
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void createOrder_WithEmptyItems_ShouldReturnValidationError() throws Exception {
        // Given - items 빈 배열
        String invalidRequestJson = """
            {
              "customerId": 1,
              "items": []
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[?(@.field == 'items')]").exists())
            .andExpect(jsonPath("$.errors[?(@.field == 'items')].message")
                .value(containsString("must not be empty")));

        // Then - DB 검증
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void createOrder_WithNegativeQuantity_ShouldReturnValidationError() throws Exception {
        // Given - quantity 음수
        String invalidRequestJson = """
            {
              "customerId": 1,
              "items": [{"productId": 101, "quantity": -1}]
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[?(@.field == 'items[0].quantity')]").exists());

        // Then - DB 검증
        assertThat(orderRepository.findAll()).isEmpty();
    }
}
```

---

### Example 3: Exception → ErrorResponse 변환 통합 테스트

```java
/**
 * Exception Handling 통합 테스트
 * Domain Exception → GlobalExceptionHandler → ErrorResponse 변환 검증
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class OrderExceptionHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderJpaRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void getOrder_WithNonExistingId_ShouldReturn404NotFound() throws Exception {
        // Given - 존재하지 않는 orderId
        Long nonExistingOrderId = 99999L;

        // When & Then - OrderNotFoundException → 404
        mockMvc.perform(get("/api/v1/orders/{orderId}", nonExistingOrderId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value(containsString("Order not found")))
            .andExpect(jsonPath("$.message").value(containsString("99999")));
    }

    @Test
    void approveOrder_WithAlreadyApprovedOrder_ShouldReturn409Conflict() throws Exception {
        // Given - 이미 승인된 Order 생성
        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": 1,
                      "items": [{"productId": 101, "quantity": 2}]
                    }
                    """))
            .andReturn();

        Long orderId = extractOrderIdFromLocation(
            createResult.getResponse().getHeader("Location")
        );

        // 첫 번째 승인 (성공)
        mockMvc.perform(patch("/api/v1/orders/{orderId}/approve", orderId))
            .andExpect(status().isOk());

        // When & Then - 두 번째 승인 시도 (실패)
        mockMvc.perform(patch("/api/v1/orders/{orderId}/approve", orderId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_ALREADY_APPROVED"))
            .andExpect(jsonPath("$.message").value(containsString("already approved")));
    }

    private Long extractOrderIdFromLocation(String location) {
        String[] parts = location.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
```

---

### Example 4: Pagination 통합 테스트

```java
/**
 * Pagination 통합 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class OrderPaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderJpaRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void listOrders_WithPagination_ShouldReturnPagedResults() throws Exception {
        // Given - 30개 Order 생성
        for (int i = 1; i <= 30; i++) {
            createOrder(i);
        }

        // When & Then - Page 1 (size=10)
        mockMvc.perform(get("/api/v1/orders")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(10)))
            .andExpect(jsonPath("$.totalElements").value(30))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(false));

        // Page 2 (size=10)
        mockMvc.perform(get("/api/v1/orders")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(10)))
            .andExpect(jsonPath("$.number").value(1))
            .andExpect(jsonPath("$.first").value(false))
            .andExpect(jsonPath("$.last").value(false));

        // Page 3 (size=10) - 마지막 페이지
        mockMvc.perform(get("/api/v1/orders")
                .param("page", "2")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(10)))
            .andExpect(jsonPath("$.number").value(2))
            .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void listOrders_WithCustomPageSize_ShouldReturnRequestedSize() throws Exception {
        // Given - 15개 Order 생성
        for (int i = 1; i <= 15; i++) {
            createOrder(i);
        }

        // When & Then - size=5
        mockMvc.perform(get("/api/v1/orders")
                .param("page", "0")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(5)))
            .andExpect(jsonPath("$.totalElements").value(15))
            .andExpect(jsonPath("$.totalPages").value(3));
    }

    private void createOrder(int customerId) throws Exception {
        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("""
                {
                  "customerId": %d,
                  "items": [{"productId": 101, "quantity": 1}]
                }
                """, customerId)));
    }
}
```

---

## 🔧 고급 패턴

### 패턴 1: Testcontainers 통합 (Real DB)

```java
package com.company.application.in.web.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers 기반 통합 테스트 Base 클래스
 * PostgreSQL Container 자동 실행
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@Testcontainers
public abstract class PostgresIntegrationTestBase {

    @Container
    protected static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true); // Container 재사용 (성능 향상)

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**사용 예시**:
```java
@AutoConfigureMockMvc
@Tag("integration")
@Tag("database")
class OrderApiPostgresIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Test
    void createOrder_ShouldPersistToPostgreSQL() throws Exception {
        // PostgreSQL Container에 실제로 저장됨
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{...}"))
            .andExpect(status().isCreated());

        // PostgreSQL에서 조회 검증
        assertThat(orderRepository.findAll()).hasSize(1);
    }
}
```

**Gradle 의존성**:
```gradle
testImplementation 'org.testcontainers:postgresql:1.19.0'
```

---

### 패턴 2: 인증/인가 통합 테스트

```java
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 인증/인가 통합 테스트
 * Spring Security 동작 검증
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class OrderSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createOrder_WithoutAuthentication_ShouldReturn401Unauthorized() throws Exception {
        // Given - 인증 없음
        String requestJson = """
            {
              "customerId": 1,
              "items": [{"productId": 101, "quantity": 2}]
            }
            """;

        // When & Then - 401 Unauthorized
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void createOrder_WithUserRole_ShouldReturn201Created() throws Exception {
        // Given - USER 권한
        String requestJson = """
            {
              "customerId": 1,
              "items": [{"productId": 101, "quantity": 2}]
            }
            """;

        // When & Then - 성공
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteOrder_WithAdminRole_ShouldReturn204NoContent() throws Exception {
        // Given - Order 생성
        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": 1,
                      "items": [{"productId": 101, "quantity": 2}]
                    }
                    """))
            .andReturn();

        Long orderId = extractOrderIdFromLocation(
            createResult.getResponse().getHeader("Location")
        );

        // When & Then - ADMIN만 삭제 가능
        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void deleteOrder_WithUserRole_ShouldReturn403Forbidden() throws Exception {
        // Given - Order 생성 (ADMIN으로)
        // ...

        // When & Then - USER는 삭제 불가
        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isForbidden());
    }

    private Long extractOrderIdFromLocation(String location) {
        String[] parts = location.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
```

---

### 패턴 3: 동시성 테스트 (낙관적 잠금)

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동시성 통합 테스트
 * 낙관적 잠금 (Optimistic Locking) 검증
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class OrderConcurrencyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderJpaRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void approveOrder_ConcurrentRequests_ShouldHandleOptimisticLocking() throws Exception {
        // Given - Order 생성
        CreateOrderRequest request = new CreateOrderRequest(
            1L,
            List.of(new OrderItemRequest(101L, 2)),
            null
        );
        ResponseEntity<OrderApiResponse> createResponse = restTemplate.postForEntity(
            "/api/v1/orders",
            request,
            OrderApiResponse.class
        );
        Long orderId = createResponse.getBody().orderId();

        // When - 10개 동시 승인 요청
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<Void> response = restTemplate.exchange(
                        "/api/v1/orders/" + orderId + "/approve",
                        HttpMethod.PATCH,
                        null,
                        Void.class
                    );

                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then - 1번만 성공, 나머지는 낙관적 잠금 실패 (409 Conflict)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(9);

        // DB 검증: APPROVED 상태 1번만
        var order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
    }
}
```

---

## 📋 통합 테스트 체크리스트

### 기본 검증
- [ ] @SpringBootTest 사용
- [ ] @AutoConfigureMockMvc 또는 TestRestTemplate 사용
- [ ] HTTP Status Code 검증 (200, 201, 400, 404, 409)
- [ ] Response Body JSON 구조 검증
- [ ] Headers 검증 (Location, Content-Type)

### Database 검증
- [ ] Repository 주입 및 DB 상태 검증
- [ ] 정상 케이스: DB 저장 확인
- [ ] 에러 케이스: DB 미저장 확인
- [ ] @AfterEach로 DB 초기화 (테스트 격리)

### UseCase 검증
- [ ] 실제 UseCase 실행 (Mocking 금지)
- [ ] Domain Logic 동작 확인
- [ ] 트랜잭션 경계 확인

### Validation 검증
- [ ] `@Valid` + Bean Validation 작동
- [ ] Validation 에러 메시지 형식
- [ ] FieldError vs GlobalError

### Exception Handling
- [ ] Domain Exception → HTTP Status Code 변환
- [ ] ErrorResponse 형식 (code, message, errors)
- [ ] Stack Trace 노출 방지

### 선택적 검증
- [ ] 인증/인가 (Spring Security)
- [ ] Pagination (Page, Pageable)
- [ ] 동시성 (Optimistic Locking)
- [ ] Testcontainers (Real DB)

---

## 🚫 통합 테스트에서 피해야 할 것

### ❌ 금지 사항
- UseCase를 @MockBean으로 Mocking (통합 테스트 목적 상실)
- Controller 직접 호출 (HTTP 계층 무시)
- DB 검증 누락 (HTTP 응답만 검증)
- 테스트 간 의존성 (@TestMethodOrder 사용 금지)
- 테스트 격리 실패 (DB 초기화 누락)

### ⚠️ 주의 사항
- @SpringBootTest는 느림 (전체 Context 로딩)
- 필요한 경우만 통합 테스트 작성 (단위 테스트 우선)
- @AfterEach로 DB 초기화 필수
- Testcontainers 재사용 활성화 (withReuse(true))

---

## 🛠️ Gradle 설정

**build.gradle**:
```gradle
dependencies {
    // Spring Boot Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'

    // MockMvc (자동 포함)
    // testImplementation 'org.springframework.boot:spring-boot-starter-web'

    // RestAssured (선택적)
    testImplementation 'io.rest-assured:rest-assured:5.3.0'
    testImplementation 'io.rest-assured:json-path:5.3.0'

    // Testcontainers (Real DB)
    testImplementation 'org.testcontainers:postgresql:1.19.0'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.0'

    // Spring Security Test
    testImplementation 'org.springframework.security:spring-security-test'
}

tasks.named('test') {
    useJUnitPlatform {
        // 통합 테스트만 실행
        includeTags 'integration'

        // 또는 제외
        // excludeTags 'integration'
    }
    systemProperty 'spring.profiles.active', 'test'
}
```

---

## 📚 참고 자료

- [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)
- [MockMvc](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html)
- [RestAssured Documentation](https://rest-assured.io/)
- [Testcontainers](https://testcontainers.com/)
- [Spring Security Test](https://docs.spring.io/spring-security/reference/servlet/test/index.html)

---

**다음 문서**: [Spring REST Docs](./03_rest-docs.md)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
