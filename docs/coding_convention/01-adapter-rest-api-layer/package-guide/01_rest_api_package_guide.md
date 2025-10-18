# REST API 패키지 가이드

> REST API Adapter Layer. **헥사고날 아키텍처의 Inbound Adapter**, **API DTO ↔ UseCase DTO 변환**.

**중요**: REST API Layer는 **외부 HTTP 요청을 Application Layer로 전달**하는 Adapter입니다. Mapper는 API DTO와 UseCase DTO 간 변환만 담당하며, 비즈니스 로직은 Application Layer에서 처리합니다.

---

## 디렉터리 구조

### 멀티모듈 위치

```
root/
├─ domain/                          # 순수 도메인 모델
├─ application/                     # UseCase, Port 인터페이스
├─ adapter/in/rest-api-admin/       # ✅ REST API Adapter (Admin)
├─ adapter/in/rest-api-mobile/      # ✅ REST API Adapter (Mobile)
├─ adapter/out/persistence-mysql/   # MySQL 영속성
└─ adapter/out/external/            # 외부 API
```

### REST API Layer 내부 구조 (Bounded Context 중심)

```
adapter/in/rest-api-admin/
└─ src/main/java/com/company/adapter/in/rest/
   ├─ shared/                        # 공통 모듈
   │  ├─ dto/                        # 공통 DTO
   │  │  ├─ ErrorResponse.java
   │  │  ├─ PageResponse.java
   │  │  └─ ApiResponse.java
   │  ├─ exception/                  # 글로벌 예외 처리
   │  │  ├─ GlobalExceptionHandler.java
   │  │  └─ ErrorCode.java
   │  ├─ filter/                     # 공통 필터
   │  │  ├─ AuthenticationFilter.java
   │  │  └─ LoggingFilter.java
   │  ├─ config/                     # Web 설정
   │  │  ├─ WebMvcConfig.java
   │  │  └─ CorsConfig.java
   │  └─ validator/                  # 커스텀 Validator
   │     └─ CustomValidators.java
   │
   ├─ order/                         # Order Bounded Context
   │  ├─ controller/
   │  │  ├─ OrderCommandController.java
   │  │  └─ OrderQueryController.java
   │  ├─ dto/
   │  │  ├─ request/
   │  │  │  ├─ CreateOrderRequest.java
   │  │  │  └─ UpdateOrderStatusRequest.java
   │  │  └─ response/
   │  │     ├─ OrderApiResponse.java
   │  │     └─ OrderSummaryApiResponse.java
   │  └─ mapper/
   │     └─ OrderApiMapper.java
   │
   ├─ payment/                       # Payment Bounded Context
   │  ├─ controller/
   │  ├─ dto/
   │  │  ├─ request/
   │  │  └─ response/
   │  └─ mapper/
   │
   └─ product/                       # Product Bounded Context
      ├─ controller/
      ├─ dto/
      │  ├─ request/
      │  └─ response/
      └─ mapper/
```

---

## 포함할 객체 & 역할

### 1. shared/ - 공통 모듈

#### dto/ - 공통 DTO
**역할**:
- 모든 API에서 사용하는 공통 응답 구조
- 에러 응답, 페이지네이션, API 래퍼

**예시**:
- `ErrorResponse.java` - 에러 응답 표준
- `PageResponse.java` - 페이지네이션 응답
- `ApiResponse.java` - API 응답 래퍼 (선택적)
- `FieldError.java` - 검증 에러 필드

#### exception/ - 글로벌 예외 처리
**역할**:
- `@RestControllerAdvice`로 모든 예외 처리
- Domain Exception → HTTP 응답 변환
- Validation Exception 처리

**예시**:
- `GlobalExceptionHandler.java` - 예외 핸들러
- `ErrorCode.java` - 에러 코드 매핑

#### filter/ - 공통 필터
**역할**:
- 인증/인가 필터
- 로깅 필터
- CORS 필터

**예시**:
- `AuthenticationFilter.java` - 토큰 검증
- `LoggingFilter.java` - 요청/응답 로깅
- `CorrelationIdFilter.java` - Trace ID

#### config/ - Web 설정
**역할**:
- Spring MVC 설정
- CORS 설정
- Swagger 설정

**예시**:
- `WebMvcConfig.java` - MVC 설정
- `CorsConfig.java` - CORS 설정
- `SwaggerConfig.java` - API 문서

#### validator/ - 커스텀 Validator
**역할**:
- Bean Validation 커스텀 검증기
- 복잡한 검증 로직 캡슐화

**예시**:
- `@ValidOrderStatus` - 주문 상태 검증
- `@ValidPhoneNumber` - 전화번호 검증

---

### 2. [boundedContext]/ - Bounded Context별 모듈

#### controller/ - REST Controller
**역할**:
- HTTP 요청 수신
- Request DTO 검증 (`@Valid`)
- Mapper로 Command/Query 변환
- UseCase 호출
- Response DTO 반환

**규칙**:
- `@RestController` 어노테이션
- CQRS 분리 (Command/Query Controller)
- `@RequestMapping("/api/[version]/[context]")`
- **비즈니스 로직 포함 금지** (UseCase로 위임)

**예시**:
```java
@RestController
@RequestMapping("/api/[version]/orders")
public class OrderCommandController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;

    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        // ✅ 1. Mapper: API Request → UseCase.Command
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // ✅ 2. UseCase 실행
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // ✅ 3. Mapper: UseCase.Response → API Response
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }
}
```

#### dto/request/ - API Request DTO
**역할**:
- HTTP 요청 본문 매핑
- Bean Validation (`@NotNull`, `@Valid`)
- Compact Constructor 추가 검증

**규칙**:
- Java Record 사용
- Immutable (불변)
- Bean Validation 어노테이션
- **UseCase Command와 별개** (Adapter Layer DTO)

**예시**:
```java
package com.company.adapter.in.rest.order.dto.request;

import jakarta.validation.constraints.*;

/**
 * Create Order API Request
 *
 * @author development-team
 * @since 1.0.0
 */
public record CreateOrderRequest(
    @NotNull(message = "Customer ID is required")
    Long customerId,

    @NotEmpty(message = "Items cannot be empty")
    @Valid
    List<OrderItemRequest> items,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes
) {
    /**
     * ✅ Compact Constructor - 추가 검증
     */
    public CreateOrderRequest {
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException("Invalid customer ID: " + customerId);
        }
    }

    public record OrderItemRequest(
        @NotNull Long productId,
        @Min(1) @Max(1000) Integer quantity,
        @NotNull Long unitPrice
    ) {}
}
```

#### dto/response/ - API Response DTO
**역할**:
- HTTP 응답 본문 생성
- JSON 직렬화 (`@JsonFormat`)
- Entity 내부 구조 숨김

**규칙**:
- Java Record 사용
- Immutable (불변)
- **UseCase Response와 별개** (Adapter Layer DTO)
- **`Api` 접두사 필수** (Application Layer DTO와 구분)
- Entity 직접 노출 금지

**네이밍 패턴**:
- `[Aggregate]ApiResponse` (예: `OrderApiResponse`)
- `[Aggregate][Type]ApiResponse` (예: `OrderSummaryApiResponse`, `OrderDetailApiResponse`)

**예시**:
```java
package com.company.adapter.in.rest.order.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * Order API Response DTO
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderApiResponse(
    Long orderId,
    Long customerId,
    String status,
    Long totalAmount,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant createdAt
) {}
```

#### mapper/ - API Mapper
**역할**:
- **API Request → UseCase.Command** 변환
- **UseCase.Response → API Response** 변환
- **단순 매핑만** (비즈니스 로직 없음)

**규칙**:
- `@Component` 어노테이션
- Stateless (상태 없음)
- **Assembler와 구분** (Application Layer의 Assembler는 UseCase DTO ↔ Domain 변환)

**예시**:
```java
package com.company.adapter.in.rest.order.mapper;

import com.company.adapter.in.rest.order.dto.request.CreateOrderRequest;
import com.company.adapter.in.rest.order.dto.response.OrderApiResponse;
import com.company.application.order.port.in.CreateOrderUseCase;
import org.springframework.stereotype.Component;

/**
 * Order API Mapper (Adapter Layer)
 * - API Request → UseCase.Command
 * - UseCase.Response → API Response
 *
 * ⚠️ Assembler와 구분:
 * - Mapper (Adapter): API DTO ↔ UseCase DTO
 * - Assembler (Application): UseCase DTO ↔ Domain
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderApiMapper {

    /**
     * ✅ API Request → UseCase.Command 변환
     */
    public CreateOrderUseCase.Command toCommand(CreateOrderRequest request) {
        List<CreateOrderUseCase.Command.OrderItem> items = request.items().stream()
            .map(item -> new CreateOrderUseCase.Command.OrderItem(
                item.productId(),
                item.quantity(),
                item.unitPrice()
            ))
            .toList();

        return new CreateOrderUseCase.Command(
            request.customerId(),
            items,
            request.notes()
        );
    }

    /**
     * ✅ UseCase.Response → API Response 변환
     */
    public OrderApiResponse toApiResponse(CreateOrderUseCase.Response response) {
        return new OrderApiResponse(
            response.orderId(),
            response.customerId(),
            response.status(),
            response.totalAmount(),
            response.createdAt()
        );
    }
}
```

---

## 계층 간 데이터 변환 흐름

### 전체 데이터 변환 흐름

```
[Adapter Layer - Controller]
HTTP Request (JSON)
    ↓
[Adapter Layer - Request DTO]
CreateOrderRequest (API DTO)
    ↓
[Adapter Layer - Mapper]
    ↓ toCommand()
CreateOrderUseCase.Command (UseCase DTO)
    ↓
[Application Layer - UseCase Service]
    ↓
[Application Layer - Assembler]
    ↓ toDomain()
Order (Domain Object)
    ↓
[Domain Layer - Business Logic]
    ↓
Order (Domain Result)
    ↓
[Application Layer - Assembler]
    ↓ toResponse()
CreateOrderUseCase.Response (UseCase DTO)
    ↓
[Adapter Layer - Mapper]
    ↓ toApiResponse()
OrderApiResponse (API DTO)
    ↓
[Adapter Layer - Controller]
HTTP Response (JSON)
```

### Mapper vs Assembler 비교

| 구분 | Mapper (Adapter) | Assembler (Application) |
|------|------------------|-------------------------|
| **위치** | `adapter/in/rest/[context]/mapper/` | `application/[context]/assembler/` |
| **변환** | API DTO ↔ UseCase DTO | UseCase DTO ↔ Domain |
| **의존성** | API Request/Response, UseCase Command/Response | UseCase Command/Response, Domain |
| **복잡도** | 단순 매핑 | Value Object 변환, 조립 로직 |
| **예제** | `OrderApiMapper` | `OrderAssembler` |

**중요**: Mapper와 Assembler는 **역할이 다릅니다**!
- **Mapper**: Adapter Layer에서 API DTO ↔ UseCase DTO 변환
- **Assembler**: Application Layer에서 UseCase DTO ↔ Domain 변환

---

## 허용/금지 의존성

### 허용 의존성
- `java.*` (표준 자바 API)
- `org.springframework.web.*` (Spring MVC)
- `org.springframework.http.*` (HTTP)
- `jakarta.validation.*` (Bean Validation)
- `com.fasterxml.jackson.*` (JSON 직렬화)
- `application..port.in..` (Inbound Port - UseCase)
- **읽기 전용**: `application..port.in..[UseCase].Command`, `[UseCase].Response`

### 금지 의존성
- `domain..` (Domain Layer 직접 의존 금지)
- `application..service..` (Application Service 직접 호출 금지)
- `application..assembler..` (Assembler 직접 호출 금지)
- `application..port.out..` (Outbound Port 직접 호출 금지)
- `adapter.out..` (다른 Adapter 직접 의존 금지)
- `jakarta.persistence.*` (JPA는 Persistence Adapter에서만)

### 의존성 방향

```
[Adapter Layer - REST API]
    ↓ (의존)
[Application Layer - UseCase Interface]
    ↓ (의존)
[Domain Layer]
```

**핵심**: Adapter는 **UseCase 인터페이스만** 의존하고, 구현체는 의존하지 않음 (의존성 역전)

---

## 네이밍 규약

### Controller
- **Command Controller**: `[Aggregate]CommandController` (예: `OrderCommandController`)
- **Query Controller**: `[Aggregate]QueryController` (예: `OrderQueryController`)
- **단일 Controller**: `[Aggregate]Controller` (CQRS 분리하지 않는 경우)

### Request DTO
- **Command**: `[Action][Aggregate]Request` (예: `CreateOrderRequest`, `UpdateOrderStatusRequest`)
- **Query**: `[Aggregate]SearchRequest`, `[Aggregate]FilterRequest` (예: `OrderSearchRequest`)

### Response DTO
- **단일 조회**: `[Aggregate]ApiResponse` (예: `OrderApiResponse`)
- **목록 조회**: `[Aggregate]SummaryApiResponse`, `[Aggregate]ListApiResponse` (예: `OrderSummaryApiResponse`)
- **상세 조회**: `[Aggregate]DetailApiResponse` (예: `OrderDetailApiResponse`)

### Mapper
- `[Aggregate]ApiMapper` (예: `OrderApiMapper`, `PaymentApiMapper`)

### URL 패턴 (RESTful)
- **Collection**: `/api/[context]` (복수형) - 예: `/api/orders`
- **Item**: `/api/[context]/{id}` (복수형) - 예: `/api/orders/123`
- **Sub-Resource**: `/api/[context]/{id}/[sub-resource]` - 예: `/api/orders/123/items`

---

## HTTP 메서드 매핑

### Command (상태 변경)
| HTTP 메서드 | 용도 | 예시 URL | Controller |
|-------------|------|----------|------------|
| `POST` | 생성 | `/api/orders` | `createOrder()` |
| `PUT` | 전체 수정 | `/api/orders/{id}` | `updateOrder()` |
| `PATCH` | 부분 수정 | `/api/orders/{id}/status` | `updateOrderStatus()` |
| `DELETE` | 삭제 | `/api/orders/{id}` | `deleteOrder()` |

### Query (조회)
| HTTP 메서드 | 용도 | 예시 URL | Controller |
|-------------|------|----------|------------|
| `GET` | 단건 조회 | `/api/orders/{id}` | `getOrder()` |
| `GET` | 목록 조회 | `/api/orders` | `getOrders()` |
| `GET` | 검색 | `/api/orders?status=PENDING` | `searchOrders()` |

---

## Do / Don't

### ✅ Do

- Controller에서 UseCase 인터페이스만 의존
- Mapper로 API DTO ↔ UseCase DTO 변환
- CQRS 분리 (Command/Query Controller)
- Bean Validation (`@Valid`) 사용
- HTTP 상태 코드 명확히 설정 (`ResponseEntity`)
- RESTful URL 설계 (명사 기반, 복수형)
- 에러 응답 표준화 (`ErrorResponse`)
- Bounded Context별 패키지 분리

### ❌ Don't

- Controller에서 Domain 객체 직접 사용 금지
- Controller에서 비즈니스 로직 구현 금지
- Entity를 API Response로 직접 반환 금지
- Application Service 직접 호출 금지 (UseCase 인터페이스 사용)
- Mapper에 비즈니스 로직 포함 금지
- RPC 스타일 URL 사용 금지 (`/createOrder` ❌)
- 동사 기반 URL 사용 금지

---

## CQRS 명명 규칙

### Command Side (상태 변경)
| 타입 | Controller | Request DTO | UseCase |
|------|------------|-------------|---------|
| 생성 | `OrderCommandController` | `CreateOrderRequest` | `CreateOrderUseCase` |
| 수정 | `OrderCommandController` | `UpdateOrderStatusRequest` | `UpdateOrderStatusUseCase` |
| 삭제 | `OrderCommandController` | - | `DeleteOrderUseCase` |

### Query Side (조회)
| 타입 | Controller | Request DTO | UseCase |
|------|------------|-------------|---------|
| 단건 조회 | `OrderQueryController` | - | `GetOrderUseCase` |
| 목록 조회 | `OrderQueryController` | `OrderSearchRequest` | `FindOrdersUseCase` |
| 검색 | `OrderQueryController` | `OrderFilterRequest` | `SearchOrdersUseCase` |

---

## 예외 처리 흐름

### Domain Exception → HTTP Response

```
[Domain Layer]
InsufficientStockException (Domain Exception)
    ↓
[Application Layer]
비즈니스 로직 실행 중 예외 발생
    ↓
[Adapter Layer - GlobalExceptionHandler]
@ExceptionHandler(InsufficientStockException.class)
    ↓
ErrorResponse (API DTO)
    ↓
HTTP 409 Conflict (JSON Response)
```

### Validation Exception → HTTP Response

```
[Adapter Layer - Controller]
@Valid CreateOrderRequest
    ↓ Bean Validation 실패
MethodArgumentNotValidException
    ↓
[Adapter Layer - GlobalExceptionHandler]
@ExceptionHandler(MethodArgumentNotValidException.class)
    ↓
ErrorResponse with FieldErrors
    ↓
HTTP 400 Bad Request (JSON Response)
```

---

## 체크리스트

### 디렉터리 구조
- [ ] Bounded Context별 패키지 분리 (`order/`, `payment/`)
- [ ] `shared/` 공통 모듈 존재
- [ ] `controller/`, `dto/`, `mapper/` 하위 패키지 구성

### Controller
- [ ] `@RestController` 어노테이션
- [ ] RESTful URL 패턴 (`/api/[context]`)
- [ ] CQRS 분리 (Command/Query Controller)
- [ ] UseCase 인터페이스만 의존
- [ ] 비즈니스 로직 없음 (UseCase로 위임)

### Request DTO
- [ ] Java Record 사용
- [ ] Bean Validation 적용 (`@Valid`, `@NotNull`)
- [ ] Compact Constructor 검증
- [ ] Immutable (불변)

### Response DTO
- [ ] Java Record 사용
- [ ] Entity 직접 노출하지 않음
- [ ] Jackson 어노테이션 최소화

### Mapper
- [ ] API Request → UseCase.Command 변환
- [ ] UseCase.Response → API Response 변환
- [ ] Stateless (상태 없음)
- [ ] 비즈니스 로직 없음 (단순 매핑만)

### 예외 처리
- [ ] `GlobalExceptionHandler` 존재
- [ ] Domain Exception 처리
- [ ] Validation Exception 처리
- [ ] 표준화된 `ErrorResponse`

---

## 관련 문서

### Application Layer
- [Application Package Guide](../../03-application-layer/package-guide/01_application_package_guide.md)
- [Command UseCase](../../03-application-layer/usecase-design/01_command-usecase.md)
- [Query UseCase](../../03-application-layer/usecase-design/02_query-usecase.md)
- [Assembler Responsibility](../../03-application-layer/assembler-pattern/01_assembler-responsibility.md)

### Domain Layer
- [Domain Package Guide](../../02-domain-layer/package-guide/01_domain_package_guide.md)

### Controller Design (다음 단계)
- [RESTful API Design](../controller-design/01_restful-api-design.md)
- [Request Validation](../controller-design/02_request-validation.md)
- [Response Handling](../controller-design/03_response-handling.md)

### DTO Patterns (다음 단계)
- [API Request DTO](../dto-patterns/01_api-request-dto.md)
- [API Response DTO](../dto-patterns/02_api-response-dto.md)
- [Error Response](../dto-patterns/03_error-response.md)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
