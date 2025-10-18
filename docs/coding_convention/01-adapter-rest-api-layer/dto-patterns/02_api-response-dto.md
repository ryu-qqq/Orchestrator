# API Response DTO - API 응답 DTO 설계 패턴

> **목적**: REST API Adapter Layer의 Response DTO 설계 원칙 및 구현 패턴
>
> **위치**: `adapter/in/web/dto/`
>
> **관련 문서**:
> - `controller-design/03_response-handling.md` (응답 처리 전략)
> - `01_api-request-dto.md` (Request DTO)
> - `03-application-layer/dto-patterns/01_request-response-dto.md` (전체 흐름)
> - `06-java21-patterns/record-patterns/01_dto-with-records.md` (Record 패턴)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. API Response DTO의 역할

**Controller 계층에서 HTTP 응답을 표현하는 불변 객체**

```
Domain → Assembler → UseCase Response → Mapper → API Response DTO → HTTP Response
```

**핵심 특성**:
- **불변성**: Java Record로 Thread-Safe 보장
- **명명 규칙**: `~ApiResponse` suffix 필수
- **직렬화**: Jackson 자동 JSON 변환
- **독립성**: Entity 구조 노출 금지

### 2. API Response DTO vs UseCase Response

| 구분 | API Response DTO | UseCase Response |
|------|------------------|------------------|
| **위치** | `adapter/in/web/dto/` | `application/port/in/` |
| **역할** | HTTP 응답 표현 | 비즈니스 작업 결과 표현 |
| **네이밍** | `~ApiResponse` | `~UseCase.Response` (Inner DTO) |
| **변환** | Mapper가 변환 | Assembler가 Domain에서 변환 |
| **직렬화** | Jackson 최적화 | Jackson 불필요 |

### 3. 네이밍 규칙: `Api` Prefix 필수

```java
// ✅ Good: Api Prefix
public record OrderApiResponse(...)
public record OrderDetailApiResponse(...)
public record OrderSummaryApiResponse(...)

// ❌ Bad: Api Prefix 없음
public record OrderResponse(...)      // ❌ UseCase Response와 혼동
public record OrderDto(...)           // ❌ 모호한 네이밍
```

**이유**:
- UseCase Response와 명확한 구분
- Adapter Layer 소속 표시
- HTTP 응답 전용 DTO 식별

---

## ❌ Anti-Pattern: 잘못된 Response DTO 설계

### 문제 1: Entity 직접 노출

```java
// ❌ Bad: Entity를 Response로 직접 반환
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        return ResponseEntity.ok(order);  // ❌ Entity 직접 노출
    }
}
```

**문제점**:
- 🔴 Entity 내부 구조가 API에 노출됨
- 🔴 Lazy Loading으로 인한 N+1 문제 및 JSON 직렬화 에러
- 🔴 순환 참조 위험 (Jackson `StackOverflowError`)
- 🔴 API 변경 시 Entity 변경 필요 (강결합)

**실제 에러 시나리오**:
```json
// ❌ Lazy Loading Exception
{
  "timestamp": "2025-10-17T10:30:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "could not initialize proxy - no Session"
}

// ❌ 순환 참조
{
  "id": 123,
  "customer": {
    "id": 456,
    "orders": [
      { "id": 123, "customer": { ... } }  // 무한 순환
    ]
  }
}
```

### 문제 2: UseCase Response 직접 반환

```java
// ❌ Bad: UseCase Response를 API Response로 직접 사용
@GetMapping("/{orderId}")
public ResponseEntity<CreateOrderUseCase.Response> getOrder(@PathVariable Long orderId) {
    CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);
    return ResponseEntity.ok(response);  // ❌ UseCase DTO 직접 노출
}
```

**문제점**:
- 🔴 Application Layer 내부 구조 노출
- 🔴 API 독립성 상실 (UseCase 변경 시 API 영향)
- 🔴 Adapter-Application 강결합

### 문제 3: Api Prefix 없는 네이밍

```java
// ❌ Bad: 모호한 네이밍
public record OrderResponse(...)      // UseCase Response와 혼동
public record OrderDto(...)           // 어느 계층의 DTO인지 불명확
```

### 문제 4: Mutable DTO

```java
// ❌ Bad: Mutable Class
public class OrderResponse {
    private Long orderId;
    private String status;

    // Setter로 가변
    public void setStatus(String status) {
        this.status = status;  // ❌ Thread-unsafe
    }
}
```

---

## ✅ Best Practice: Java Record + from() 메서드

### 패턴 1: 기본 Response DTO

```java
package com.company.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * Order API Response DTO
 *
 * <p>주문 생성 API의 응답을 표현합니다.
 *
 * <p>변환 흐름:
 * <pre>
 * Domain (Order) → Assembler → UseCase.Response → Mapper → OrderApiResponse
 * </pre>
 *
 * @param orderId 주문 ID
 * @param status 주문 상태 (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED)
 * @param totalAmount 총 금액
 * @param createdAt 생성 일시
 *
 * @author Development Team
 * @since 1.0.0
 */
public record OrderApiResponse(
    Long orderId,

    String status,

    String totalAmount,  // ✅ BigDecimal → String (JSON 정밀도 문제 방지)

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    LocalDateTime createdAt
) {}
```

**핵심 요소**:
- ✅ **Java Record**: 불변성 보장
- ✅ **Api Suffix**: `OrderApiResponse` 네이밍
- ✅ **Jackson 최적화**: `@JsonFormat`으로 날짜 형식 지정
- ✅ **Primitive 타입 변환**: `BigDecimal` → `String` (JSON 정밀도 유지)
- ✅ **Javadoc**: 각 필드 설명 및 변환 흐름

---

### 패턴 2: Mapper를 통한 변환 (Adapter Layer)

```java
package com.company.adapter.in.web.mapper;

import com.company.adapter.in.web.dto.OrderApiResponse;
import com.company.application.port.in.CreateOrderUseCase;
import org.springframework.stereotype.Component;

/**
 * Order API Mapper
 *
 * <p>UseCase Response를 API Response DTO로 변환합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
@Component
public class OrderApiMapper {

    /**
     * UseCase Response → API Response 변환
     *
     * @param response UseCase 실행 결과
     * @return API Response DTO
     */
    public OrderApiResponse toApiResponse(CreateOrderUseCase.Response response) {
        return new OrderApiResponse(
            response.orderId(),
            response.status(),
            response.totalAmount().toString(),  // ✅ BigDecimal → String 변환
            response.createdAt()
        );
    }
}
```

**Controller 사용**:
```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            OrderApiMapper orderApiMapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderApiMapper = orderApiMapper;
    }

    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        // 1. API Request → UseCase Command 변환
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // 2. UseCase 실행
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // 3. UseCase Response → API Response 변환 ✅
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", "/api/v1/orders/" + apiResponse.orderId())
            .body(apiResponse);
    }
}
```

---

## ✅ 상세 정보 Response: 중첩 DTO 패턴

### 패턴: 상세 조회용 Response (Nested DTO)

```java
package com.company.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Detail API Response DTO
 *
 * <p>주문 상세 조회 API의 응답을 표현합니다.
 *
 * <p>특징:
 * <ul>
 *   <li>고객 정보 포함</li>
 *   <li>주문 항목 상세 정보 포함</li>
 *   <li>중첩 DTO 구조 (OrderItemApiResponse)</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 */
public record OrderDetailApiResponse(
    Long orderId,

    String status,

    String totalAmount,

    CustomerInfo customer,  // ✅ 중첩 DTO

    List<OrderItemApiResponse> items,  // ✅ 중첩 DTO List

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    LocalDateTime createdAt
) {

    /**
     * 고객 정보 (중첩 DTO)
     */
    public record CustomerInfo(
        Long customerId,
        String customerName,
        String email
    ) {}

    /**
     * 주문 항목 (중첩 DTO)
     */
    public record OrderItemApiResponse(
        Long productId,
        String productName,
        Integer quantity,
        String unitPrice,
        String subtotal
    ) {}
}
```

**Mapper 구현**:
```java
@Component
public class OrderApiMapper {

    /**
     * UseCase Response → API Detail Response 변환
     */
    public OrderDetailApiResponse toDetailApiResponse(GetOrderQuery.Response response) {

        // ✅ Customer 정보 변환
        OrderDetailApiResponse.CustomerInfo customerInfo =
            new OrderDetailApiResponse.CustomerInfo(
                response.customerId(),
                response.customerName(),
                response.customerEmail()
            );

        // ✅ OrderItem List 변환
        List<OrderDetailApiResponse.OrderItemApiResponse> items = response.items().stream()
            .map(item -> new OrderDetailApiResponse.OrderItemApiResponse(
                item.productId(),
                item.productName(),
                item.quantity(),
                item.unitPrice().toString(),
                item.subtotal().toString()
            ))
            .toList();

        return new OrderDetailApiResponse(
            response.orderId(),
            response.status(),
            response.totalAmount().toString(),
            customerInfo,
            items,
            response.createdAt()
        );
    }
}
```

**응답 예시**:
```json
{
  "orderId": 123,
  "status": "CONFIRMED",
  "totalAmount": "150000",
  "customer": {
    "customerId": 456,
    "customerName": "홍길동",
    "email": "hong@example.com"
  },
  "items": [
    {
      "productId": 789,
      "productName": "Spring Boot 책",
      "quantity": 2,
      "unitPrice": "50000",
      "subtotal": "100000"
    },
    {
      "productId": 790,
      "productName": "Java 21 책",
      "quantity": 1,
      "unitPrice": "50000",
      "subtotal": "50000"
    }
  ],
  "createdAt": "2025-10-17T10:30:00"
}
```

---

## ✅ 페이지네이션 Response 패턴

### 패턴: PageResponse<T> Generic DTO

```java
package com.company.adapter.in.web.dto;

import java.util.List;

/**
 * Page Response DTO (Generic)
 *
 * <p>페이지네이션이 적용된 API 응답을 표현합니다.
 *
 * @param <T> 페이지 콘텐츠 타입 (API Response DTO)
 *
 * @author Development Team
 * @since 1.0.0
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {

    /**
     * Spring Page → PageResponse 변환
     *
     * @param page Spring Data Page 객체
     * @return PageResponse
     */
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }

    /**
     * Spring Page + Mapper → PageResponse 변환
     *
     * @param page Spring Data Page 객체
     * @param mapper UseCase Response → API Response 변환 함수
     * @return PageResponse
     */
    public static <S, T> PageResponse<T> of(
            org.springframework.data.domain.Page<S> page,
            java.util.function.Function<S, T> mapper) {

        List<T> mappedContent = page.getContent()
            .stream()
            .map(mapper)
            .toList();

        return new PageResponse<>(
            mappedContent,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }
}
```

**Controller 사용**:
```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final GetOrdersQuery getOrdersQuery;
    private final OrderApiMapper orderApiMapper;

    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryApiResponse>> getOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        // 1. UseCase 실행 (UseCase Response Page)
        GetOrdersQuery.Response response = getOrdersQuery.getOrders(
            new GetOrdersQuery.Query(pageable)
        );

        // 2. UseCase Response Page → API Response Page 변환
        PageResponse<OrderSummaryApiResponse> pageResponse = PageResponse.of(
            response.orders(),
            orderApiMapper::toSummaryApiResponse  // ✅ Mapper 함수 참조
        );

        return ResponseEntity.ok(pageResponse);
    }
}
```

**응답 예시**:
```json
{
  "content": [
    {
      "orderId": 123,
      "status": "CONFIRMED",
      "totalAmount": "150000",
      "createdAt": "2025-10-17T10:30:00"
    },
    {
      "orderId": 124,
      "status": "PENDING",
      "totalAmount": "200000",
      "createdAt": "2025-10-17T11:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

---

## 🎯 실전 예제: 다양한 Response DTO 패턴

### 시나리오 1: 요약 정보 Response (Summary)

```java
/**
 * Order Summary API Response DTO
 *
 * <p>주문 목록 조회 시 사용하는 요약 정보 DTO입니다.
 *
 * <p>특징:
 * <ul>
 *   <li>최소한의 정보만 포함 (성능 최적화)</li>
 *   <li>상세 정보는 별도 API로 조회</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 */
public record OrderSummaryApiResponse(
    Long orderId,
    String status,
    String totalAmount,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    LocalDateTime createdAt
) {}
```

### 시나리오 2: 생성 결과 Response (Creation Result)

```java
/**
 * Order Created API Response DTO
 *
 * <p>주문 생성 성공 시 반환하는 최소 정보 DTO입니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
public record OrderCreatedApiResponse(
    Long orderId,
    String status,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    LocalDateTime createdAt
) {}
```

**Controller**:
```java
@PostMapping
public ResponseEntity<OrderCreatedApiResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest request) {

    CreateOrderUseCase.Response response = createOrderUseCase.createOrder(
        orderApiMapper.toCommand(request)
    );

    OrderCreatedApiResponse apiResponse = new OrderCreatedApiResponse(
        response.orderId(),
        response.status(),
        response.createdAt()
    );

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .header("Location", "/api/v1/orders/" + apiResponse.orderId())
        .body(apiResponse);
}
```

### 시나리오 3: 빈 응답 (No Content)

```java
/**
 * Order Cancelled (No Response Body)
 */
@DeleteMapping("/{orderId}")
public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
    CancelOrderUseCase.Command command = new CancelOrderUseCase.Command(orderId);
    cancelOrderUseCase.cancelOrder(command);

    // ✅ 204 No Content - Response Body 없음
    return ResponseEntity.noContent().build();
}
```

### 시나리오 4: Batch Operation Response

```java
/**
 * Batch Operation API Response DTO
 *
 * <p>여러 주문을 한 번에 처리한 결과를 표현합니다.
 *
 * @author Development Team
 * @since 1.0.0
 */
public record BatchOperationApiResponse(
    int totalCount,      // 전체 처리 대상 개수
    int successCount,    // 성공 개수
    int failureCount,    // 실패 개수
    List<FailureDetail> failures  // 실패 상세 정보
) {
    /**
     * 실패 상세 정보
     */
    public record FailureDetail(
        Long orderId,
        String errorCode,
        String errorMessage
    ) {}
}
```

**응답 예시**:
```json
{
  "totalCount": 10,
  "successCount": 8,
  "failureCount": 2,
  "failures": [
    {
      "orderId": 123,
      "errorCode": "ORDER-001",
      "errorMessage": "Order already cancelled"
    },
    {
      "orderId": 456,
      "errorCode": "ORDER-002",
      "errorMessage": "Order not found"
    }
  ]
}
```

---

## 🔧 Jackson 직렬화 최적화

### 패턴 1: 날짜/시간 포맷 통일

```java
/**
 * Jackson 날짜 포맷 설정
 */
public record OrderApiResponse(
    Long orderId,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    LocalDateTime createdAt,

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    LocalDate orderDate,

    @JsonFormat(shape = JsonFormat.Shape.STRING)  // ✅ ISO-8601 형식
    Instant timestamp
) {}
```

**응답 예시**:
```json
{
  "orderId": 123,
  "createdAt": "2025-10-17T10:30:00",
  "orderDate": "2025-10-17",
  "timestamp": "2025-10-17T01:30:00Z"
}
```

### 패턴 2: Null 필드 제외

```java
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Null 필드 제외 설정
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // ✅ 클래스 레벨
public record OrderApiResponse(
    Long orderId,
    String status,
    String notes  // null이면 JSON에서 제외
) {}
```

**응답 예시**:
```json
// notes가 null인 경우
{
  "orderId": 123,
  "status": "PENDING"
  // "notes" 필드 제외
}
```

### 패턴 3: 필드명 커스터마이징

```java
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON 필드명 커스터마이징
 */
public record OrderApiResponse(
    @JsonProperty("order_id")  // ✅ snake_case 변환
    Long orderId,

    @JsonProperty("order_status")
    String status
) {}
```

**응답 예시**:
```json
{
  "order_id": 123,
  "order_status": "PENDING"
}
```

### 패턴 4: Enum 직렬화 제어

```java
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum 직렬화 제어
 */
public enum OrderStatus {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    SHIPPED("shipped");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    @JsonValue  // ✅ JSON 직렬화 시 이 값 사용
    public String getValue() {
        return value;
    }
}
```

---

## 📊 BigDecimal vs String 변환 전략

### 문제: JSON Number의 정밀도 손실

```java
// ❌ Bad: BigDecimal을 Number로 직렬화
public record OrderApiResponse(
    BigDecimal totalAmount  // JSON Number로 직렬화 시 정밀도 손실 가능
) {}

// JSON 출력 (문제 발생 가능)
{
  "totalAmount": 150000.123456789012345  // JavaScript에서 정밀도 손실
}
```

### ✅ Solution: String으로 변환

```java
// ✅ Good: BigDecimal → String 변환
public record OrderApiResponse(
    String totalAmount  // ✅ 정밀도 보존
) {}

// JSON 출력
{
  "totalAmount": "150000.123456789012345"  // ✅ 정밀도 완전 보존
}
```

**Mapper 구현**:
```java
@Component
public class OrderApiMapper {

    public OrderApiResponse toApiResponse(CreateOrderUseCase.Response response) {
        return new OrderApiResponse(
            response.orderId(),
            response.totalAmount().toString()  // ✅ BigDecimal → String
        );
    }
}
```

---

## 📋 API Response DTO 설계 체크리스트

### 기본 구조
- [ ] Java Record 사용하는가?
- [ ] `~ApiResponse` suffix가 있는가?
- [ ] 불변성이 보장되는가?
- [ ] Javadoc이 모든 필드에 작성되어 있는가?
- [ ] `@author`, `@since` 태그가 있는가?

### Entity 노출 방지
- [ ] Entity를 직접 반환하지 않는가?
- [ ] UseCase Response를 직접 반환하지 않는가?
- [ ] Mapper를 통해 변환하는가?
- [ ] Domain 내부 구조가 노출되지 않는가?

### Jackson 직렬화
- [ ] `@JsonFormat`으로 날짜 형식을 지정했는가?
- [ ] `@JsonInclude`로 null 필드를 제외하는가?
- [ ] `@JsonProperty`는 정말 필요한 경우만 사용하는가?
- [ ] BigDecimal이 String으로 변환되는가?

### 중첩 DTO
- [ ] 중첩 DTO가 내부 Record로 정의되어 있는가?
- [ ] 중첩 DTO의 네이밍이 명확한가?

### 페이지네이션
- [ ] `PageResponse<T>` Generic DTO를 사용하는가?
- [ ] `of()` 정적 팩토리 메서드가 있는가?

### Mapper 통합
- [ ] OrderApiMapper가 UseCase Response → API Response 변환을 담당하는가?
- [ ] Mapper가 `@Component`로 등록되어 있는가?

### 계층 분리
- [ ] DTO에 비즈니스 로직이 없는가?
- [ ] Domain 객체를 직접 참조하지 않는가?
- [ ] UseCase Response와 명확히 분리되어 있는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
