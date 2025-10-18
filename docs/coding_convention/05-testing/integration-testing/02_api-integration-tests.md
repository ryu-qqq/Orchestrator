# API Integration Tests - REST API 통합 테스트

**목적**: MockMvc 또는 RestAssured를 활용하여 REST API 엔드포인트를 실제 환경과 유사하게 테스트

**관련 문서**:
- [Testcontainers Setup](./01_testcontainers-setup.md)
- [Persistence Tests](./03_persistence-tests.md)

**검증 도구**: MockMvc, RestAssured, Testcontainers

---

## 📌 핵심 원칙

### API 통합 테스트의 목표

1. **End-to-End 검증**: Controller → Service → Repository → DB 전체 플로우
2. **HTTP 계약 검증**: Request/Response 형식, Status Code, Headers
3. **Validation 검증**: `@Valid` + Bean Validation 동작 확인
4. **Error Handling 검증**: Exception → Error Response 변환

---

## ❌ 금지 패턴 (Anti-Patterns)

### Anti-Pattern 1: MockMvc 없이 Controller 직접 호출

```java
// ❌ Controller를 일반 메서드처럼 직접 호출
@SpringBootTest
class OrderControllerTest {

    @Autowired
    private OrderController orderController;

    @Test
    void createOrder() {
        OrderRequest request = new OrderRequest(/* ... */);
        OrderResponse response = orderController.createOrder(request); // ❌ HTTP 계층 무시

        assertThat(response).isNotNull();
    }
}
```

**문제점**:
- HTTP 요청/응답 직렬화/역직렬화 미검증
- Status Code, Headers 검증 불가
- `@Valid` Validation 미작동
- Spring Security, Interceptor 우회

---

### Anti-Pattern 2: 단위 테스트와 통합 테스트 혼용

```java
// ❌ @WebMvcTest + Mocking (이것은 단위 테스트)
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @MockBean
    private CreateOrderUseCase createOrderUseCase; // ❌ UseCase Mocking

    @Test
    void createOrder() {
        when(createOrderUseCase.createOrder(any()))
            .thenReturn(OrderId.of(1L));

        // MockMvc 호출
    }
}
```

**문제점**:
- 실제 비즈니스 로직 미검증
- DB 연동 미검증
- False Positive (Mocking 때문에 성공)

---

## ✅ 올바른 통합 테스트 패턴

### 패턴 1: @SpringBootTest + MockMvc

```java
package com.company.application.in.web;

import com.company.application.IntegrationTestBase;
import com.company.domain.order.CustomerId;
import com.company.domain.order.OrderLineItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Order REST API 통합 테스트 (MockMvc)
 *
 * @author development-team
 * @since 1.0.0
 */
@AutoConfigureMockMvc
class OrderRestApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createOrder_ShouldReturnCreatedStatus() throws Exception {
        // Given
        String requestJson = """
            {
                "customerId": 1,
                "items": [
                    {
                        "productId": 101,
                        "quantity": 2,
                        "price": 1000.00
                    }
                ]
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.orderId").exists())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createOrder_WithInvalidRequest_ShouldReturnBadRequest() throws Exception {
        // Given - customerId 누락
        String invalidRequestJson = """
            {
                "items": []
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").exists());
    }
}
```

**검증 범위**:
- ✅ HTTP Status Code (201 Created, 400 Bad Request)
- ✅ Response Body JSON 구조
- ✅ Validation 에러 메시지
- ✅ Location Header

---

### 패턴 2: RestAssured (더 읽기 쉬운 BDD 스타일)

```java
package com.company.application.in.web;

import com.company.application.IntegrationTestBase;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Order REST API 통합 테스트 (RestAssured)
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderRestApiRestAssuredTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void createOrder_ShouldReturnCreatedStatus() {
        // Given
        String requestJson = """
            {
                "customerId": 1,
                "items": [
                    {"productId": 101, "quantity": 2, "price": 1000.00}
                ]
            }
            """;

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(requestJson)
        .when()
            .post("/api/orders")
        .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("orderId", notNullValue())
            .body("status", equalTo("PENDING"));
    }

    @Test
    void getOrder_ShouldReturnOrderDetails() {
        // Given - Order 생성
        Integer orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {"customerId": 1, "items": [{"productId": 101, "quantity": 2, "price": 1000.00}]}
                """)
            .post("/api/orders")
            .then().extract().path("orderId");

        // When & Then - Order 조회
        given()
            .pathParam("orderId", orderId)
        .when()
            .get("/api/orders/{orderId}")
        .then()
            .statusCode(200)
            .body("orderId", equalTo(orderId))
            .body("customerId", equalTo(1))
            .body("items", hasSize(1));
    }
}
```

**RestAssured 장점**:
- ✅ BDD 스타일 (Given-When-Then)
- ✅ 가독성 높음
- ✅ JSON Path 검증 간편
- ✅ 실제 HTTP 요청/응답

---

## 🎯 실전 예제: CRUD 통합 테스트

### ✅ Example 1: Order CRUD (MockMvc)

```java
package com.company.application.in.web;

import com.company.application.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Order CRUD 통합 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@AutoConfigureMockMvc
class OrderCrudIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void orderLifecycle_CreateReadUpdateDelete() throws Exception {
        // 1. CREATE
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "customerId": 1,
                        "items": [{"productId": 101, "quantity": 2, "price": 1000.00}]
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String location = createResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull();

        // 2. READ
        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        // 3. UPDATE (Approve)
        mockMvc.perform(patch(location + "/approve")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        // 4. DELETE
        mockMvc.perform(delete(location))
            .andExpect(status().isNoContent());

        // 5. Verify Deleted
        mockMvc.perform(get(location))
            .andExpect(status().isNotFound());
    }
}
```

---

### ✅ Example 2: Validation 에러 처리 (RestAssured)

```java
/**
 * Validation 통합 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderValidationIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void createOrder_WithNullCustomerId_ShouldReturnValidationError() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": null,
                    "items": [{"productId": 101, "quantity": 2, "price": 1000.00}]
                }
                """)
        .when()
            .post("/api/orders")
        .then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_FAILED"))
            .body("fieldErrors.customerId", containsString("must not be null"));
    }

    @Test
    void createOrder_WithEmptyItems_ShouldReturnValidationError() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": 1,
                    "items": []
                }
                """)
        .when()
            .post("/api/orders")
        .then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_FAILED"))
            .body("fieldErrors.items", containsString("must not be empty"));
    }

    @Test
    void createOrder_WithInvalidPrice_ShouldReturnValidationError() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "customerId": 1,
                    "items": [{"productId": 101, "quantity": 2, "price": -100.00}]
                }
                """)
        .when()
            .post("/api/orders")
        .then()
            .statusCode(400)
            .body("errorCode", equalTo("VALIDATION_FAILED"))
            .body("fieldErrors.items[0].price", containsString("must be positive"));
    }
}
```

---

## 🔧 고급 테스트 패턴

### 패턴 1: 인증/인가 통합 테스트

```java
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 인증/인가 통합 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@AutoConfigureMockMvc
class OrderSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createOrder_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId": 1, "items": [{"productId": 101, "quantity": 2, "price": 1000.00}]}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void createOrder_WithUserRole_ShouldReturnCreated() throws Exception {
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId": 1, "items": [{"productId": 101, "quantity": 2, "price": 1000.00}]}
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteOrder_WithAdminRole_ShouldReturnNoContent() throws Exception {
        // Given - Order 생성
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId": 1, "items": [{"productId": 101, "quantity": 2, "price": 1000.00}]}
                    """))
            .andReturn();

        String location = createResult.getResponse().getHeader("Location");

        // When & Then - 삭제
        mockMvc.perform(delete(location))
            .andExpect(status().isNoContent());
    }
}
```

---

### 패턴 2: Pagination 통합 테스트

```java
/**
 * Pagination 통합 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@AutoConfigureMockMvc
class OrderPaginationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listOrders_WithPagination_ShouldReturnPagedResults() throws Exception {
        // Given - 30개 Order 생성
        for (int i = 0; i < 30; i++) {
            createOrder();
        }

        // When & Then - Page 1 (size=10)
        mockMvc.perform(get("/api/orders")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(10)))
            .andExpect(jsonPath("$.totalElements").value(30))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.number").value(0));

        // Page 2
        mockMvc.perform(get("/api/orders")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(10)))
            .andExpect(jsonPath("$.number").value(1));
    }

    private void createOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"customerId": 1, "items": [{"productId": 101, "quantity": 2, "price": 1000.00}]}
                """));
    }
}
```

---

### 패턴 3: 동시성 테스트 (낙관적 잠금)

```java
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동시성 통합 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderConcurrencyIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void approveOrder_ConcurrentRequests_ShouldHandleOptimisticLocking() throws Exception {
        // Given - Order 생성
        Integer orderId = createOrder();

        // When - 10개 동시 승인 요청
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    given()
                        .pathParam("orderId", orderId)
                    .when()
                        .patch("/api/orders/{orderId}/approve")
                    .then()
                        .statusCode(200);
                    successCount.incrementAndGet();
                } catch (AssertionError e) {
                    if (e.getMessage().contains("409")) { // Conflict
                        conflictCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then - 1번만 성공, 나머지는 낙관적 잠금 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(9);
    }

    private Integer createOrder() {
        return given()
            .contentType(ContentType.JSON)
            .body("""
                {"customerId": 1, "items": [{"productId": 101, "quantity": 2, "price": 1000.00}]}
                """)
            .post("/api/orders")
            .then().extract().path("orderId");
    }
}
```

---

## 📋 API 통합 테스트 체크리스트

### 기본 검증
- [ ] HTTP Status Code (200, 201, 400, 404, 409, 500)
- [ ] Response Body JSON 구조
- [ ] Request/Response 직렬화/역직렬화
- [ ] Headers (Location, Content-Type)

### Validation 검증
- [ ] `@Valid` + Bean Validation 작동
- [ ] Validation 에러 메시지 형식
- [ ] Field Error vs Global Error

### Error Handling
- [ ] Business Exception → HTTP Status Code
- [ ] Error Response 형식 (errorCode, message, fieldErrors)
- [ ] Stack Trace 노출 방지

### 보안 검증
- [ ] 인증 (401 Unauthorized)
- [ ] 인가 (403 Forbidden)
- [ ] CSRF 보호
- [ ] XSS 방지

---

## 🛠️ Gradle 설정

**`build.gradle`**:
```gradle
dependencies {
    // MockMvc
    testImplementation 'org.springframework.boot:spring-boot-starter-test'

    // RestAssured (선택적)
    testImplementation 'io.rest-assured:rest-assured:5.3.0'
    testImplementation 'io.rest-assured:json-path:5.3.0'

    // Spring Security Test
    testImplementation 'org.springframework.security:spring-security-test'
}

tasks.named('test') {
    useJUnitPlatform()
    systemProperty 'spring.profiles.active', 'test'
}
```

---

## 📚 참고 자료

- [Spring MockMvc](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html)
- [RestAssured Documentation](https://rest-assured.io/)
- [Spring Security Test](https://docs.spring.io/spring-security/reference/servlet/test/index.html)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
