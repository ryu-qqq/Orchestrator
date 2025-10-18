# Controller Unit Test - @WebMvcTest 단위 테스트

> **목적**: Controller Layer의 HTTP 요청/응답 처리 로직 검증
>
> **레이어**: Adapter Layer (REST API)
>
> **위치**: `adapter/in/web/controller/` 테스트
>
> **관련 문서**:
> - `../controller-design/02_request-validation.md` - 요청 검증 전략
> - `../controller-design/03_response-handling.md` - 응답 처리 패턴
> - `../../03-application-layer/testing/01_application-service-testing.md` - UseCase 테스트
>
> **필수 버전**: Spring Boot 3.0+, JUnit 5, MockMvc

---

## 📌 핵심 원칙

### 1. @WebMvcTest - Controller Layer만 테스트

**Spring MVC 구성요소만 로딩 (빠른 실행)**

```java
@WebMvcTest(OrderCommandController.class)
class OrderCommandControllerTest {
    // Controller + MockMvc만 로딩
    // UseCase는 @MockBean으로 Mocking
}
```

**장점**:
- ✅ **빠른 실행**: 전체 Context 로딩 불필요
- ✅ **격리된 테스트**: Controller 로직만 검증
- ✅ **MockMvc 자동 설정**: HTTP 요청/응답 시뮬레이션

### 2. UseCase Port Mocking

**비즈니스 로직은 Mocking, HTTP 처리만 테스트**

```
MockMvc → Controller → @MockBean UseCase
                  ↓
            HTTP 200 OK + JSON Response
```

### 3. 검증 대상

**Controller 단위 테스트에서 검증할 것**:
- ✅ HTTP 메서드 매핑 (`@GetMapping`, `@PostMapping`)
- ✅ 요청 검증 (`@Valid`, Bean Validation)
- ✅ 응답 상태 코드 (200, 201, 400, 404)
- ✅ JSON 직렬화/역직렬화
- ✅ Mapper 호출 (API DTO ↔ UseCase DTO)
- ✅ ErrorResponse 형식

**Controller 단위 테스트에서 검증하지 말 것**:
- ❌ 비즈니스 로직 (UseCase에서 테스트)
- ❌ 데이터베이스 연동 (Integration Test)
- ❌ 외부 API 호출 (Integration Test)

---

## ❌ Anti-Pattern: 전체 Context 로딩

### 문제: @SpringBootTest로 Controller 테스트

```java
// ❌ Bad: 전체 Spring Context 로딩 (느림)
@SpringBootTest
@AutoConfigureMockMvc
class OrderCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 전체 Bean 로딩 (DB, Cache, Security 등)
    // Controller 테스트에 불필요한 리소스 낭비
}
```

**문제점**:
- 🔴 **느린 실행**: 전체 Spring Context 로딩 (5-10초)
- 🔴 **불필요한 의존성**: DB, Redis, Security 등 모두 로딩
- 🔴 **테스트 격리 실패**: 다른 Bean의 영향 받음

---

## ✅ Best Practice: @WebMvcTest 사용

### 패턴 1: 기본 Controller 테스트

```java
package com.company.adapter.in.web.controller;

import com.company.adapter.in.web.dto.*;
import com.company.adapter.in.web.mapper.OrderApiMapper;
import com.company.application.port.in.CreateOrderUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderCommandController 단위 테스트
 *
 * <p>@WebMvcTest를 사용하여 Controller Layer만 테스트합니다.
 * UseCase는 @MockBean으로 Mocking하여 HTTP 처리 로직만 검증합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
@WebMvcTest(OrderCommandController.class)
class OrderCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateOrderUseCase createOrderUseCase;

    @MockBean
    private OrderApiMapper orderApiMapper;

    @Test
    void createOrder_WithValidRequest_ShouldReturn201Created() throws Exception {
        // Given
        CreateOrderUseCase.Command command = new CreateOrderUseCase.Command(
            1L,
            List.of(new CreateOrderUseCase.Command.OrderItem(101L, 2)),
            "Test order"
        );

        CreateOrderUseCase.Response useCaseResponse = new CreateOrderUseCase.Response(
            1L,
            "PENDING",
            "2000.00",
            LocalDateTime.now()
        );

        OrderApiResponse apiResponse = new OrderApiResponse(
            1L,
            "PENDING",
            "2000.00",
            LocalDateTime.now()
        );

        given(orderApiMapper.toCommand(any(CreateOrderRequest.class)))
            .willReturn(command);
        given(createOrderUseCase.createOrder(command))
            .willReturn(useCaseResponse);
        given(orderApiMapper.toApiResponse(useCaseResponse))
            .willReturn(apiResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
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
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.orderId").value(1))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalAmount").value("2000.00"))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createOrder_WithInvalidRequest_ShouldReturn400BadRequest() throws Exception {
        // Given: customerId null, items empty

        // When & Then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": null,
                      "items": [],
                      "notes": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[?(@.field == 'customerId')]").exists())
            .andExpect(jsonPath("$.errors[?(@.field == 'items')]").exists());
    }

    @Test
    void createOrder_WithNegativeQuantity_ShouldReturn400BadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": 1,
                      "items": [
                        {
                          "productId": 101,
                          "quantity": -1
                        }
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"))
            .andExpect(jsonPath("$.errors[0].message").value("Quantity must be at least 1"));
    }
}
```

### 패턴 2: Query Controller 테스트

```java
package com.company.adapter.in.web.controller;

import com.company.adapter.in.web.dto.*;
import com.company.adapter.in.web.mapper.OrderApiMapper;
import com.company.application.port.in.GetOrderQuery;
import com.company.domain.order.exception.OrderNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderQueryController 단위 테스트
 *
 * @author Development Team
 * @since 1.0.0
 */
@WebMvcTest(OrderQueryController.class)
class OrderQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetOrderQuery getOrderQuery;

    @MockBean
    private OrderApiMapper orderApiMapper;

    @Test
    void getOrder_WithValidOrderId_ShouldReturn200Ok() throws Exception {
        // Given
        Long orderId = 1L;
        GetOrderQuery.Response queryResponse = new GetOrderQuery.Response(
            orderId,
            "PENDING",
            "2000.00",
            LocalDateTime.now()
        );

        OrderApiResponse apiResponse = new OrderApiResponse(
            orderId,
            "PENDING",
            "2000.00",
            LocalDateTime.now()
        );

        given(getOrderQuery.getOrder(any()))
            .willReturn(queryResponse);
        given(orderApiMapper.toApiResponse(queryResponse))
            .willReturn(apiResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalAmount").value("2000.00"));
    }

    @Test
    void getOrder_WithNonExistentOrderId_ShouldReturn404NotFound() throws Exception {
        // Given
        Long orderId = 999L;
        given(getOrderQuery.getOrder(any()))
            .willThrow(new OrderNotFoundException(orderId));

        // When & Then
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER-001"))
            .andExpect(jsonPath("$.message").value("Order not found: 999"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.path").value("/api/v1/orders/999"));
    }

    @Test
    void getOrder_WithInvalidOrderId_ShouldReturn400BadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/orders/{orderId}", "invalid"))
            .andExpect(status().isBadRequest());
    }
}
```

### 패턴 3: Pagination 테스트

```java
@WebMvcTest(OrderQueryController.class)
class OrderQueryControllerPaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetOrdersQuery getOrdersQuery;

    @MockBean
    private OrderApiMapper orderApiMapper;

    @Test
    void getOrders_WithPagination_ShouldReturnPagedResponse() throws Exception {
        // Given
        List<OrderApiResponse> orders = List.of(
            new OrderApiResponse(1L, "PENDING", "1000.00", LocalDateTime.now()),
            new OrderApiResponse(2L, "PENDING", "2000.00", LocalDateTime.now())
        );

        PageResponse<OrderApiResponse> pageResponse = new PageResponse<>(
            orders,
            0,      // page
            10,     // size
            2,      // totalElements
            1,      // totalPages
            true,   // first
            true    // last
        );

        given(getOrdersQuery.getOrders(any()))
            .willReturn(pageResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/orders")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(true));
    }
}
```

---

## 🎯 MockMvc 주요 메서드

### 요청 생성

```java
// GET
mockMvc.perform(get("/api/v1/orders/{id}", 1))

// POST with JSON body
mockMvc.perform(post("/api/v1/orders")
    .contentType(MediaType.APPLICATION_JSON)
    .content("""
        {
          "customerId": 1
        }
        """))

// PUT
mockMvc.perform(put("/api/v1/orders/{id}", 1)
    .contentType(MediaType.APPLICATION_JSON)
    .content("{}"))

// DELETE
mockMvc.perform(delete("/api/v1/orders/{id}", 1))

// Query Parameters
mockMvc.perform(get("/api/v1/orders")
    .param("page", "0")
    .param("size", "10"))

// Headers
mockMvc.perform(get("/api/v1/orders")
    .header("Authorization", "Bearer token"))
```

### 응답 검증

```java
// HTTP 상태 코드
.andExpect(status().isOk())              // 200
.andExpect(status().isCreated())         // 201
.andExpect(status().isNoContent())       // 204
.andExpect(status().isBadRequest())      // 400
.andExpect(status().isNotFound())        // 404
.andExpect(status().isConflict())        // 409

// Header 검증
.andExpect(header().exists("Location"))
.andExpect(header().string("Content-Type", "application/json"))

// JSON Path 검증
.andExpect(jsonPath("$.orderId").value(1))
.andExpect(jsonPath("$.status").value("PENDING"))
.andExpect(jsonPath("$.items").isArray())
.andExpect(jsonPath("$.items.length()").value(2))
.andExpect(jsonPath("$.items[0].productId").value(101))
.andExpect(jsonPath("$.items[?(@.quantity > 0)]").exists())

// Content Type 검증
.andExpect(content().contentType(MediaType.APPLICATION_JSON))

// Response Body 전체 검증
.andExpect(content().json("""
    {
      "orderId": 1,
      "status": "PENDING"
    }
    """))
```

---

## 🔧 테스트 설정

### 테스트 Configuration

```java
package com.company.adapter.in.web.config;

import com.company.adapter.in.web.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Controller 테스트 공통 설정
 *
 * @author Development Team
 * @since 1.0.0
 */
@TestConfiguration
public class ControllerTestConfig {

    /**
     * GlobalExceptionHandler Bean 등록
     *
     * <p>@WebMvcTest는 @RestControllerAdvice를 자동으로 로딩하지 않으므로
     * 명시적으로 Bean 등록이 필요합니다.
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * ObjectMapper 커스터마이징
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .findAndRegisterModules();  // LocalDateTime 등 Java 8 Time 지원
    }
}
```

### 테스트에서 사용

```java
@WebMvcTest(OrderCommandController.class)
@Import(ControllerTestConfig.class)  // ✅ GlobalExceptionHandler 포함
class OrderCommandControllerTest {
    // ...
}
```

---

## 📋 Controller 단위 테스트 체크리스트

### 기본 구조
- [ ] `@WebMvcTest(TargetController.class)` 사용하는가?
- [ ] `@Autowired MockMvc` 주입하는가?
- [ ] `@MockBean` UseCase Port 선언하는가?
- [ ] `@MockBean` Mapper 선언하는가?

### HTTP 요청/응답 검증
- [ ] 정상 요청 시 201/200 상태 코드 반환하는가?
- [ ] 잘못된 요청 시 400 Bad Request 반환하는가?
- [ ] 리소스 없을 때 404 Not Found 반환하는가?
- [ ] Location Header를 포함하는가? (201 Created)

### Bean Validation 검증
- [ ] `@Valid` 검증 실패 시 400 반환하는가?
- [ ] `FieldError` 목록이 포함되는가?
- [ ] 에러 메시지가 명확한가?

### JSON 직렬화/역직렬화
- [ ] Request JSON → DTO 역직렬화 검증하는가?
- [ ] DTO → Response JSON 직렬화 검증하는가?
- [ ] JSON Path로 응답 필드 검증하는가?

### Mapper 검증
- [ ] `orderApiMapper.toCommand()` 호출하는가?
- [ ] `orderApiMapper.toApiResponse()` 호출하는가?

### GlobalExceptionHandler 통합
- [ ] `@Import(ControllerTestConfig.class)` 사용하는가?
- [ ] 예외 발생 시 ErrorResponse 형식 검증하는가?
- [ ] HTTP 상태 코드가 ErrorCode와 일치하는가?

---

## 🚫 Anti-Pattern

### ❌ 비즈니스 로직 테스트

```java
// ❌ Bad: Controller 테스트에서 비즈니스 로직 검증
@Test
void createOrder_ShouldCalculateTotalPrice() {
    // 비즈니스 로직은 Domain/Application Layer에서 테스트
    // Controller는 HTTP 처리만 검증
}
```

### ❌ DB 연동 테스트

```java
// ❌ Bad: Controller 테스트에서 DB 검증
@Test
void createOrder_ShouldSaveToDatabase() {
    // DB 연동은 Integration Test에서 검증
}
```

### ❌ 실제 UseCase 호출

```java
// ❌ Bad: @SpringBootTest로 전체 Context 로딩
@SpringBootTest
class OrderCommandControllerTest {
    @Autowired
    private CreateOrderUseCase createOrderUseCase;  // 실제 Bean

    // 너무 느림, Controller 테스트에 불필요
}
```

---

## 📊 테스트 실행

### Gradle

```bash
# Controller 단위 테스트만 실행
./gradlew test --tests "*ControllerTest"

# 특정 Controller 테스트
./gradlew test --tests "OrderCommandControllerTest"
```

### JUnit 5 Tags (선택적)

```java
@Tag("unit")
@WebMvcTest(OrderCommandController.class)
class OrderCommandControllerTest {
    // ...
}
```

```bash
# Tag로 실행
./gradlew test -Dtest.tags=unit
```

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
