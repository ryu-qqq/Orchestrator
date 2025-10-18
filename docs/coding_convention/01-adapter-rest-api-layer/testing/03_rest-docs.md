# Spring REST Docs - API 문서 자동화

**목적**: 테스트 코드와 함께 API 문서를 자동으로 생성하여 항상 최신 상태의 정확한 문서 유지

**관련 문서**:
- [Controller 단위 테스트](./01_controller-unit-test.md) - @WebMvcTest 테스트
- [API 통합 테스트](./02_integration-test.md) - @SpringBootTest 통합 테스트

**핵심 도구**: Spring REST Docs, AsciiDoc, Gradle AsciiDoctor Plugin

---

## 📌 핵심 원칙

### Spring REST Docs란?

**Spring REST Docs**는 테스트 코드 실행 중 API 문서를 자동 생성하는 도구입니다.

**핵심 개념**:
- ✅ **테스트 주도 문서화**: 테스트가 성공해야 문서 생성 (항상 정확한 문서 보장)
- ✅ **자동 업데이트**: 코드 변경 시 테스트와 문서가 함께 업데이트
- ✅ **프로덕션 코드 무침투**: 문서화를 위한 Annotation 불필요 (Swagger와 차이점)

### Swagger vs Spring REST Docs

| 비교 항목 | Swagger (Springdoc OpenAPI) | Spring REST Docs |
|----------|---------------------------|------------------|
| **문서 생성 방식** | 프로덕션 코드에 `@Operation` Annotation | 테스트 코드로 문서 생성 |
| **정확성 보장** | Runtime 문서 (불일치 가능) | 테스트 성공 시에만 문서 생성 (정확성 보장) |
| **코드 침투성** | Controller에 Annotation 추가 필요 | 프로덕션 코드 무침투 |
| **문서 형식** | JSON (OpenAPI 3.0) | AsciiDoc, Markdown, HTML |
| **장점** | 즉시 테스트 가능한 UI (Swagger UI) | 정확성, 깔끔한 프로덕션 코드 |
| **단점** | Annotation 증가, 정확성 미보장 | 초기 설정 복잡 |

**권장 사용 시나리오**:
- **Swagger**: 빠른 프로토타이핑, 외부 API 공개, 개발자 테스트 UI 필요
- **Spring REST Docs**: 정확한 문서 필수, 프로덕션 코드 깔끔하게 유지, Enterprise 환경

---

## 🏗️ 설정

### 1. Gradle 설정

**build.gradle**:
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.0'
    id 'io.spring.dependency-management' version '1.1.0'
    id 'org.asciidoctor.jvm.convert' version '3.3.2'  // AsciiDoc 변환 플러그인
}

configurations {
    asciidoctorExt
}

dependencies {
    // Spring REST Docs
    testImplementation 'org.springframework.restdocs:spring-restdocs-mockmvc'

    // AsciiDoctor 확장 (선택적)
    asciidoctorExt 'org.springframework.restdocs:spring-restdocs-asciidoctor'
}

ext {
    snippetsDir = file('build/generated-snippets')  // 스니펫 생성 디렉토리
}

test {
    outputs.dir snippetsDir  // 테스트 실행 시 스니펫 디렉토리 생성
    useJUnitPlatform()
}

asciidoctor {
    inputs.dir snippetsDir  // 스니펫 디렉토리에서 읽기
    configurations 'asciidoctorExt'
    dependsOn test  // 테스트 실행 후 문서 생성
}

bootJar {
    dependsOn asciidoctor  // Jar 생성 전 문서 생성
    from ("${asciidoctor.outputDir}/html5") {  // 생성된 HTML 문서 포함
        into 'static/docs'
    }
}
```

**핵심 설정**:
- `snippetsDir`: 테스트 실행 중 생성된 스니펫 저장 위치
- `asciidoctor.dependsOn test`: 테스트 → 문서 생성 순서 보장
- `bootJar.from asciidoctor.outputDir`: 생성된 문서를 Jar에 포함 (`/static/docs`에서 접근 가능)

---

### 2. 테스트 설정 클래스

**RestDocsTestConfig.java**:
```java
package com.company.application.config;

import org.springframework.boot.test.autoconfigure.restdocs.RestDocsMockMvcConfigurationCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor;

import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;

/**
 * Spring REST Docs 테스트 설정
 *
 * @author development-team
 * @since 1.0.0
 */
@TestConfiguration
public class RestDocsTestConfig {

    @Bean
    public RestDocsMockMvcConfigurationCustomizer restDocsMockMvcConfigurationCustomizer() {
        return configurer -> configurer.operationPreprocessors()
            .withRequestDefaults(prettyPrint())   // 요청 JSON 포맷팅
            .withResponseDefaults(prettyPrint()); // 응답 JSON 포맷팅
    }

    /**
     * 요청 전처리: 프로토콜, 호스트, 포트 제거 (가독성 향상)
     */
    public static OperationRequestPreprocessor preprocessRequest() {
        return preprocessRequest(
            modifyUris()
                .scheme("https")
                .host("api.example.com")
                .removePort(),
            prettyPrint()
        );
    }

    /**
     * 응답 전처리: JSON 포맷팅
     */
    public static OperationResponsePreprocessor preprocessResponse() {
        return preprocessResponse(prettyPrint());
    }
}
```

---

### 3. 테스트 Base 클래스

**RestDocsTestSupport.java**:
```java
package com.company.application.support;

import com.company.application.config.RestDocsTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * REST Docs 테스트 Base 클래스
 *
 * @author development-team
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs  // REST Docs 자동 설정
@Import(RestDocsTestConfig.class)
@ExtendWith(RestDocumentationExtension.class)
public abstract class RestDocsTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUp(WebApplicationContext webApplicationContext,
               RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(MockMvcRestDocumentation.documentationConfiguration(restDocumentation))
            .build();
    }
}
```

---

## ✅ 기본 패턴

### 패턴 1: Request/Response 기본 문서화

```java
package com.company.application.in.web;

import com.company.application.support.RestDocsTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static com.company.application.config.RestDocsTestConfig.preprocessRequest;
import static com.company.application.config.RestDocsTestConfig.preprocessResponse;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order API 문서화 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
@Tag("restdocs")
class OrderApiDocumentationTest extends RestDocsTestSupport {

    @Test
    void createOrder() throws Exception {
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

        // When & Then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andDo(document("orders/create",  // 문서 스니펫 이름
                preprocessRequest(),          // 요청 전처리
                preprocessResponse()          // 응답 전처리
            ));
    }
}
```

**생성된 스니펫** (`build/generated-snippets/orders/create/`):
- `http-request.adoc`: HTTP 요청 예시
- `http-response.adoc`: HTTP 응답 예시
- `curl-request.adoc`: cURL 명령어
- `httpie-request.adoc`: HTTPie 명령어

---

### 패턴 2: Path Parameters 문서화

```java
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;

@Test
void getOrder() throws Exception {
    // Given - Order 생성
    Long orderId = 1L;

    // When & Then
    mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
        .andExpect(status().isOk())
        .andDo(document("orders/get",
            preprocessRequest(),
            preprocessResponse(),
            pathParameters(  // Path Parameter 문서화
                parameterWithName("orderId").description("조회할 주문 ID")
            )
        ));
}
```

---

### 패턴 3: Query Parameters 문서화

```java
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;

@Test
void listOrders() throws Exception {
    mockMvc.perform(get("/api/v1/orders")
            .param("page", "0")
            .param("size", "10")
            .param("status", "PENDING"))
        .andExpect(status().isOk())
        .andDo(document("orders/list",
            preprocessRequest(),
            preprocessResponse(),
            queryParameters(  // Query Parameter 문서화
                parameterWithName("page").description("페이지 번호 (0부터 시작)").optional(),
                parameterWithName("size").description("페이지 크기 (기본값: 20)").optional(),
                parameterWithName("status").description("주문 상태 필터 (PENDING, APPROVED, CANCELLED)").optional()
            )
        ));
}
```

---

### 패턴 4: Request/Response Fields 문서화

```java
import static org.springframework.restdocs.payload.PayloadDocumentation.*;

@Test
void createOrderWithFieldDocs() throws Exception {
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

    // When & Then
    mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andDo(document("orders/create-with-fields",
            preprocessRequest(),
            preprocessResponse(),
            requestFields(  // Request Body 필드 문서화
                fieldWithPath("customerId").description("고객 ID"),
                fieldWithPath("items").description("주문 항목 리스트"),
                fieldWithPath("items[].productId").description("상품 ID"),
                fieldWithPath("items[].quantity").description("주문 수량"),
                fieldWithPath("notes").description("주문 메모").optional()
            ),
            responseFields(  // Response Body 필드 문서화
                fieldWithPath("orderId").description("생성된 주문 ID"),
                fieldWithPath("status").description("주문 상태 (PENDING, APPROVED, CANCELLED)"),
                fieldWithPath("customerId").description("고객 ID"),
                fieldWithPath("totalAmount").description("총 주문 금액"),
                fieldWithPath("createdAt").description("주문 생성 시각 (ISO-8601)")
            )
        ));
}
```

---

### 패턴 5: Headers 문서화

```java
import static org.springframework.restdocs.headers.HeaderDocumentation.*;

@Test
void createOrderWithHeaders() throws Exception {
    // Given
    String requestJson = """
        {
          "customerId": 1,
          "items": [{"productId": 101, "quantity": 2}]
        }
        """;

    // When & Then
    mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer {access-token}")  // 인증 헤더
            .content(requestJson))
        .andExpect(status().isCreated())
        .andDo(document("orders/create-with-headers",
            preprocessRequest(),
            preprocessResponse(),
            requestHeaders(  // Request Header 문서화
                headerWithName("Authorization").description("Bearer 토큰 (JWT)")
            ),
            responseHeaders(  // Response Header 문서화
                headerWithName("Location").description("생성된 리소스 URI")
            )
        ));
}
```

---

## 🎯 실전 예제

### Example 1: CRUD API 완전 문서화

```java
/**
 * Order CRUD API 문서화
 *
 * @author development-team
 * @since 1.0.0
 */
@Tag("restdocs")
class OrderCrudApiDocumentationTest extends RestDocsTestSupport {

    @Autowired
    private OrderJpaRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder() throws Exception {
        String requestJson = """
            {
              "customerId": 1,
              "items": [
                {
                  "productId": 101,
                  "quantity": 2
                }
              ],
              "notes": "신규 주문"
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andDo(document("orders/create",
                preprocessRequest(),
                preprocessResponse(),
                requestFields(
                    fieldWithPath("customerId").type(JsonFieldType.NUMBER)
                        .description("고객 ID (필수)"),
                    fieldWithPath("items").type(JsonFieldType.ARRAY)
                        .description("주문 항목 리스트 (최소 1개)"),
                    fieldWithPath("items[].productId").type(JsonFieldType.NUMBER)
                        .description("상품 ID"),
                    fieldWithPath("items[].quantity").type(JsonFieldType.NUMBER)
                        .description("주문 수량 (최소 1)"),
                    fieldWithPath("notes").type(JsonFieldType.STRING)
                        .description("주문 메모 (선택)").optional()
                ),
                responseFields(
                    fieldWithPath("orderId").type(JsonFieldType.NUMBER)
                        .description("생성된 주문 ID"),
                    fieldWithPath("status").type(JsonFieldType.STRING)
                        .description("주문 상태 (PENDING, APPROVED, CANCELLED)"),
                    fieldWithPath("customerId").type(JsonFieldType.NUMBER)
                        .description("고객 ID"),
                    fieldWithPath("totalAmount").type(JsonFieldType.STRING)
                        .description("총 주문 금액 (BigDecimal String)"),
                    fieldWithPath("createdAt").type(JsonFieldType.STRING)
                        .description("주문 생성 시각 (ISO-8601 형식)")
                ),
                responseHeaders(
                    headerWithName("Location")
                        .description("생성된 주문의 리소스 URI (/api/v1/orders/{orderId})")
                )
            ));
    }

    @Test
    void getOrder() throws Exception {
        // Given - Order 생성
        Long orderId = createOrderInDb();

        // When & Then
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andDo(document("orders/get",
                preprocessRequest(),
                preprocessResponse(),
                pathParameters(
                    parameterWithName("orderId")
                        .description("조회할 주문 ID")
                ),
                responseFields(
                    fieldWithPath("orderId").description("주문 ID"),
                    fieldWithPath("status").description("주문 상태"),
                    fieldWithPath("customerId").description("고객 ID"),
                    fieldWithPath("items").description("주문 항목 리스트"),
                    fieldWithPath("items[].productId").description("상품 ID"),
                    fieldWithPath("items[].quantity").description("주문 수량"),
                    fieldWithPath("items[].price").description("상품 단가"),
                    fieldWithPath("totalAmount").description("총 주문 금액"),
                    fieldWithPath("notes").description("주문 메모").optional(),
                    fieldWithPath("createdAt").description("주문 생성 시각"),
                    fieldWithPath("updatedAt").description("주문 수정 시각")
                )
            ));
    }

    @Test
    void updateOrder() throws Exception {
        // Given - Order 생성
        Long orderId = createOrderInDb();

        // When & Then
        mockMvc.perform(patch("/api/v1/orders/{orderId}/approve", orderId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andDo(document("orders/approve",
                preprocessRequest(),
                preprocessResponse(),
                pathParameters(
                    parameterWithName("orderId").description("승인할 주문 ID")
                ),
                responseFields(
                    fieldWithPath("orderId").description("주문 ID"),
                    fieldWithPath("status").description("변경된 주문 상태 (APPROVED)"),
                    fieldWithPath("customerId").description("고객 ID"),
                    fieldWithPath("totalAmount").description("총 주문 금액"),
                    fieldWithPath("updatedAt").description("수정 시각")
                )
            ));
    }

    @Test
    void deleteOrder() throws Exception {
        // Given - Order 생성
        Long orderId = createOrderInDb();

        // When & Then
        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId))
            .andExpect(status().isNoContent())
            .andDo(document("orders/delete",
                preprocessRequest(),
                preprocessResponse(),
                pathParameters(
                    parameterWithName("orderId").description("삭제할 주문 ID")
                )
            ));
    }

    private Long createOrderInDb() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": 1,
                      "items": [{"productId": 101, "quantity": 2}]
                    }
                    """))
            .andReturn();

        String location = result.getResponse().getHeader("Location");
        String[] parts = location.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
```

---

### Example 2: Pagination API 문서화

```java
import static org.springframework.restdocs.snippet.Attributes.key;

@Test
void listOrdersWithPagination() throws Exception {
    // Given - 30개 Order 생성
    for (int i = 1; i <= 30; i++) {
        createOrderInDb();
    }

    // When & Then
    mockMvc.perform(get("/api/v1/orders")
            .param("page", "0")
            .param("size", "10")
            .param("status", "PENDING"))
        .andExpect(status().isOk())
        .andDo(document("orders/list-pagination",
            preprocessRequest(),
            preprocessResponse(),
            queryParameters(
                parameterWithName("page")
                    .description("페이지 번호 (0부터 시작)")
                    .optional()
                    .attributes(key("default").value("0")),
                parameterWithName("size")
                    .description("페이지 크기")
                    .optional()
                    .attributes(key("default").value("20"), key("max").value("100")),
                parameterWithName("status")
                    .description("주문 상태 필터 (PENDING, APPROVED, CANCELLED)")
                    .optional()
            ),
            responseFields(
                fieldWithPath("content").description("주문 리스트"),
                fieldWithPath("content[].orderId").description("주문 ID"),
                fieldWithPath("content[].status").description("주문 상태"),
                fieldWithPath("content[].customerId").description("고객 ID"),
                fieldWithPath("content[].totalAmount").description("총 주문 금액"),
                fieldWithPath("content[].createdAt").description("주문 생성 시각"),

                // Pagination 정보
                fieldWithPath("pageable").description("페이징 정보"),
                fieldWithPath("pageable.pageNumber").description("현재 페이지 번호"),
                fieldWithPath("pageable.pageSize").description("페이지 크기"),
                fieldWithPath("pageable.sort").description("정렬 정보"),
                fieldWithPath("pageable.sort.sorted").description("정렬 여부"),
                fieldWithPath("pageable.sort.unsorted").description("비정렬 여부"),
                fieldWithPath("pageable.sort.empty").description("정렬 조건 없음 여부"),
                fieldWithPath("pageable.offset").description("오프셋"),
                fieldWithPath("pageable.paged").description("페이징 여부"),
                fieldWithPath("pageable.unpaged").description("비페이징 여부"),

                fieldWithPath("totalElements").description("전체 항목 수"),
                fieldWithPath("totalPages").description("전체 페이지 수"),
                fieldWithPath("number").description("현재 페이지 번호 (0부터 시작)"),
                fieldWithPath("size").description("페이지 크기"),
                fieldWithPath("numberOfElements").description("현재 페이지 항목 수"),
                fieldWithPath("first").description("첫 페이지 여부"),
                fieldWithPath("last").description("마지막 페이지 여부"),
                fieldWithPath("empty").description("빈 페이지 여부"),

                fieldWithPath("sort").description("정렬 정보"),
                fieldWithPath("sort.sorted").description("정렬 여부"),
                fieldWithPath("sort.unsorted").description("비정렬 여부"),
                fieldWithPath("sort.empty").description("정렬 조건 없음 여부")
            )
        ));
}
```

---

### Example 3: Error Response 문서화

```java
@Test
void getOrderNotFound() throws Exception {
    // Given - 존재하지 않는 orderId
    Long nonExistingOrderId = 99999L;

    // When & Then
    mockMvc.perform(get("/api/v1/orders/{orderId}", nonExistingOrderId))
        .andExpect(status().isNotFound())
        .andDo(document("errors/order-not-found",
            preprocessRequest(),
            preprocessResponse(),
            pathParameters(
                parameterWithName("orderId").description("조회할 주문 ID")
            ),
            responseFields(
                fieldWithPath("timestamp").description("에러 발생 시각 (ISO-8601)"),
                fieldWithPath("code").description("에러 코드 (ORDER_NOT_FOUND)"),
                fieldWithPath("message").description("에러 메시지"),
                fieldWithPath("path").description("요청 URI")
            )
        ));
}

@Test
void createOrderValidationError() throws Exception {
    // Given - customerId null
    String invalidRequestJson = """
        {
          "customerId": null,
          "items": []
        }
        """;

    // When & Then
    mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidRequestJson))
        .andExpect(status().isBadRequest())
        .andDo(document("errors/validation-failed",
            preprocessRequest(),
            preprocessResponse(),
            requestFields(
                fieldWithPath("customerId").description("고객 ID (null 시 에러)"),
                fieldWithPath("items").description("주문 항목 리스트 (빈 배열 시 에러)")
            ),
            responseFields(
                fieldWithPath("timestamp").description("에러 발생 시각"),
                fieldWithPath("code").description("에러 코드 (VALIDATION_FAILED)"),
                fieldWithPath("message").description("에러 메시지"),
                fieldWithPath("path").description("요청 URI"),
                fieldWithPath("errors").description("필드별 에러 리스트"),
                fieldWithPath("errors[].field").description("에러 발생 필드명"),
                fieldWithPath("errors[].message").description("필드 에러 메시지"),
                fieldWithPath("errors[].rejectedValue").description("거부된 값").optional()
            )
        ));
}
```

---

## 🔧 고급 패턴

### 패턴 1: Custom Snippets (재사용 가능한 문서 조각)

**CustomSnippets.java**:
```java
package com.company.application.support;

import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.request.ParameterDescriptor;

import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.snippet.Attributes.key;

/**
 * 재사용 가능한 REST Docs Snippets
 *
 * @author development-team
 * @since 1.0.0
 */
public class CustomSnippets {

    /**
     * 공통 Pagination Query Parameters
     */
    public static ParameterDescriptor[] paginationParameters() {
        return new ParameterDescriptor[]{
            parameterWithName("page")
                .description("페이지 번호 (0부터 시작)")
                .optional()
                .attributes(key("default").value("0")),
            parameterWithName("size")
                .description("페이지 크기")
                .optional()
                .attributes(key("default").value("20"), key("max").value("100"))
        };
    }

    /**
     * 공통 Pagination Response Fields
     */
    public static FieldDescriptor[] paginationResponseFields(String contentPath) {
        return new FieldDescriptor[]{
            fieldWithPath(contentPath).description("페이지 내용"),
            fieldWithPath("totalElements").description("전체 항목 수"),
            fieldWithPath("totalPages").description("전체 페이지 수"),
            fieldWithPath("number").description("현재 페이지 번호"),
            fieldWithPath("size").description("페이지 크기"),
            fieldWithPath("first").description("첫 페이지 여부"),
            fieldWithPath("last").description("마지막 페이지 여부"),
            fieldWithPath("empty").description("빈 페이지 여부")
        };
    }

    /**
     * 공통 Error Response Fields
     */
    public static FieldDescriptor[] errorResponseFields() {
        return new FieldDescriptor[]{
            fieldWithPath("timestamp").description("에러 발생 시각 (ISO-8601)"),
            fieldWithPath("code").description("에러 코드"),
            fieldWithPath("message").description("에러 메시지"),
            fieldWithPath("path").description("요청 URI")
        };
    }

    /**
     * Validation Error Response Fields
     */
    public static FieldDescriptor[] validationErrorResponseFields() {
        return new FieldDescriptor[]{
            fieldWithPath("timestamp").description("에러 발생 시각"),
            fieldWithPath("code").description("에러 코드 (VALIDATION_FAILED)"),
            fieldWithPath("message").description("에러 메시지"),
            fieldWithPath("path").description("요청 URI"),
            fieldWithPath("errors").description("필드별 에러 리스트"),
            fieldWithPath("errors[].field").description("에러 발생 필드명"),
            fieldWithPath("errors[].message").description("필드 에러 메시지"),
            fieldWithPath("errors[].rejectedValue").description("거부된 값").optional()
        };
    }
}
```

**사용 예시**:
```java
import static com.company.application.support.CustomSnippets.*;

@Test
void listOrdersWithCustomSnippets() throws Exception {
    mockMvc.perform(get("/api/v1/orders")
            .param("page", "0")
            .param("size", "10"))
        .andExpect(status().isOk())
        .andDo(document("orders/list-with-custom-snippets",
            preprocessRequest(),
            preprocessResponse(),
            queryParameters(paginationParameters()),  // 재사용
            responseFields(
                paginationResponseFields("content"),  // 재사용
                fieldWithPath("content[].orderId").description("주문 ID"),
                fieldWithPath("content[].status").description("주문 상태")
                // ... 기타 필드
            )
        ));
}
```

---

### 패턴 2: AsciiDoc 문서 조합

**src/docs/asciidoc/api-guide.adoc**:
```asciidoc
= Order Management API Guide
:doctype: book
:icons: font
:source-highlighter: highlightjs
:toc: left
:toclevels: 2
:sectlinks:

[[overview]]
= Overview

Order Management API는 주문 생성, 조회, 수정, 삭제 기능을 제공합니다.

[[overview-http-verbs]]
== HTTP Verbs

RESTful 원칙에 따라 다음과 같은 HTTP 메서드를 사용합니다:

|===
| Verb | Usage

| `GET`
| 리소스 조회

| `POST`
| 새 리소스 생성

| `PATCH`
| 기존 리소스 부분 수정

| `DELETE`
| 리소스 삭제
|===

[[overview-http-status-codes]]
== HTTP Status Codes

|===
| Status Code | Usage

| `200 OK`
| 요청 성공

| `201 Created`
| 새 리소스 생성 성공

| `204 No Content`
| 요청 성공, 응답 본문 없음

| `400 Bad Request`
| 잘못된 요청 (Validation 실패)

| `404 Not Found`
| 리소스 없음

| `409 Conflict`
| 비즈니스 규칙 위반
|===

[[resources]]
= Resources

[[resources-orders]]
== Orders

주문 리소스는 고객의 주문을 관리합니다.

[[resources-orders-create]]
=== 주문 생성

`POST` 요청으로 새 주문을 생성합니다.

operation::orders/create[snippets='http-request,request-fields,http-response,response-fields,response-headers']

[[resources-orders-get]]
=== 주문 조회

`GET` 요청으로 특정 주문을 조회합니다.

operation::orders/get[snippets='http-request,path-parameters,http-response,response-fields']

[[resources-orders-list]]
=== 주문 목록 조회

`GET` 요청으로 주문 목록을 페이징하여 조회합니다.

operation::orders/list-pagination[snippets='http-request,query-parameters,http-response,response-fields']

[[resources-orders-approve]]
=== 주문 승인

`PATCH` 요청으로 주문을 승인합니다.

operation::orders/approve[snippets='http-request,path-parameters,http-response,response-fields']

[[resources-orders-delete]]
=== 주문 삭제

`DELETE` 요청으로 주문을 삭제합니다.

operation::orders/delete[snippets='http-request,path-parameters,http-response']

[[errors]]
= Errors

[[errors-order-not-found]]
== Order Not Found

존재하지 않는 주문 ID로 조회 시 404 에러를 반환합니다.

operation::errors/order-not-found[snippets='http-request,http-response,response-fields']

[[errors-validation-failed]]
== Validation Failed

요청 Validation 실패 시 400 에러를 반환합니다.

operation::errors/validation-failed[snippets='http-request,http-response,response-fields']
```

**문서 빌드**:
```bash
./gradlew clean asciidoctor
```

**생성된 HTML**:
- `build/docs/asciidoc/api-guide.html`

**접근 URL** (bootJar 실행 후):
- `http://localhost:8080/docs/api-guide.html`

---

### 패턴 3: 문서화 자동화 (테스트 실패 시 문서 미생성)

**build.gradle**:
```gradle
test {
    outputs.dir snippetsDir
    useJUnitPlatform {
        includeTags 'restdocs'  // REST Docs 테스트만 실행
    }

    // 테스트 실패 시 빌드 중단
    failFast = true
}

asciidoctor {
    inputs.dir snippetsDir
    configurations 'asciidoctorExt'
    dependsOn test  // 테스트 성공 시에만 문서 생성

    // 문서 생성 실패 시 빌드 중단
    failFast = true
}
```

**효과**:
- ✅ 테스트 실패 → 문서 생성 안 됨 (정확성 보장)
- ✅ 문서 생성 실패 → 빌드 중단 (CI/CD 안전성)

---

## 📋 REST Docs 체크리스트

### 설정
- [ ] Gradle AsciiDoctor 플러그인 추가
- [ ] `spring-restdocs-mockmvc` 의존성 추가
- [ ] `snippetsDir` 설정
- [ ] RestDocsTestConfig 생성
- [ ] RestDocsTestSupport Base 클래스 생성

### 테스트 작성
- [ ] `@AutoConfigureRestDocs` 추가
- [ ] `document()` 호출로 스니펫 생성
- [ ] Request/Response 필드 문서화
- [ ] Path/Query Parameters 문서화
- [ ] Headers 문서화
- [ ] Error 케이스 문서화

### AsciiDoc 작성
- [ ] `src/docs/asciidoc/api-guide.adoc` 생성
- [ ] Overview 섹션 작성
- [ ] HTTP Verbs, Status Codes 정의
- [ ] `operation::` 매크로로 스니펫 조합
- [ ] 빌드 및 HTML 생성 확인

### 배포
- [ ] `bootJar`에 HTML 포함 설정
- [ ] `/docs/api-guide.html` 접근 확인
- [ ] CI/CD 파이프라인에 문서 빌드 추가

---

## 🚫 REST Docs에서 피해야 할 것

### ❌ 금지 사항
- 테스트 없이 수동으로 문서 작성 (정확성 미보장)
- 프로덕션 코드에 문서화 Annotation 추가 (Swagger 스타일)
- 필드 문서화 누락 (미문서화 필드는 에러 발생)
- 테스트 실패 시 문서 강제 생성

### ⚠️ 주의 사항
- 모든 필드 문서화 필수 (`.optional()` 명시 필요)
- Request/Response 스펙 변경 시 테스트도 수정
- AsciiDoc 문법 오류 주의
- 스니펫 이름 중복 방지

---

## 📚 참고 자료

- [Spring REST Docs 공식 문서](https://docs.spring.io/spring-restdocs/docs/current/reference/htmlsingle/)
- [AsciiDoc Syntax](https://docs.asciidoctor.org/asciidoc/latest/syntax-quick-reference/)
- [Gradle AsciiDoctor Plugin](https://asciidoctor.org/docs/asciidoctor-gradle-plugin/)

---

**이전 문서**: [API 통합 테스트](./02_integration-test.md)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
