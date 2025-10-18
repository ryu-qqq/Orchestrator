# REST Controller 생성 커맨드

당신은 Spring REST API Controller를 생성하는 전문가입니다.

## 🎯 컨텍스트 주입 (자동)

---

## 🎯 ADAPTER-REST 레이어 규칙 (자동 주입됨)

### ❌ 금지 규칙 (Zero-Tolerance)

- **Domain 객체 직접 노출 금지**: API는 Request/Response DTO만 사용
- **UseCase 호출 외 비즈니스 로직 금지**: Controller는 순수 Adapter 역할
- **Lombok 금지**: Plain Java 사용
- **@RestController 누락 금지**: REST API는 반드시 @RestController 사용
- **HTTP 상태 코드 오류**: 적절한 HTTP 상태 코드 사용 (200, 201, 400, 404, 500 등)

### ✅ 필수 규칙

- **Request/Response DTO**: API 전용 DTO 사용
- **@Valid 유효성 검증**: Input DTO는 @Valid로 검증
- **ErrorResponse 표준화**: 예외는 GlobalExceptionHandler에서 처리
- **Javadoc 필수**: `@author`, `@since` 포함
- **RESTful 설계**: HTTP Method + Resource 명사 사용

### 📋 상세 문서

- [RESTful API Design](docs/coding_convention/01-adapter-rest-api-layer/controller-design/01_restful-api-design.md)
- [Request Validation](docs/coding_convention/01-adapter-rest-api-layer/controller-design/02_request-validation.md)
- [API DTO Patterns](docs/coding_convention/01-adapter-rest-api-layer/dto-patterns/01_api-request-dto.md)
- [Global Exception Handler](docs/coding_convention/01-adapter-rest-api-layer/exception-handling/01_global-exception-handler.md)

**이 규칙들은 실시간으로 검증됩니다.**

---

## 📋 작업 지시

### 1. 입력 분석

- **Resource 이름**: 첫 번째 인자 (예: `Order`, `Payment`, `User`)
- **PRD 파일** (선택): 두 번째 인자로 PRD 문서 경로

### 2. 생성할 파일

다음 파일을 `adapter/in/web/src/main/java/com/company/template/adapter/in/web/` 경로에 생성:

```
adapter/in/web/src/main/java/com/company/template/adapter/in/web/
├── controller/
│   └── {Resource}Controller.java
├── dto/
│   ├── {Resource}CreateRequest.java
│   ├── {Resource}Response.java
│   └── ErrorResponse.java
└── mapper/
    └── {Resource}ApiMapper.java
```

### 3. 필수 준수 규칙

#### Controller 패턴

```java
package com.company.template.adapter.in.web.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * {Resource} REST API Controller
 *
 * <p>{간단한 설명}</p>
 *
 * <p><strong>책임:</strong></p>
 * <ul>
 *   <li>✅ HTTP 요청/응답 처리</li>
 *   <li>✅ DTO 변환 및 유효성 검증</li>
 *   <li>❌ 비즈니스 로직 포함 금지</li>
 * </ul>
 *
 * @author Claude
 * @since {현재 날짜}
 */
@RestController
@RequestMapping("/api/v1/{resources}")
public class {Resource}Controller {

    private final {UseCase}UseCase useCase;
    private final {Resource}ApiMapper mapper;

    /**
     * 생성자
     *
     * @param useCase {UseCase} UseCase
     * @param mapper API Mapper
     * @author Claude
     * @since {현재 날짜}
     */
    public {Resource}Controller(
        {UseCase}UseCase useCase,
        {Resource}ApiMapper mapper
    ) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    /**
     * {Resource} 생성 API
     *
     * <p>POST /api/v1/{resources}</p>
     *
     * @param request 생성 요청 DTO
     * @return 생성된 {Resource} 응답
     * @author Claude
     * @since {현재 날짜}
     */
    @PostMapping
    public ResponseEntity<{Resource}Response> create{Resource}(
        @Valid @RequestBody {Resource}CreateRequest request
    ) {
        // 1. API Request → UseCase Command 변환
        {UseCase}Command command = mapper.toCommand(request);

        // 2. UseCase 실행
        {UseCase}Result result = useCase.execute(command);

        // 3. UseCase Result → API Response 변환
        {Resource}Response response = mapper.toResponse(result);

        // 4. HTTP 201 Created 반환
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    /**
     * {Resource} 조회 API
     *
     * <p>GET /api/v1/{resources}/{id}</p>
     *
     * @param id {Resource} ID
     * @return {Resource} 응답
     * @author Claude
     * @since {현재 날짜}
     */
    @GetMapping("/{id}")
    public ResponseEntity<{Resource}Response> get{Resource}(
        @PathVariable String id
    ) {
        {UseCase}Query query = new {UseCase}Query(id);
        {UseCase}Result result = queryUseCase.execute(query);
        {Resource}Response response = mapper.toResponse(result);

        return ResponseEntity.ok(response);
    }
}
```

#### Request DTO (record with validation)

```java
package com.company.template.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {Resource} 생성 요청 DTO
 *
 * @param customerId 고객 ID
 * @param amount 금액
 * @author Claude
 * @since {현재 날짜}
 */
public record {Resource}CreateRequest(
    @NotBlank(message = "고객 ID는 필수입니다")
    String customerId,

    @NotNull(message = "금액은 필수입니다")
    java.math.BigDecimal amount
) {}
```

#### Response DTO (record)

```java
package com.company.template.adapter.in.web.dto;

/**
 * {Resource} 응답 DTO
 *
 * @param id {Resource} ID
 * @param status 상태
 * @param createdAt 생성 시각
 * @author Claude
 * @since {현재 날짜}
 */
public record {Resource}Response(
    String id,
    String status,
    String createdAt
) {}
```

#### API Mapper

```java
package com.company.template.adapter.in.web.mapper;

import org.springframework.stereotype.Component;

/**
 * {Resource} API Mapper
 *
 * <p>API Request/Response ↔ UseCase Command/Result 변환</p>
 *
 * @author Claude
 * @since {현재 날짜}
 */
@Component
public class {Resource}ApiMapper {

    /**
     * API Request → UseCase Command 변환
     *
     * @param request API Request
     * @return UseCase Command
     * @author Claude
     * @since {현재 날짜}
     */
    public {UseCase}Command toCommand({Resource}CreateRequest request) {
        return new {UseCase}Command(
            generateId(),  // ID 생성 로직
            request.customerId()
        );
    }

    /**
     * UseCase Result → API Response 변환
     *
     * @param result UseCase Result
     * @return API Response
     * @author Claude
     * @since {현재 날짜}
     */
    public {Resource}Response toResponse({UseCase}Result result) {
        return new {Resource}Response(
            result.aggregateId(),
            result.status(),
            java.time.LocalDateTime.now().toString()
        );
    }

    private String generateId() {
        return java.util.UUID.randomUUID().toString();
    }
}
```

#### GlobalExceptionHandler

```java
package com.company.template.adapter.in.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global Exception Handler
 *
 * <p>모든 Controller 예외를 처리</p>
 *
 * @author Claude
 * @since {현재 날짜}
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * IllegalArgumentException 처리
     *
     * @param ex 예외
     * @return ErrorResponse
     * @author Claude
     * @since {현재 날짜}
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex
    ) {
        ErrorResponse error = new ErrorResponse(
            "INVALID_REQUEST",
            ex.getMessage()
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }
}
```

### 4. 생성 체크리스트

- [ ] **@RestController**: Controller 클래스에 반드시 포함
- [ ] **DTO 사용**: Domain 객체 직접 노출 금지
- [ ] **@Valid 검증**: Input DTO에 유효성 검증
- [ ] **HTTP 상태 코드**: 201 (Created), 200 (OK), 400 (Bad Request) 등
- [ ] **Mapper 분리**: API ↔ UseCase 변환 로직 분리
- [ ] **Lombok 미사용**: Plain Java record 사용
- [ ] **Javadoc 완전성**: `@author`, `@since` 포함

## 🚀 실행

PRD를 읽고 API 요구사항을 분석한 후, 위 규칙을 따라 Controller를 생성하세요.
