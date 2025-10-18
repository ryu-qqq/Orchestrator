# API Response Handling - 응답 처리 및 표준화

> **목적**: REST API 응답의 일관된 처리 및 HTTP 상태 코드 활용 가이드
>
> **위치**: `adapter/in/rest-api-*/src/main/java/com/company/adapter/in/rest/[boundedContext]/controller/`
>
> **관련 문서**:
> - `package-guide/01_rest_api_package_guide.md` (전체 구조)
> - `controller-design/01_restful-api-design.md` (RESTful API 설계)
> - `dto-patterns/02_api-response-dto.md` (Response DTO 패턴)
> - `08-error-handling/03_global-exception-handler.md` (에러 응답)
> - `08-error-handling/04_error-response-format.md` (에러 응답 포맷)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. ResponseEntity 사용

**모든 Controller 메서드는 ResponseEntity를 반환**

```java
// ✅ Good: ResponseEntity로 상태 코드 명시
@PostMapping
public ResponseEntity<OrderApiResponse> createOrder(@RequestBody CreateOrderRequest request) {
    return ResponseEntity
        .status(HttpStatus.CREATED)  // 201 Created
        .body(response);
}

// ❌ Bad: 상태 코드 제어 불가
@PostMapping
public OrderApiResponse createOrder(@RequestBody CreateOrderRequest request) {
    return response;  // 항상 200 OK
}
```

### 2. HTTP 상태 코드 명시

**적절한 HTTP 상태 코드로 의미 전달**

```
성공 응답:
- 200 OK: 조회, 수정 성공
- 201 Created: 생성 성공
- 204 No Content: 삭제 성공 (응답 본문 없음)

에러 응답:
- 400 Bad Request: 검증 실패, 잘못된 요청
- 404 Not Found: 리소스 없음
- 409 Conflict: 비즈니스 규칙 위반
- 500 Internal Server Error: 서버 오류
```

### 3. 응답 본문 표준화

**성공/에러 모두 일관된 JSON 구조 사용**

```json
// 성공 응답
{
  "orderId": 123,
  "status": "CONFIRMED",
  "totalAmount": 50000,
  "createdAt": "2025-10-17T10:30:00Z"
}

// 에러 응답 (GlobalExceptionHandler가 자동 생성)
{
  "code": "ORDER-001",
  "message": "Order not found: 999",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders/999"
}
```

---

## ❌ Anti-Pattern: 상태 코드 오용

### 잘못된 설계 (모두 200 OK 반환)

```java
// ❌ Bad: 모든 응답이 200 OK
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    /**
     * ❌ 문제점:
     * - 생성 성공 시 201 Created가 아닌 200 OK 반환
     * - 에러 발생 시 200 OK + success: false 패턴 (안티패턴)
     */
    @PostMapping
    public OrderApiResponse createOrder(@RequestBody CreateOrderRequest request) {
        // ❌ 항상 200 OK 반환
        return orderService.createOrder(request);
    }

    /**
     * ❌ 문제점:
     * - 삭제 성공 시에도 200 OK + 빈 응답
     * - 204 No Content가 더 적절
     */
    @DeleteMapping("/{orderId}")
    public void cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        // ❌ 200 OK + 빈 응답
    }

    /**
     * ❌ 문제점:
     * - 에러를 200 OK로 반환
     * - HTTP 상태 코드로 성공/실패 구분 불가
     */
    @GetMapping("/{orderId}")
    public Map<String, Object> getOrder(@PathVariable Long orderId) {
        try {
            Order order = orderService.getOrder(orderId);
            return Map.of(
                "success", true,
                "data", order
            );
        } catch (OrderNotFoundException ex) {
            // ❌ 에러도 200 OK로 반환
            return Map.of(
                "success", false,
                "error", ex.getMessage()
            );
        }
    }
}
```

### 문제점
- ❌ **의미 전달 실패**: 모두 200 OK로 성공/실패 구분 불가
- ❌ **RESTful 원칙 위반**: HTTP 상태 코드의 의미 무시
- ❌ **클라이언트 부담**: 응답 본문을 파싱해야만 성공/실패 판단 가능
- ❌ **HTTP 캐싱 불가**: 상태 코드 기반 캐싱 전략 사용 불가

---

## ✅ 권장 패턴: ResponseEntity 활용

### 1. 생성(Create) - 201 Created

```java
/**
 * 주문 생성
 *
 * @param request 주문 생성 요청
 * @return 201 Created + Location 헤더 + 생성된 주문 정보
 */
@PostMapping
public ResponseEntity<OrderApiResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest request) {

    CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);
    CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);
    OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

    return ResponseEntity
        .status(HttpStatus.CREATED)  // ✅ 201 Created
        .header(HttpHeaders.LOCATION, "/api/v1/orders/" + apiResponse.orderId())  // ✅ Location 헤더
        .body(apiResponse);
}
```

**응답 예시**:
```http
HTTP/1.1 201 Created
Location: /api/v1/orders/123
Content-Type: application/json

{
  "orderId": 123,
  "customerId": 456,
  "status": "CONFIRMED",
  "totalAmount": 50000,
  "createdAt": "2025-10-17T10:30:00Z"
}
```

### 2. 조회(Read) - 200 OK

#### 단건 조회

```java
/**
 * 주문 단건 조회
 *
 * @param orderId 주문 ID
 * @return 200 OK + 주문 상세 정보
 */
@GetMapping("/{orderId}")
public ResponseEntity<OrderDetailApiResponse> getOrder(@PathVariable Long orderId) {

    GetOrderQuery.Query query = new GetOrderQuery.Query(orderId);
    GetOrderQuery.Response response = getOrderQuery.getOrder(query);
    OrderDetailApiResponse apiResponse = orderApiMapper.toDetailApiResponse(response);

    return ResponseEntity.ok(apiResponse);  // ✅ 200 OK
}
```

**응답 예시**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "orderId": 123,
  "customerId": 456,
  "customerName": "홍길동",
  "status": "CONFIRMED",
  "items": [
    {
      "productId": 789,
      "productName": "상품A",
      "quantity": 2,
      "unitPrice": 25000,
      "totalPrice": 50000
    }
  ],
  "totalAmount": 50000,
  "createdAt": "2025-10-17T10:30:00Z",
  "confirmedAt": "2025-10-17T10:35:00Z"
}
```

#### 목록 조회 (페이징)

```java
/**
 * 주문 목록 조회 (페이징)
 *
 * @param customerId 고객 ID (optional)
 * @param status 주문 상태 (optional)
 * @param page 페이지 번호
 * @param size 페이지 크기
 * @param sort 정렬 조건
 * @return 200 OK + 페이징된 주문 목록
 */
@GetMapping
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
```

**응답 예시**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "content": [
    {
      "orderId": 123,
      "customerId": 456,
      "customerName": "홍길동",
      "status": "CONFIRMED",
      "totalAmount": 50000,
      "createdAt": "2025-10-17T10:30:00Z"
    },
    {
      "orderId": 124,
      "customerId": 457,
      "customerName": "김철수",
      "status": "SHIPPED",
      "totalAmount": 75000,
      "createdAt": "2025-10-17T11:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

### 3. 수정(Update) - 200 OK

```java
/**
 * 주문 상태 변경
 *
 * @param orderId 주문 ID
 * @param request 상태 변경 요청
 * @return 200 OK + 수정된 주문 정보
 */
@PatchMapping("/{orderId}/status")
public ResponseEntity<OrderApiResponse> updateOrderStatus(
        @PathVariable Long orderId,
        @Valid @RequestBody UpdateOrderStatusRequest request) {

    UpdateOrderUseCase.Command command = orderApiMapper.toUpdateCommand(orderId, request);
    UpdateOrderUseCase.Response response = updateOrderUseCase.updateOrderStatus(command);
    OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

    return ResponseEntity.ok(apiResponse);  // ✅ 200 OK
}
```

**응답 예시**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "orderId": 123,
  "status": "SHIPPED",
  "totalAmount": 50000,
  "updatedAt": "2025-10-17T15:00:00Z"
}
```

### 4. 삭제(Delete) - 204 No Content

```java
/**
 * 주문 취소 (논리적 삭제)
 *
 * @param orderId 주문 ID
 * @param request 취소 요청 (취소 사유 포함)
 * @return 204 No Content
 */
@DeleteMapping("/{orderId}")
public ResponseEntity<Void> cancelOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody CancelOrderRequest request) {

    CancelOrderUseCase.Command command = orderApiMapper.toCancelCommand(orderId, request);
    cancelOrderUseCase.cancelOrder(command);

    return ResponseEntity.noContent().build();  // ✅ 204 No Content
}
```

**응답 예시**:
```http
HTTP/1.1 204 No Content
```

---

## 🎯 특수 응답 패턴

### 1. 비어있는 목록 조회

```java
/**
 * 비어있는 목록도 200 OK 반환
 *
 * @return 200 OK + 빈 리스트
 */
@GetMapping
public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
        @RequestParam Long customerId) {

    // 조회 결과가 없어도 200 OK
    PageResponse<OrderSummaryApiResponse> emptyResponse = PageResponse.empty();

    return ResponseEntity.ok(emptyResponse);  // ✅ 200 OK
}
```

**응답 예시**:
```json
{
  "content": [],  // 빈 리스트
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

### 2. 조건부 응답 (If-None-Match)

```java
/**
 * ETag 기반 조건부 조회
 *
 * @param orderId 주문 ID
 * @param ifNoneMatch If-None-Match 헤더 값
 * @return 200 OK 또는 304 Not Modified
 */
@GetMapping("/{orderId}")
public ResponseEntity<OrderDetailApiResponse> getOrder(
        @PathVariable Long orderId,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

    GetOrderQuery.Response response = getOrderQuery.getOrder(new GetOrderQuery.Query(orderId));
    OrderDetailApiResponse apiResponse = orderApiMapper.toDetailApiResponse(response);

    // ETag 생성 (버전 또는 해시 기반)
    String etag = "\"" + response.version() + "\"";

    // 클라이언트 캐시가 최신이면 304 Not Modified
    if (etag.equals(ifNoneMatch)) {
        return ResponseEntity
            .status(HttpStatus.NOT_MODIFIED)  // ✅ 304 Not Modified
            .eTag(etag)
            .build();
    }

    // 변경되었으면 200 OK + 데이터
    return ResponseEntity
        .ok()
        .eTag(etag)
        .body(apiResponse);
}
```

### 3. 부분 성공 응답 (Multi-Status)

```java
/**
 * 일괄 처리 시 부분 성공
 *
 * @param request 일괄 주문 생성 요청
 * @return 207 Multi-Status + 성공/실패 목록
 */
@PostMapping("/batch")
public ResponseEntity<BatchOrderApiResponse> createBatchOrders(
        @Valid @RequestBody BatchOrderRequest request) {

    BatchOrderUseCase.Response response = batchOrderUseCase.createOrders(
        orderApiMapper.toBatchCommand(request)
    );

    BatchOrderApiResponse apiResponse = orderApiMapper.toBatchApiResponse(response);

    // 일부 성공, 일부 실패
    if (apiResponse.hasFailures()) {
        return ResponseEntity
            .status(HttpStatus.MULTI_STATUS)  // ✅ 207 Multi-Status
            .body(apiResponse);
    }

    // 모두 성공
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(apiResponse);
}
```

**응답 예시** (207 Multi-Status):
```json
{
  "totalRequests": 10,
  "successCount": 7,
  "failureCount": 3,
  "results": [
    {
      "index": 0,
      "status": "SUCCESS",
      "orderId": 123
    },
    {
      "index": 1,
      "status": "FAILED",
      "errorCode": "ORDER-002",
      "errorMessage": "Insufficient stock"
    }
  ]
}
```

---

## 📦 PageResponse 패턴

### PageResponse Record

```java
package com.company.adapter.in.rest.shared.dto;

import java.util.List;
import java.util.function.Function;

/**
 * 페이징 응답 DTO
 *
 * <p>Spring Data의 Page를 API 응답으로 변환합니다.
 *
 * @param <T> 컨텐츠 타입
 * @author Development Team
 * @since 1.0.0
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

    /**
     * Spring Data Page를 PageResponse로 변환
     *
     * @param page Spring Data Page
     * @param <T> 컨텐츠 타입
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
     * Page 변환과 함께 컨텐츠 매핑
     *
     * @param page Spring Data Page
     * @param mapper 컨텐츠 변환 함수
     * @param <S> 소스 타입
     * @param <T> 타겟 타입
     * @return PageResponse
     */
    public static <S, T> PageResponse<T> of(
            org.springframework.data.domain.Page<S> page,
            Function<S, T> mapper) {

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

    /**
     * 빈 페이지 응답 생성
     *
     * @param <T> 컨텐츠 타입
     * @return 빈 PageResponse
     */
    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(
            List.of(),
            0,
            20,
            0,
            0,
            true,
            true
        );
    }
}
```

### 사용 예시

```java
@GetMapping
public ResponseEntity<PageResponse<OrderSummaryApiResponse>> searchOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    Pageable pageable = PageRequest.of(page, size);
    Page<OrderSummary> orderPage = searchOrdersQuery.searchOrders(pageable);

    // ✅ 방법 1: Spring Page를 직접 변환
    PageResponse<OrderSummaryApiResponse> response = PageResponse.of(
        orderPage,
        orderApiMapper::toSummaryApiResponse  // 메서드 참조
    );

    // ✅ 방법 2: 수동 매핑
    List<OrderSummaryApiResponse> mappedContent = orderPage.getContent()
        .stream()
        .map(orderApiMapper::toSummaryApiResponse)
        .toList();

    PageResponse<OrderSummaryApiResponse> response2 = PageResponse.of(
        orderPage.map(orderApiMapper::toSummaryApiResponse)
    );

    return ResponseEntity.ok(response);
}
```

---

## 🚨 에러 응답 (GlobalExceptionHandler 연계)

### GlobalExceptionHandler가 자동 처리

```java
/**
 * 글로벌 예외 핸들러
 *
 * <p>모든 Controller에서 발생하는 예외를 자동으로 적절한 HTTP 상태 코드와
 * ErrorResponse로 변환합니다.
 *
 * @see 08-error-handling/03_global-exception-handler.md
 * @see 08-error-handling/04_error-response-format.md
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 Not Found - 리소스 없음
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            OrderNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of(
            ex.getErrorCode().getCode(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)  // ✅ 404 Not Found
            .body(response);
    }

    /**
     * 400 Bad Request - 검증 실패
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ErrorResponse.FieldError(
                error.getField(),
                error.getRejectedValue(),
                error.getDefaultMessage()
            ))
            .toList();

        ErrorResponse response = ErrorResponse.of(
            "VALIDATION_FAILED",
            "Validation failed",
            request.getRequestURI(),
            fieldErrors
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)  // ✅ 400 Bad Request
            .body(response);
    }

    /**
     * 409 Conflict - 비즈니스 규칙 위반
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(
            InsufficientStockException ex,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.of(
            ex.getErrorCode().getCode(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.CONFLICT)  // ✅ 409 Conflict
            .body(response);
    }

    /**
     * 500 Internal Server Error - 서버 오류
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse response = ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred. Please try again later.",
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)  // ✅ 500
            .body(response);
    }
}
```

### 에러 응답 예시

**404 Not Found**:
```json
{
  "code": "ORDER-001",
  "message": "Order not found: 999",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders/999"
}
```

**400 Bad Request** (Validation):
```json
{
  "code": "VALIDATION_FAILED",
  "message": "Validation failed",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders",
  "errors": [
    {
      "field": "customerId",
      "rejectedValue": null,
      "message": "Customer ID is required"
    }
  ]
}
```

**409 Conflict**:
```json
{
  "code": "ORDER-002",
  "message": "Insufficient stock for product 100: requested=50, available=10",
  "timestamp": "2025-10-17T10:30:00",
  "path": "/api/v1/orders"
}
```

---

## 📋 실무 체크리스트

### ResponseEntity 사용
- [ ] 모든 Controller 메서드가 `ResponseEntity<T>`를 반환하는가?
- [ ] 적절한 HTTP 상태 코드를 명시적으로 설정하는가?
- [ ] 생성(POST) 시 201 Created를 반환하는가?
- [ ] 삭제(DELETE) 시 204 No Content를 반환하는가?

### HTTP 헤더
- [ ] 생성 시 Location 헤더를 포함하는가?
- [ ] 적절한 Content-Type을 설정하는가? (application/json)
- [ ] 필요 시 ETag, Cache-Control 헤더를 활용하는가?

### 응답 본문
- [ ] Response DTO에 `Api` 접두사를 사용하는가? (예: `OrderApiResponse`)
- [ ] Response DTO는 Java Record로 작성되었는가?
- [ ] Entity를 직접 반환하지 않는가? (DTO 변환 필수)

### 페이징
- [ ] 페이징 응답에 `PageResponse<T>`를 사용하는가?
- [ ] 페이징 정보 (page, size, totalElements 등)를 포함하는가?
- [ ] 빈 목록도 200 OK로 반환하는가?

### 에러 처리
- [ ] GlobalExceptionHandler에서 에러 응답을 통일했는가?
- [ ] 에러 응답에 `ErrorResponse`를 사용하는가?
- [ ] 적절한 HTTP 상태 코드를 반환하는가? (400, 404, 409, 500)
- [ ] 민감한 정보를 에러 응답에 노출하지 않는가?

### 일관성
- [ ] 모든 API가 동일한 응답 구조를 사용하는가?
- [ ] 성공/에러 응답 형식이 일관적인가?
- [ ] HTTP 상태 코드 사용이 일관적인가?

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
