# RESTful API 설계 원칙

> **목적**: REST 아키텍처 스타일을 따르는 API 설계 원칙 및 HTTP 메서드 활용 가이드
>
> **위치**: `adapter/in/rest-api-*/src/main/java/com/company/adapter/in/rest/[boundedContext]/controller/`
>
> **관련 문서**:
> - `package-guide/01_rest_api_package_guide.md` (전체 구조)
> - `03-application-layer/usecase-design/01_command-usecase.md` (UseCase 연계)
> - `controller-design/02_request-validation.md` (요청 검증)
> - `controller-design/03_response-handling.md` (응답 처리)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. REST (Representational State Transfer)

REST는 리소스(Resource) 중심의 아키텍처 스타일입니다.

#### 핵심 개념
- **리소스**: URI로 식별되는 대상 (예: `/orders`, `/orders/{orderId}`)
- **표현(Representation)**: 리소스의 상태를 JSON, XML 등으로 표현
- **상태 전이**: HTTP 메서드를 통한 리소스 상태 변경
- **무상태(Stateless)**: 각 요청은 독립적, 서버는 클라이언트 상태를 저장하지 않음

#### REST 제약 조건
1. **Client-Server**: 클라이언트와 서버의 관심사 분리
2. **Stateless**: 요청마다 필요한 모든 정보를 포함
3. **Cacheable**: 응답은 캐시 가능 여부를 명시
4. **Uniform Interface**: 일관된 인터페이스 (HTTP 표준 활용)
5. **Layered System**: 계층화된 시스템 구조

---

## ❌ Anti-Pattern: RPC 스타일 API

### 잘못된 설계 (동사 기반 URI)

```java
// ❌ Bad: 동사 기반 URI (RPC 스타일)
@RestController
@RequestMapping("/api/v1")
public class OrderController {

    @PostMapping("/createOrder")  // ❌ 동사 사용
    public OrderApiResponse createOrder(@RequestBody CreateOrderRequest request) {
        // ...
    }

    @PostMapping("/updateOrderStatus")  // ❌ 동사 사용
    public OrderApiResponse updateStatus(@RequestBody UpdateStatusRequest request) {
        // ...
    }

    @PostMapping("/deleteOrder")  // ❌ 동사 사용
    public void deleteOrder(@RequestParam Long orderId) {
        // ...
    }

    @GetMapping("/getOrderList")  // ❌ 동사 사용
    public List<OrderApiResponse> getOrders() {
        // ...
    }
}
```

### 문제점
- ❌ 리소스가 아닌 행위(동사)에 집중
- ❌ HTTP 메서드의 의미를 무시 (모두 POST 또는 GET으로 처리)
- ❌ RESTful 클라이언트 도구와 호환성 저하
- ❌ API 설계 일관성 부족
- ❌ 확장성 및 유지보수성 저하

---

## ✅ 권장 패턴: Resource 기반 RESTful API

### 올바른 설계 (명사 기반 URI + HTTP 메서드)

```java
/**
 * 주문 Command API Controller
 *
 * <p>RESTful API 설계 원칙:
 * <ul>
 *   <li>리소스 기반 URI: {@code /api/v1/orders}</li>
 *   <li>HTTP 메서드 활용: POST (생성), PUT (전체 수정), PATCH (부분 수정), DELETE (삭제)</li>
 *   <li>적절한 상태 코드 반환: 201 Created, 204 No Content 등</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 * @see CreateOrderUseCase
 * @see UpdateOrderUseCase
 */
@RestController
@RequestMapping("/api/v1/orders")  // ✅ 리소스(명사) 기반 URI
public class OrderCommandController {

    private final CreateOrderUseCase createOrderUseCase;
    private final UpdateOrderUseCase updateOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    public OrderCommandController(
            CreateOrderUseCase createOrderUseCase,
            UpdateOrderUseCase updateOrderUseCase,
            CancelOrderUseCase cancelOrderUseCase,
            OrderApiMapper orderApiMapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.updateOrderUseCase = updateOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.orderApiMapper = orderApiMapper;
    }

    /**
     * 주문 생성
     *
     * @param request 주문 생성 요청
     * @return 201 Created + 생성된 주문 정보
     */
    @PostMapping  // ✅ POST = 리소스 생성
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity
            .status(HttpStatus.CREATED)  // ✅ 201 Created
            .header(HttpHeaders.LOCATION, "/api/v1/orders/" + apiResponse.orderId())
            .body(apiResponse);
    }

    /**
     * 주문 상태 변경 (부분 수정)
     *
     * @param orderId 주문 ID
     * @param request 상태 변경 요청
     * @return 200 OK + 수정된 주문 정보
     */
    @PatchMapping("/{orderId}/status")  // ✅ PATCH = 부분 수정
    public ResponseEntity<OrderApiResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        UpdateOrderUseCase.Command command = orderApiMapper.toUpdateCommand(orderId, request);
        UpdateOrderUseCase.Response response = updateOrderUseCase.updateOrderStatus(command);
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity.ok(apiResponse);  // ✅ 200 OK
    }

    /**
     * 주문 취소
     *
     * @param orderId 주문 ID
     * @param request 취소 요청 (취소 사유 포함)
     * @return 204 No Content
     */
    @DeleteMapping("/{orderId}")  // ✅ DELETE = 리소스 삭제
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CancelOrderRequest request) {

        CancelOrderUseCase.Command command = orderApiMapper.toCancelCommand(orderId, request);
        cancelOrderUseCase.cancelOrder(command);

        return ResponseEntity.noContent().build();  // ✅ 204 No Content
    }
}
```

```java
/**
 * 주문 Query API Controller
 *
 * <p>조회 API는 별도 Controller로 분리 (CQRS 패턴)
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderQueryController {

    private final GetOrderQuery getOrderQuery;
    private final SearchOrdersQuery searchOrdersQuery;
    private final OrderApiMapper orderApiMapper;

    public OrderQueryController(
            GetOrderQuery getOrderQuery,
            SearchOrdersQuery searchOrdersQuery,
            OrderApiMapper orderApiMapper) {
        this.getOrderQuery = getOrderQuery;
        this.searchOrdersQuery = searchOrdersQuery;
        this.orderApiMapper = orderApiMapper;
    }

    /**
     * 주문 단건 조회
     *
     * @param orderId 주문 ID
     * @return 200 OK + 주문 상세 정보
     */
    @GetMapping("/{orderId}")  // ✅ GET = 리소스 조회
    public ResponseEntity<OrderDetailApiResponse> getOrder(@PathVariable Long orderId) {

        GetOrderQuery.Query query = new GetOrderQuery.Query(orderId);
        GetOrderQuery.Response response = getOrderQuery.getOrder(query);
        OrderDetailApiResponse apiResponse = orderApiMapper.toDetailApiResponse(response);

        return ResponseEntity.ok(apiResponse);  // ✅ 200 OK
    }

    /**
     * 주문 목록 조회 (페이징, 필터링, 정렬 지원)
     *
     * @param customerId 고객 ID (optional)
     * @param status 주문 상태 (optional)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @param sort 정렬 조건
     * @return 200 OK + 주문 목록 (페이징 정보 포함)
     */
    @GetMapping  // ✅ GET = 리소스 목록 조회
    public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        SearchOrdersQuery.Query query = SearchOrdersQuery.Query.builder()
            .customerId(customerId)
            .status(status)
            .page(page)
            .size(size)
            .sort(sort)
            .build();

        SearchOrdersQuery.Response response = searchOrdersQuery.searchOrders(query);
        PageResponse<OrderSummaryApiResponse> apiResponse = orderApiMapper.toPageApiResponse(response);

        return ResponseEntity.ok(apiResponse);  // ✅ 200 OK
    }
}
```

---

## 🎯 HTTP 메서드 활용 가이드

### HTTP 메서드 선택 기준

| HTTP 메서드 | 용도 | 멱등성 | 안전성 | 요청 본문 | 응답 본문 |
|------------|------|--------|--------|----------|----------|
| **GET** | 리소스 조회 | ✅ Yes | ✅ Yes | ❌ No | ✅ Yes |
| **POST** | 리소스 생성 | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| **PUT** | 리소스 전체 수정 | ✅ Yes | ❌ No | ✅ Yes | ✅ Yes |
| **PATCH** | 리소스 부분 수정 | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| **DELETE** | 리소스 삭제 | ✅ Yes | ❌ No | Optional | Optional |

**용어 설명:**
- **멱등성(Idempotent)**: 동일한 요청을 여러 번 실행해도 결과가 동일
- **안전성(Safe)**: 리소스 상태를 변경하지 않음

### 1. GET - 리소스 조회

```java
// ✅ Good: 단건 조회
@GetMapping("/{orderId}")
public ResponseEntity<OrderDetailApiResponse> getOrder(@PathVariable Long orderId) {
    // ...
}

// ✅ Good: 목록 조회 (Query Parameter 활용)
@GetMapping
public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
        @RequestParam(required = false) Long customerId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page) {
    // ...
}

// ❌ Bad: GET으로 상태 변경
@GetMapping("/{orderId}/approve")  // ❌ 상태 변경은 POST/PATCH 사용
public ResponseEntity<Void> approveOrder(@PathVariable Long orderId) {
    // ...
}
```

### 2. POST - 리소스 생성

```java
// ✅ Good: 새로운 리소스 생성
@PostMapping
public ResponseEntity<OrderApiResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest request) {

    // UseCase 실행
    CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

    // 201 Created + Location 헤더
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .header(HttpHeaders.LOCATION, "/api/v1/orders/" + response.orderId())
        .body(orderApiMapper.toApiResponse(response));
}

// ✅ Good: 하위 리소스 생성
@PostMapping("/{orderId}/items")
public ResponseEntity<OrderItemApiResponse> addOrderItem(
        @PathVariable Long orderId,
        @Valid @RequestBody AddOrderItemRequest request) {
    // ...
}

// ✅ Good: 비멱등 작업 (검색, 복잡한 쿼리 등)
@PostMapping("/search")  // GET으로 표현하기 복잡한 검색 조건
public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
        @Valid @RequestBody OrderSearchCriteria criteria) {
    // ...
}
```

### 3. PUT - 리소스 전체 수정

```java
// ✅ Good: 리소스 전체 교체
@PutMapping("/{orderId}")
public ResponseEntity<OrderApiResponse> replaceOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody ReplaceOrderRequest request) {

    // 주의: PUT은 리소스 전체를 교체하므로,
    // request에 모든 필드가 포함되어야 함
    ReplaceOrderUseCase.Command command = orderApiMapper.toReplaceCommand(orderId, request);
    ReplaceOrderUseCase.Response response = replaceOrderUseCase.replaceOrder(command);

    return ResponseEntity.ok(orderApiMapper.toApiResponse(response));
}

// ❌ Bad: PUT으로 부분 수정 (PATCH 사용해야 함)
@PutMapping("/{orderId}/status")  // ❌ 부분 수정은 PATCH
public ResponseEntity<OrderApiResponse> updateStatus(
        @PathVariable Long orderId,
        @RequestBody UpdateStatusRequest request) {
    // ...
}
```

### 4. PATCH - 리소스 부분 수정

```java
// ✅ Good: 리소스 부분 수정
@PatchMapping("/{orderId}/status")
public ResponseEntity<OrderApiResponse> updateOrderStatus(
        @PathVariable Long orderId,
        @Valid @RequestBody UpdateOrderStatusRequest request) {

    // 주의: PATCH는 일부 필드만 수정
    UpdateOrderUseCase.Command command = orderApiMapper.toUpdateCommand(orderId, request);
    UpdateOrderUseCase.Response response = updateOrderUseCase.updateOrderStatus(command);

    return ResponseEntity.ok(orderApiMapper.toApiResponse(response));
}

// ✅ Good: 여러 필드 부분 수정
@PatchMapping("/{orderId}")
public ResponseEntity<OrderApiResponse> updateOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody UpdateOrderRequest request) {
    // request에는 수정할 필드만 포함
    // null이 아닌 필드만 업데이트
    // ...
}
```

### 5. DELETE - 리소스 삭제

```java
// ✅ Good: 리소스 삭제 (논리적 삭제)
@DeleteMapping("/{orderId}")
public ResponseEntity<Void> cancelOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody CancelOrderRequest request) {  // 삭제 사유 포함 가능

    CancelOrderUseCase.Command command = orderApiMapper.toCancelCommand(orderId, request);
    cancelOrderUseCase.cancelOrder(command);

    return ResponseEntity.noContent().build();  // ✅ 204 No Content
}

// ✅ Good: 하위 리소스 삭제
@DeleteMapping("/{orderId}/items/{itemId}")
public ResponseEntity<Void> removeOrderItem(
        @PathVariable Long orderId,
        @PathVariable Long itemId) {

    RemoveOrderItemUseCase.Command command = new RemoveOrderItemUseCase.Command(orderId, itemId);
    removeOrderItemUseCase.removeOrderItem(command);

    return ResponseEntity.noContent().build();
}
```

---

## 🌐 URI 설계 원칙

### 1. URI 네이밍 규칙

#### ✅ 권장 규칙

```java
// ✅ Good: 명사 사용 (복수형)
@RequestMapping("/api/v1/orders")       // ✅ orders (복수형)
@RequestMapping("/api/v1/customers")    // ✅ customers (복수형)
@RequestMapping("/api/v1/products")     // ✅ products (복수형)

// ✅ Good: 계층 구조 표현 (하위 리소스)
@GetMapping("/orders/{orderId}/items")              // ✅ 주문의 항목들
@GetMapping("/customers/{customerId}/addresses")    // ✅ 고객의 주소들
@PostMapping("/products/{productId}/reviews")       // ✅ 상품의 리뷰들

// ✅ Good: kebab-case 사용 (복합 단어)
@GetMapping("/order-items")              // ✅ kebab-case
@GetMapping("/payment-methods")          // ✅ kebab-case
@GetMapping("/shipping-addresses")       // ✅ kebab-case

// ✅ Good: 소문자 사용
@GetMapping("/api/v1/customers")         // ✅ 소문자
```

#### ❌ 잘못된 패턴

```java
// ❌ Bad: 동사 사용
@PostMapping("/api/v1/createOrder")      // ❌ 동사
@GetMapping("/api/v1/getCustomers")      // ❌ 동사
@DeleteMapping("/api/v1/deleteProduct")  // ❌ 동사

// ❌ Bad: 단수형 (일관성 부족)
@RequestMapping("/api/v1/order")         // ❌ 단수형 (orders 권장)

// ❌ Bad: camelCase 또는 snake_case
@GetMapping("/orderItems")               // ❌ camelCase
@GetMapping("/payment_methods")          // ❌ snake_case

// ❌ Bad: 대문자 사용
@GetMapping("/api/v1/Customers")         // ❌ 대문자
@GetMapping("/api/v1/ORDERS")            // ❌ 대문자

// ❌ Bad: 불필요한 깊이
@GetMapping("/api/v1/companies/{companyId}/departments/{deptId}/teams/{teamId}/members/{memberId}/projects")
// ❌ 너무 깊은 계층 (3단계 이하 권장)
```

### 2. Path Variable vs Query Parameter

#### Path Variable (경로 변수)
**용도**: 리소스 식별, 계층 구조 표현

```java
// ✅ Good: 리소스 식별
@GetMapping("/orders/{orderId}")                           // ✅ 특정 주문 조회
@GetMapping("/customers/{customerId}/orders")              // ✅ 특정 고객의 주문 목록
@GetMapping("/orders/{orderId}/items/{itemId}")            // ✅ 특정 주문의 특정 항목

// ✅ Good: 하위 리소스 접근
@GetMapping("/products/{productId}/reviews/{reviewId}")    // ✅ 특정 상품의 특정 리뷰
```

#### Query Parameter (쿼리 파라미터)
**용도**: 필터링, 정렬, 페이징, 검색 조건

```java
// ✅ Good: 필터링 조건
@GetMapping("/orders")
public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
        @RequestParam(required = false) Long customerId,      // ✅ 필터: 고객 ID
        @RequestParam(required = false) String status,        // ✅ 필터: 주문 상태
        @RequestParam(required = false) LocalDate startDate,  // ✅ 필터: 시작일
        @RequestParam(required = false) LocalDate endDate) {  // ✅ 필터: 종료일
    // ...
}

// ✅ Good: 페이징
@GetMapping("/products")
public ResponseEntity<PageResponse<ProductApiResponse>> getProducts(
        @RequestParam(defaultValue = "0") int page,          // ✅ 페이지 번호
        @RequestParam(defaultValue = "20") int size) {       // ✅ 페이지 크기
    // ...
}

// ✅ Good: 정렬
@GetMapping("/customers")
public ResponseEntity<PageResponse<CustomerApiResponse>> getCustomers(
        @RequestParam(defaultValue = "createdAt,desc") String sort) {  // ✅ 정렬 조건
    // sort 형식: "필드명,방향" (예: "name,asc", "createdAt,desc")
    // ...
}

// ✅ Good: 복합 조건
@GetMapping("/products")
public ResponseEntity<PageResponse<ProductApiResponse>> searchProducts(
        @RequestParam(required = false) String keyword,      // ✅ 검색어
        @RequestParam(required = false) String category,     // ✅ 카테고리
        @RequestParam(required = false) Integer minPrice,    // ✅ 최소 가격
        @RequestParam(required = false) Integer maxPrice,    // ✅ 최대 가격
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "price,asc") String sort) {
    // ...
}
```

### 3. 버전 관리

```java
// ✅ Good: URI 경로에 버전 명시
@RequestMapping("/api/v1/orders")   // ✅ 버전 1
@RequestMapping("/api/v2/orders")   // ✅ 버전 2

// ❌ Bad: 버전 없음 (하위 호환성 문제)
@RequestMapping("/api/orders")

// ❌ Bad: 쿼리 파라미터로 버전 관리 (비권장)
@GetMapping("/api/orders?version=1")
```

---

## 📊 HTTP 상태 코드 가이드

### 성공 응답 (2xx)

| 상태 코드 | 의미 | 사용 시점 | 응답 본문 |
|----------|------|----------|----------|
| **200 OK** | 요청 성공 | GET, PUT, PATCH 성공 | ✅ Yes |
| **201 Created** | 리소스 생성 성공 | POST로 리소스 생성 | ✅ Yes + Location 헤더 |
| **204 No Content** | 성공 (응답 본문 없음) | DELETE 성공, PUT/PATCH 응답 불필요 시 | ❌ No |

```java
// ✅ 200 OK: 조회, 수정 성공
@GetMapping("/{orderId}")
public ResponseEntity<OrderDetailApiResponse> getOrder(@PathVariable Long orderId) {
    // ...
    return ResponseEntity.ok(apiResponse);  // 200 OK
}

// ✅ 201 Created: 생성 성공 + Location 헤더
@PostMapping
public ResponseEntity<OrderApiResponse> createOrder(@RequestBody CreateOrderRequest request) {
    // ...
    return ResponseEntity
        .status(HttpStatus.CREATED)  // 201 Created
        .header(HttpHeaders.LOCATION, "/api/v1/orders/" + response.orderId())
        .body(apiResponse);
}

// ✅ 204 No Content: 삭제 성공, 응답 본문 불필요
@DeleteMapping("/{orderId}")
public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
    // ...
    return ResponseEntity.noContent().build();  // 204 No Content
}
```

### 클라이언트 에러 (4xx)

| 상태 코드 | 의미 | 사용 시점 |
|----------|------|----------|
| **400 Bad Request** | 잘못된 요청 | 검증 실패, 파라미터 오류 |
| **401 Unauthorized** | 인증 필요 | 인증되지 않은 사용자 |
| **403 Forbidden** | 권한 없음 | 인증됐으나 권한 부족 |
| **404 Not Found** | 리소스 없음 | 존재하지 않는 리소스 조회 |
| **409 Conflict** | 충돌 | 비즈니스 규칙 위반 |

```java
// GlobalExceptionHandler에서 자동 처리
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 Bad Request: 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)  // 400
            .body(ErrorResponse.from(ex));
    }

    // 404 Not Found: 리소스 없음
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            OrderNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)  // 404
            .body(ErrorResponse.from(ex));
    }

    // 409 Conflict: 비즈니스 규칙 위반
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(
            InsufficientStockException ex) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)  // 409
            .body(ErrorResponse.from(ex));
    }
}
```

### 서버 에러 (5xx)

| 상태 코드 | 의미 | 사용 시점 |
|----------|------|----------|
| **500 Internal Server Error** | 서버 내부 오류 | 예상치 못한 예외 |
| **503 Service Unavailable** | 서비스 이용 불가 | 서버 과부하, 점검 중 |

```java
// GlobalExceptionHandler에서 자동 처리
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleInternalServerError(Exception ex) {
    log.error("Unexpected error occurred", ex);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)  // 500
        .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "An unexpected error occurred"));
}
```

---

## 🔍 고급 패턴

### 1. Pagination (페이징)

```java
/**
 * 페이지네이션 응답
 *
 * @param <T> 컨텐츠 타입
 */
public record PageResponse<T>(
    List<T> content,              // 데이터 목록
    int page,                     // 현재 페이지 (0부터 시작)
    int size,                     // 페이지 크기
    long totalElements,           // 전체 요소 수
    int totalPages,               // 전체 페이지 수
    boolean first,                // 첫 페이지 여부
    boolean last                  // 마지막 페이지 여부
) {
    public static <T> PageResponse<T> of(Page<T> page) {
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
}

@GetMapping
public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort) {

    SearchOrdersQuery.Query query = SearchOrdersQuery.Query.builder()
        .page(page)
        .size(size)
        .sort(sort)
        .build();

    Page<OrderSummary> resultPage = searchOrdersQuery.searchOrders(query);
    PageResponse<OrderSummaryApiResponse> response = PageResponse.of(
        resultPage.map(orderApiMapper::toSummaryApiResponse)
    );

    return ResponseEntity.ok(response);
}
```

### 2. Filtering (필터링)

```java
@GetMapping
public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
        // 필터 조건
        @RequestParam(required = false) Long customerId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) Integer minAmount,
        @RequestParam(required = false) Integer maxAmount,

        // 페이징 & 정렬
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort) {

    // Query 객체로 조합
    SearchOrdersQuery.Query query = SearchOrdersQuery.Query.builder()
        .customerId(customerId)
        .status(status)
        .startDate(startDate)
        .endDate(endDate)
        .minAmount(minAmount)
        .maxAmount(maxAmount)
        .page(page)
        .size(size)
        .sort(sort)
        .build();

    // UseCase 실행
    Page<OrderSummary> resultPage = searchOrdersQuery.searchOrders(query);
    PageResponse<OrderSummaryApiResponse> response = PageResponse.of(
        resultPage.map(orderApiMapper::toSummaryApiResponse)
    );

    return ResponseEntity.ok(response);
}
```

### 3. Sorting (정렬)

```java
/**
 * 정렬 조건 파싱 유틸리티
 */
public class SortParser {

    /**
     * 정렬 문자열 파싱
     *
     * @param sort 정렬 문자열 (예: "createdAt,desc" 또는 "name,asc")
     * @return Sort 객체
     */
    public static Sort parse(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }

        String[] parts = sort.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid sort format: " + sort);
        }

        String property = parts[0];
        String direction = parts[1];

        return "asc".equalsIgnoreCase(direction)
            ? Sort.by(property).ascending()
            : Sort.by(property).descending();
    }
}

@GetMapping
public ResponseEntity<PageResponse<ProductApiResponse>> getProducts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "name,asc") String sort) {

    Sort sortObj = SortParser.parse(sort);  // 정렬 파싱
    Pageable pageable = PageRequest.of(page, size, sortObj);

    // ...
}
```

---

## 📋 실무 체크리스트

### API 설계 시
- [ ] URI는 명사(리소스) 기반으로 설계했는가?
- [ ] HTTP 메서드를 올바르게 사용했는가? (GET=조회, POST=생성, PUT=전체수정, PATCH=부분수정, DELETE=삭제)
- [ ] 복수형 명사를 사용했는가? (`/orders`, `/customers`)
- [ ] kebab-case를 사용했는가? (`/order-items`, `/payment-methods`)
- [ ] 버전을 URI에 포함했는가? (`/api/v1/...`)
- [ ] 적절한 HTTP 상태 코드를 반환하는가? (200, 201, 204, 400, 404, 409, 500)

### Controller 구현 시
- [ ] Command와 Query를 분리했는가? (CQRS 패턴)
- [ ] `@Valid`로 요청 검증을 수행하는가?
- [ ] Mapper를 통해 API DTO ↔ UseCase DTO 변환을 수행하는가?
- [ ] UseCase 인터페이스에 의존하는가? (구현체가 아닌)
- [ ] 적절한 ResponseEntity를 반환하는가?
- [ ] 생성 시 Location 헤더를 포함하는가? (201 Created)

### 페이징/필터링/정렬 시
- [ ] 페이징 파라미터는 Query Parameter로 전달하는가?
- [ ] 기본값(defaultValue)을 설정했는가?
- [ ] 페이지 번호는 0부터 시작하는가?
- [ ] 정렬 형식은 "필드명,방향" 형식인가?
- [ ] 필터 조건은 `required = false`로 설정했는가?

### Path Variable vs Query Parameter
- [ ] 리소스 식별에는 Path Variable을 사용하는가?
- [ ] 필터링/정렬/페이징에는 Query Parameter를 사용하는가?
- [ ] Query Parameter는 모두 optional로 설정했는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
