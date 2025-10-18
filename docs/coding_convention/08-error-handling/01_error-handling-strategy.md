# Error Handling Strategy - 에러 핸들링 전략

> **목적**: 멀티모듈 + 헥사고날 아키텍처 기반 계층화된 에러 핸들링 전략
>
> **위치**: Domain Layer (예외 정의) → Adapter Layer (예외 처리)
>
> **관련 문서**:
> - `02_domain-exception-design.md` (Domain Layer 예외 설계)
> - `03_global-exception-handler.md` (Adapter Layer 예외 처리)
> - `04_error-response-format.md` (API 에러 응답 포맷)
> - `05_errorcode-management.md` (ErrorCode 관리 전략)
>
> **필수 버전**: Spring Boot 3.0+, Java 21+

---

## 📌 핵심 원칙

### 1. 계층화된 예외 처리

```
┌─────────────────────────────────────────────────┐
│ Adapter Layer (REST API)                        │
│  - GlobalExceptionHandler                       │
│  - ErrorResponse (API 응답)                     │
│  - HTTP 상태 코드 매핑                          │
└─────────────────────────────────────────────────┘
                    ↑ 예외 변환
┌─────────────────────────────────────────────────┐
│ Application Layer                                │
│  - UseCase 구현                                  │
│  - 트랜잭션 경계                                 │
│  - Domain Exception 전파                         │
└─────────────────────────────────────────────────┘
                    ↑ 예외 발생
┌─────────────────────────────────────────────────┐
│ Domain Layer                                     │
│  - BusinessException (abstract)                  │
│  - ErrorCode (interface)                         │
│  - Domain-specific Exceptions                    │
└─────────────────────────────────────────────────┘
```

### 2. Bounded Context별 ErrorCode 분리 (DDD 원칙)

**❌ 안티패턴: 단일 거대 ErrorCode**
```java
// ❌ Bad: 모든 도메인을 하나의 enum에
public enum ErrorCode {
    ORDER_NOT_FOUND, CUSTOMER_NOT_FOUND, PAYMENT_FAILED, ...
    // 수백 개의 에러 코드가 한 파일에 집중
    // - Bounded Context 경계 무시
    // - 팀 간 머지 충돌 빈번
    // - 모듈 독립성 파괴
}
```

**✅ 권장 패턴: Context별 ErrorCode 분리**
```java
// ✅ Good: Bounded Context별 ErrorCode
domain/
├─ shared/exception/
│  ├─ ErrorCode.java (interface)
│  └─ CommonErrorCode.java (공통 에러만)
│
├─ order/exception/
│  └─ OrderErrorCode.java (Order 전용)
│
├─ customer/exception/
│  └─ CustomerErrorCode.java (Customer 전용)
│
└─ payment/exception/
   └─ PaymentErrorCode.java (Payment 전용)
```

**장점**:
- ✅ DDD Bounded Context 원칙 준수
- ✅ 모듈 독립성 유지 (마이크로서비스 전환 용이)
- ✅ 팀별 독립 개발 가능 (머지 충돌 최소화)
- ✅ 코드 가독성 향상 (각 enum 50줄 내외)

---

## 🎯 3계층 에러 핸들링 전략

### 1단계: Domain Layer - 비즈니스 예외 정의

**역할**: 비즈니스 규칙 위반을 도메인 예외로 표현

```java
package com.company.domain.shared.exception;

/**
 * ErrorCode 공통 인터페이스
 *
 * @author Development Team
 * @since 1.0.0
 */
public interface ErrorCode {
    String getCode();
    int getHttpStatus();
    String getMessage();
}
```

```java
package com.company.domain.shared.exception;

/**
 * 비즈니스 예외 추상 클래스
 *
 * @author Development Team
 * @since 1.0.0
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

```java
package com.company.domain.order.exception;

/**
 * Order Domain 예외
 *
 * @author Development Team
 * @since 1.0.0
 */
public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(Long orderId) {
        super(OrderErrorCode.ORDER_NOT_FOUND,
              "Order not found: " + orderId);
    }
}

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super(OrderErrorCode.INSUFFICIENT_STOCK,
              String.format("Insufficient stock for product %d: requested=%d, available=%d",
                          productId, requested, available));
    }
}
```

### 2단계: Application Layer - 예외 전파

**역할**: Domain 예외를 그대로 전파, 트랜잭션 롤백

```java
package com.company.application.order.service;

import com.company.domain.order.exception.OrderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create Order UseCase 구현체
 *
 * @author Development Team
 * @since 1.0.0
 */
@Service
public class CreateOrderService implements CreateOrderUseCase {

    private final LoadOrderPort loadOrderPort;

    @Override
    @Transactional
    public Response createOrder(Command command) {

        // Domain 예외가 발생하면 그대로 전파
        Order order = loadOrderPort.findById(command.orderId())
            .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        // 비즈니스 로직 실행 (Domain 예외 발생 가능)
        order.confirm();

        return Response.from(order);
    }
}
```

### 3단계: Adapter Layer - 예외 처리 및 HTTP 응답 변환

**역할**: Domain 예외를 HTTP 응답으로 변환

```java
package com.company.adapter.in.web.exception;

import com.company.domain.shared.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 전역 예외 핸들러
 *
 * @author Development Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * BusinessException 처리
     * - Domain Layer에서 발생한 모든 비즈니스 예외 처리
     * - ErrorCode 인터페이스를 통해 HTTP 상태 코드 및 메시지 추출
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        ErrorCode errorCode = ex.getErrorCode();

        ErrorResponse response = ErrorResponse.of(
            errorCode.getCode(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    /**
     * Validation 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new FieldError(
                error.getField(),
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
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    /**
     * 예상치 못한 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse response = ErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred",
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
}
```

---

## 🏗️ 멀티모듈 구조에서의 예외 배치

### 권장 디렉터리 구조

```
domain/
├─ shared/
│  └─ exception/
│     ├─ ErrorCode.java           # ✅ 공통 인터페이스
│     ├─ BusinessException.java   # ✅ 추상 예외
│     └─ CommonErrorCode.java     # ✅ 공통 에러 (UNAUTHORIZED, FORBIDDEN 등)
│
├─ order/
│  └─ exception/
│     ├─ OrderErrorCode.java      # ✅ Order 전용 ErrorCode
│     ├─ OrderNotFoundException.java
│     ├─ InsufficientStockException.java
│     └─ InvalidOrderStatusException.java
│
├─ customer/
│  └─ exception/
│     ├─ CustomerErrorCode.java   # ✅ Customer 전용 ErrorCode
│     ├─ CustomerNotFoundException.java
│     └─ DuplicateEmailException.java
│
└─ payment/
   └─ exception/
      ├─ PaymentErrorCode.java    # ✅ Payment 전용 ErrorCode
      ├─ PaymentFailedException.java
      └─ InvalidCardException.java

application/
└─ (UseCase 구현체 - 예외 전파만 수행)

adapter/
└─ in/web/
   └─ exception/
      ├─ GlobalExceptionHandler.java  # ✅ 전역 예외 핸들러
      └─ ErrorResponse.java           # ✅ API 에러 응답 DTO
```

---

## 🔍 예외 처리 흐름 예시

### 시나리오: 주문 조회 실패

```
1. Controller
   ↓ GET /api/v1/orders/999

2. UseCase
   ↓ loadOrderPort.findById(999)
   ↓ Optional.empty()
   ↓ throw new OrderNotFoundException(999)

3. GlobalExceptionHandler
   ↓ @ExceptionHandler(BusinessException.class)
   ↓ errorCode = OrderErrorCode.ORDER_NOT_FOUND
   ↓ httpStatus = 404

4. HTTP Response
   ↓ 404 Not Found
   {
     "code": "ORDER-001",
     "message": "Order not found: 999",
     "timestamp": "2025-01-17T10:30:00",
     "path": "/api/v1/orders/999"
   }
```

---

## 📋 에러 핸들링 체크리스트

### Domain Layer
- [ ] `ErrorCode` 인터페이스 정의했는가?
- [ ] Bounded Context별로 ErrorCode enum을 분리했는가?
- [ ] `BusinessException` 추상 클래스를 상속하는가?
- [ ] Domain 예외는 비즈니스 규칙 위반만 표현하는가?
- [ ] Framework 의존성이 없는가? (`org.springframework.*` 금지)

### Application Layer
- [ ] Domain 예외를 그대로 전파하는가?
- [ ] 불필요한 예외 변환을 하지 않는가?
- [ ] `@Transactional` 메서드에서 예외 발생 시 롤백되는가?

### Adapter Layer
- [ ] `GlobalExceptionHandler`를 사용하는가?
- [ ] `@RestControllerAdvice`를 적용했는가?
- [ ] `ErrorCode` 인터페이스에만 의존하는가?
- [ ] 적절한 HTTP 상태 코드를 반환하는가?
- [ ] `ErrorResponse`를 표준화했는가?

---

## 🎯 주요 HTTP 상태 코드 매핑

| 상황 | HTTP 상태 코드 | ErrorCode 예시 |
|------|---------------|---------------|
| 리소스 없음 | 404 Not Found | `ORDER_NOT_FOUND` |
| 검증 실패 | 400 Bad Request | `INVALID_INPUT` |
| 비즈니스 규칙 위반 | 409 Conflict | `INSUFFICIENT_STOCK` |
| 인증 필요 | 401 Unauthorized | `UNAUTHORIZED` |
| 권한 없음 | 403 Forbidden | `FORBIDDEN` |
| 서버 오류 | 500 Internal Server Error | `INTERNAL_ERROR` |

---

**작성자**: Development Team
**최초 작성일**: 2025-01-17
**최종 수정일**: 2025-01-17
**버전**: 1.0.0
