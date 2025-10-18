# API-to-UseCase Mapper - API DTO와 UseCase DTO 변환

> **목적**: Adapter Layer에서 API DTO와 UseCase DTO 간의 변환을 담당하는 Mapper 패턴
>
> **위치**: `adapter/in/web/mapper/`
>
> **관련 문서**:
> - `dto-patterns/01_api-request-dto.md` (API Request DTO)
> - `dto-patterns/02_api-response-dto.md` (API Response DTO)
> - `02_mapper-responsibility.md` (Mapper vs Assembler)
> - `03-application-layer/assembler-pattern/01_assembler-responsibility.md` (Assembler 역할)
> - `03-application-layer/package-guide/01_application_package_guide.md` (전체 흐름)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. Mapper의 역할

**Adapter Layer에서 API DTO와 UseCase DTO를 변환하는 Stateless 컴포넌트**

```
HTTP Request → API Request DTO → Mapper → UseCase Command → UseCase
UseCase → UseCase Response → Mapper → API Response DTO → HTTP Response
```

**핵심 특성**:
- **위치**: `adapter/in/web/mapper/` (Adapter Layer)
- **책임**: API DTO ↔ UseCase DTO 변환 (단순 매핑만)
- **Stateless**: 상태 없이 순수 변환 함수만 제공
- **Spring Bean**: `@Component`로 등록하여 주입

### 2. Mapper vs Assembler 구분

| 구분 | Mapper (Adapter) | Assembler (Application) |
|------|------------------|-------------------------|
| **위치** | `adapter/in/web/mapper/` | `application/[context]/assembler/` |
| **변환** | API DTO ↔ UseCase DTO | UseCase DTO ↔ Domain |
| **의존성** | API DTO, UseCase DTO | UseCase DTO, Domain |
| **복잡도** | 단순 매핑 | Value Object 변환, 조립 로직 |
| **예제** | `OrderApiMapper` | `OrderAssembler` |

**중요**: Mapper와 Assembler는 **역할이 다릅니다**!
- **Mapper**: Adapter Layer에서 API와 UseCase 간 변환
- **Assembler**: Application Layer에서 UseCase와 Domain 간 변환

---

## 🔄 전체 데이터 변환 흐름

```
[Adapter Layer - Controller]
API Request (CreateOrderRequest)
    ↓
[Adapter Layer - Mapper]  ← 이 문서의 주제
    ↓ toCommand()
UseCase Command (CreateOrderUseCase.Command)
    ↓
[Application Layer - Service]
    ↓
[Application Layer - Assembler]
    ↓ toDomain()
Domain Object (Order)
    ↓
[Domain Layer - Business Logic]
    ↓
Domain Result (Order)
    ↓
[Application Layer - Assembler]
    ↓ toResponse()
UseCase Response (CreateOrderUseCase.Response)
    ↓
[Adapter Layer - Mapper]  ← 이 문서의 주제
    ↓ toApiResponse()
API Response (OrderApiResponse)
    ↓
[Adapter Layer - Controller]
```

---

## ❌ Anti-Pattern: 잘못된 Mapper 설계

### 문제 1: Mapper에 비즈니스 로직 포함

```java
// ❌ Bad: Mapper에 비즈니스 로직
@Component
public class OrderApiMapper {

    public CreateOrderUseCase.Command toCommand(CreateOrderRequest request) {
        // ❌ 할인 계산 로직 (Domain으로 이동)
        BigDecimal totalAmount = request.items().stream()
            .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ❌ 할인 적용 (비즈니스 규칙)
        if (totalAmount.compareTo(BigDecimal.valueOf(100000)) > 0) {
            totalAmount = totalAmount.multiply(BigDecimal.valueOf(0.9));
        }

        return new CreateOrderUseCase.Command(
            request.customerId(),
            mapItems(request.items()),
            totalAmount  // ❌ Mapper에서 계산한 금액
        );
    }
}
```

**문제점**:
- 🔴 Mapper가 비즈니스 규칙 포함 (SRP 위반)
- 🔴 재사용 불가능한 로직
- 🔴 Domain Layer의 책임 침범

### 문제 2: Mapper에서 Port 호출 (외부 의존)

```java
// ❌ Bad: Mapper에서 Repository 호출
@Component
public class OrderApiMapper {
    private final LoadCustomerPort loadCustomerPort;  // ❌

    public CreateOrderUseCase.Command toCommand(CreateOrderRequest request) {
        // ❌ Mapper에서 외부 의존 호출
        Customer customer = loadCustomerPort.load(request.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));

        return new CreateOrderUseCase.Command(
            customer.getId().value(),
            mapItems(request.items())
        );
    }
}
```

**문제점**:
- 🔴 Mapper가 외부 의존성 호출
- 🔴 단순 매핑 책임 초과
- 🔴 Service Layer의 책임 침범

### 문제 3: Assembler와 Mapper 혼동

```java
// ❌ Bad: Mapper가 Domain 직접 다룸
@Component
public class OrderApiMapper {

    public Order toDomain(CreateOrderRequest request) {  // ❌ Domain 변환
        return Order.create(
            CustomerId.of(request.customerId()),
            request.items().stream()
                .map(item -> OrderLineItem.create(...))
                .toList()
        );
    }
}
```

**문제점**:
- 🔴 Mapper가 Domain 변환 (Assembler 책임)
- 🔴 계층 경계 위반
- 🔴 Adapter Layer가 Domain 직접 다룸

---

## ✅ Best Practice: Stateless Mapper

### 패턴 1: 기본 Mapper 구조

```java
package com.company.adapter.in.web.mapper;

import com.company.adapter.in.web.dto.CreateOrderRequest;
import com.company.adapter.in.web.dto.OrderApiResponse;
import com.company.application.port.in.CreateOrderUseCase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Order API Mapper
 *
 * <p>Adapter Layer에서 API DTO와 UseCase DTO를 변환합니다.
 *
 * <p>변환 방향:
 * <ul>
 *   <li>API Request → UseCase Command (toCommand)</li>
 *   <li>UseCase Response → API Response (toApiResponse)</li>
 * </ul>
 *
 * <p>책임:
 * <ul>
 *   <li>단순 필드 매핑만 수행</li>
 *   <li>비즈니스 로직 포함 금지</li>
 *   <li>외부 의존성 호출 금지</li>
 *   <li>Stateless (상태 없음)</li>
 * </ul>
 *
 * @author Development Team
 * @since 1.0.0
 */
@Component
public class OrderApiMapper {

    /**
     * API Request → UseCase Command 변환
     *
     * @param request API Request DTO
     * @return UseCase Command
     */
    public CreateOrderUseCase.Command toCommand(CreateOrderRequest request) {
        List<CreateOrderUseCase.Command.OrderItem> items = request.items().stream()
            .map(this::toCommandItem)
            .toList();

        return new CreateOrderUseCase.Command(
            request.customerId(),
            items,
            request.notes()
        );
    }

    /**
     * OrderItemRequest → Command.OrderItem 변환
     */
    private CreateOrderUseCase.Command.OrderItem toCommandItem(
            CreateOrderRequest.OrderItemRequest item) {
        return new CreateOrderUseCase.Command.OrderItem(
            item.productId(),
            item.quantity()
        );
    }

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

**핵심 요소**:
- ✅ **@Component**: Spring Bean으로 등록
- ✅ **Stateless**: 인스턴스 변수 없음
- ✅ **단순 매핑**: 필드 변환만 수행
- ✅ **Private 헬퍼 메서드**: 중첩 DTO 변환 로직 분리
- ✅ **Javadoc**: 변환 방향 및 책임 명시

---

### 패턴 2: Controller에서 사용

```java
package com.company.adapter.in.web;

import com.company.adapter.in.web.dto.CreateOrderRequest;
import com.company.adapter.in.web.dto.OrderApiResponse;
import com.company.adapter.in.web.mapper.OrderApiMapper;
import com.company.application.port.in.CreateOrderUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Order REST API Controller
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderApiMapper orderApiMapper;  // ✅ Mapper 주입

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            OrderApiMapper orderApiMapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.orderApiMapper = orderApiMapper;
    }

    /**
     * 주문 생성
     *
     * <p>변환 흐름:
     * <pre>
     * CreateOrderRequest → Mapper → CreateOrderUseCase.Command
     *                            ↓
     *                  UseCase 실행
     *                            ↓
     * CreateOrderUseCase.Response → Mapper → OrderApiResponse
     * </pre>
     */
    @PostMapping
    public ResponseEntity<OrderApiResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        // ✅ 1. API Request → UseCase Command 변환
        CreateOrderUseCase.Command command = orderApiMapper.toCommand(request);

        // ✅ 2. UseCase 실행
        CreateOrderUseCase.Response response = createOrderUseCase.createOrder(command);

        // ✅ 3. UseCase Response → API Response 변환
        OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", "/api/v1/orders/" + apiResponse.orderId())
            .body(apiResponse);
    }
}
```

---

## 🎯 실전 예제: 다양한 Mapper 패턴

### 시나리오 1: Update Request Mapper

```java
/**
 * Order API Mapper - Update 관련 변환
 */
@Component
public class OrderApiMapper {

    /**
     * Update Request → Command 변환
     */
    public UpdateOrderUseCase.Command toUpdateCommand(
            Long orderId,
            UpdateOrderRequest request) {

        return new UpdateOrderUseCase.Command(
            orderId,
            request.notes(),
            request.status()
        );
    }

    /**
     * Update Response → API Response 변환
     */
    public OrderApiResponse toApiResponse(UpdateOrderUseCase.Response response) {
        return new OrderApiResponse(
            response.orderId(),
            response.status(),
            response.totalAmount().toString(),
            response.updatedAt()
        );
    }
}
```

**Controller 사용**:
```java
@PatchMapping("/{orderId}")
public ResponseEntity<OrderApiResponse> updateOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody UpdateOrderRequest request) {

    UpdateOrderUseCase.Command command = orderApiMapper.toUpdateCommand(orderId, request);
    UpdateOrderUseCase.Response response = updateOrderUseCase.updateOrder(command);
    OrderApiResponse apiResponse = orderApiMapper.toApiResponse(response);

    return ResponseEntity.ok(apiResponse);
}
```

---

### 시나리오 2: Query Parameters Mapper

```java
/**
 * Order API Mapper - Query 관련 변환
 */
@Component
public class OrderApiMapper {

    /**
     * Search Request + Pageable → Query 변환
     */
    public GetOrdersQuery.Query toQuery(
            OrderSearchRequest searchRequest,
            Pageable pageable) {

        return new GetOrdersQuery.Query(
            searchRequest.customerId(),
            searchRequest.status(),
            searchRequest.startDate(),
            searchRequest.endDate(),
            pageable.getPageNumber(),
            pageable.getPageSize(),
            mapSort(pageable.getSort())
        );
    }

    /**
     * Spring Sort → Query Sort 변환
     */
    private List<GetOrdersQuery.Query.SortField> mapSort(Sort sort) {
        return sort.stream()
            .map(order -> new GetOrdersQuery.Query.SortField(
                order.getProperty(),
                order.getDirection().name()
            ))
            .toList();
    }

    /**
     * Query Response → Page<API Response> 변환
     */
    public PageResponse<OrderSummaryApiResponse> toPageResponse(
            GetOrdersQuery.Response response) {

        List<OrderSummaryApiResponse> content = response.orders().stream()
            .map(this::toSummaryApiResponse)
            .toList();

        return new PageResponse<>(
            content,
            response.pageNumber(),
            response.pageSize(),
            response.totalElements(),
            response.totalPages(),
            response.isFirst(),
            response.isLast()
        );
    }

    /**
     * Order Summary → Summary API Response 변환
     */
    private OrderSummaryApiResponse toSummaryApiResponse(
            GetOrdersQuery.Response.OrderSummary summary) {

        return new OrderSummaryApiResponse(
            summary.orderId(),
            summary.status(),
            summary.totalAmount().toString(),
            summary.createdAt()
        );
    }
}
```

---

### 시나리오 3: 상세 조회 Mapper (중첩 DTO)

```java
/**
 * Order API Mapper - 상세 조회 변환
 */
@Component
public class OrderApiMapper {

    /**
     * Query Response → Detail API Response 변환
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
            .map(this::toItemApiResponse)
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

    /**
     * Query Item → Item API Response 변환
     */
    private OrderDetailApiResponse.OrderItemApiResponse toItemApiResponse(
            GetOrderQuery.Response.OrderItem item) {

        return new OrderDetailApiResponse.OrderItemApiResponse(
            item.productId(),
            item.productName(),
            item.quantity(),
            item.unitPrice().toString(),
            item.subtotal().toString()
        );
    }
}
```

---

### 시나리오 4: Batch Operation Mapper

```java
/**
 * Order API Mapper - Batch 작업 변환
 */
@Component
public class OrderApiMapper {

    /**
     * Batch Request → Command 변환
     */
    public CancelOrdersUseCase.Command toBatchCommand(BatchCancelOrdersRequest request) {
        return new CancelOrdersUseCase.Command(
            request.orderIds(),
            request.reason()
        );
    }

    /**
     * Batch Response → API Response 변환
     */
    public BatchOperationApiResponse toBatchResponse(CancelOrdersUseCase.Response response) {

        List<BatchOperationApiResponse.FailureDetail> failures = response.failures().stream()
            .map(failure -> new BatchOperationApiResponse.FailureDetail(
                failure.orderId(),
                failure.errorCode(),
                failure.errorMessage()
            ))
            .toList();

        return new BatchOperationApiResponse(
            response.totalCount(),
            response.successCount(),
            response.failureCount(),
            failures
        );
    }
}
```

---

## 🔧 고급 패턴

### 패턴 1: Type Conversion Helper

```java
/**
 * Order API Mapper with Type Conversion
 */
@Component
public class OrderApiMapper {

    /**
     * BigDecimal → String 변환 헬퍼
     */
    private String toAmountString(BigDecimal amount) {
        return amount != null ? amount.toString() : "0";
    }

    /**
     * LocalDateTime → String 변환 헬퍼 (ISO-8601)
     */
    private String toDateTimeString(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }

    /**
     * UseCase Response → API Response 변환
     */
    public OrderApiResponse toApiResponse(CreateOrderUseCase.Response response) {
        return new OrderApiResponse(
            response.orderId(),
            response.status(),
            toAmountString(response.totalAmount()),  // ✅ 헬퍼 사용
            response.createdAt()
        );
    }
}
```

### 패턴 2: Null-Safe Mapping

```java
/**
 * Order API Mapper with Null Safety
 */
@Component
public class OrderApiMapper {

    /**
     * Null-Safe List 변환
     */
    private <S, T> List<T> mapList(List<S> source, Function<S, T> mapper) {
        if (source == null || source.isEmpty()) {
            return List.of();  // ✅ Empty List 반환
        }
        return source.stream()
            .map(mapper)
            .toList();
    }

    /**
     * Query Response → API Response 변환
     */
    public OrderDetailApiResponse toDetailApiResponse(GetOrderQuery.Response response) {

        List<OrderDetailApiResponse.OrderItemApiResponse> items = mapList(
            response.items(),
            this::toItemApiResponse  // ✅ Null-Safe
        );

        return new OrderDetailApiResponse(
            response.orderId(),
            response.status(),
            response.totalAmount().toString(),
            toCustomerInfo(response),  // ✅ Null-Safe 헬퍼
            items,
            response.createdAt()
        );
    }

    private OrderDetailApiResponse.CustomerInfo toCustomerInfo(GetOrderQuery.Response response) {
        if (response.customerId() == null) {
            return null;  // ✅ Null 반환 (JsonInclude로 제외)
        }

        return new OrderDetailApiResponse.CustomerInfo(
            response.customerId(),
            response.customerName(),
            response.customerEmail()
        );
    }
}
```

---

## 🚫 Mapper 책임 범위 (Do / Don't)

### Do ✅

- **단순 필드 매핑**: API DTO ↔ UseCase DTO
- **타입 변환**: `BigDecimal` → `String`, `LocalDateTime` → `String`
- **중첩 DTO 변환**: List, Nested Record 매핑
- **Null Safety**: null 체크 및 기본값 설정
- **Stateless**: 인스턴스 변수 없이 순수 함수만

### Don't ❌

- **비즈니스 로직**: 할인 계산, 재고 확인 등
- **외부 의존 호출**: Repository, Port, Service 호출
- **Domain 객체 다루기**: Domain 변환은 Assembler의 책임
- **Validation**: Bean Validation은 Controller에서
- **상태 유지**: Stateful 구현 금지

---

## 📋 Mapper 설계 체크리스트

### 기본 구조
- [ ] `adapter/in/web/mapper/` 패키지에 위치하는가?
- [ ] `@Component`로 Spring Bean 등록되어 있는가?
- [ ] `~ApiMapper` 네이밍 규칙을 따르는가?
- [ ] Stateless인가? (인스턴스 변수 없음)
- [ ] Javadoc이 작성되어 있는가?

### 변환 메서드
- [ ] `toCommand()` 메서드가 API Request → UseCase Command 변환하는가?
- [ ] `toApiResponse()` 메서드가 UseCase Response → API Response 변환하는가?
- [ ] Private 헬퍼 메서드로 중첩 DTO 변환 로직을 분리했는가?

### 책임 분리
- [ ] 단순 필드 매핑만 수행하는가?
- [ ] 비즈니스 로직이 없는가?
- [ ] 외부 의존성(Port, Repository)을 호출하지 않는가?
- [ ] Domain 객체를 직접 다루지 않는가? (Assembler 책임)

### Null Safety
- [ ] null 값에 대한 방어 로직이 있는가?
- [ ] Optional 대신 null 체크를 사용하는가?
- [ ] Empty List를 `List.of()`로 반환하는가?

### Controller 통합
- [ ] Controller에서 Mapper를 주입받는가?
- [ ] Controller가 Mapper를 통해 변환하는가?
- [ ] Controller가 직접 변환 로직을 포함하지 않는가?

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
