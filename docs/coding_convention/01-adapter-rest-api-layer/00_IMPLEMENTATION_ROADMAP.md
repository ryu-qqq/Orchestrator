# REST API Layer 구현 로드맵

> **목적**: `01-adapter-rest-api-layer/` 코딩 컨벤션 문서 작성 순서 및 가이드
>
> **작성 규칙**: 이 문서는 각 세션마다 참조하여 **일관된 작업 진행**을 보장합니다.

---

## 📋 전체 작업 순서

### Phase 1: 핵심 구조 (우선 작업) ✅ 필수

```
01-adapter-rest-api-layer/
├── package-guide/
│   └── 01_rest_api_package_guide.md       # ✅ 1순위: 전체 패키지 구조
├── controller-design/
│   ├── 01_restful-api-design.md            # ✅ 2순위: RESTful API 설계
│   ├── 02_request-validation.md            # ✅ 3순위: 요청 검증
│   └── 03_response-handling.md             # ✅ 4순위: 응답 처리
└── dto-patterns/
    ├── 01_api-request-dto.md               # ✅ 5순위: Request DTO
    ├── 02_api-response-dto.md              # ✅ 6순위: Response DTO
    └── 03_error-response.md                # ✅ 7순위: 에러 응답
```

### Phase 2: 변환 계층 (중요) 🔄

```
├── mapper-patterns/
│   ├── 01_api-to-usecase-mapper.md         # ✅ 8순위: API→UseCase 변환
│   └── 02_mapper-responsibility.md         # ✅ 9순위: Mapper 역할
└── exception-handling/
    ├── 01_global-exception-handler.md      # ✅ 10순위: 글로벌 예외 처리
    ├── 02_custom-error-codes.md            # ✅ 11순위: 에러 코드
    └── 03_validation-exception.md          # ✅ 12순위: 검증 예외
```

### Phase 3: 보안 & 테스트 (선택적) 🔒

```
├── security/
│   ├── 01_authentication-filter.md         # 🔐 13순위: 인증 필터
│   ├── 02_authorization-annotation.md      # 🔐 14순위: 권한 어노테이션
│   └── 03_cors-configuration.md            # 🔐 15순위: CORS 설정
└── testing/
    ├── 01_controller-unit-test.md          # 🧪 16순위: Controller 단위 테스트
    ├── 02_integration-test.md              # 🧪 17순위: API 통합 테스트
    └── 03_rest-docs.md                     # 🧪 18순위: REST Docs
```

---

## 📌 각 문서별 작성 가이드

### 1️⃣ package-guide/01_rest_api_package_guide.md

**목적**: REST API Layer 전체 패키지 구조 및 역할 정의

**포함 내용**:
- [ ] 디렉터리 구조 (Bounded Context 기반)
- [ ] 각 패키지별 역할 (controller, dto, mapper, exception)
- [ ] 계층 간 데이터 흐름 (API Request → UseCase Command)
- [ ] Adapter Layer vs Application Layer 구분
- [ ] 허용/금지 의존성 규칙
- [ ] 네이밍 컨벤션
- [ ] ArchUnit 검증 규칙

**참조 문서**:
- `03-application-layer/package-guide/01_application_package_guide.md` (구조 참조)
- `04-persistence-layer/package-guide/01_persistence_package_guide.md` (패턴 참조)

**템플릿**: 기존 package-guide 문서 구조 따름

---

### 2️⃣ controller-design/01_restful-api-design.md

**목적**: RESTful API 설계 원칙 및 HTTP 메서드 활용

**포함 내용**:
- [ ] REST 원칙 (Resource 기반, URI 설계)
- [ ] HTTP 메서드 매핑 (GET, POST, PUT, DELETE)
- [ ] 상태 코드 사용 (200, 201, 400, 404, 500)
- [ ] URI 네이밍 규칙 (kebab-case, 복수형)
- [ ] Query Parameter vs Path Variable
- [ ] Pagination, Filtering, Sorting 패턴
- [ ] Anti-Pattern (동사 사용, RPC 스타일)

**참조 문서**:
- `03-application-layer/usecase-design/01_command-usecase.md` (UseCase 연계)

**예제 코드**:
```java
// ✅ Good: Resource 기반
@GetMapping("/orders/{orderId}")
@PutMapping("/orders/{orderId}/status")

// ❌ Bad: 동사 기반 (RPC 스타일)
@PostMapping("/createOrder")
@PostMapping("/updateOrderStatus")
```

---

### 3️⃣ controller-design/02_request-validation.md

**목적**: API 요청 검증 전략 (Bean Validation)

**포함 내용**:
- [ ] `@Valid`, `@Validated` 사용
- [ ] Bean Validation 어노테이션 (`@NotNull`, `@Min`, `@Max`)
- [ ] Compact Constructor 검증 (Java Record)
- [ ] Custom Validator 작성
- [ ] 검증 실패 시 에러 응답 처리
- [ ] 중첩 DTO 검증 (`@Valid` 전파)

**참조 문서**:
- `03-application-layer/dto-patterns/03_dto-validation.md`

**예제 코드**:
```java
public record CreateOrderRequest(
    @NotNull(message = "Customer ID is required")
    Long customerId,

    @NotEmpty(message = "Items cannot be empty")
    @Valid
    List<OrderItemRequest> items
) {
    // Compact Constructor 추가 검증
    public CreateOrderRequest {
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException("Invalid customer ID");
        }
    }
}
```

---

### 4️⃣ controller-design/03_response-handling.md

**목적**: API 응답 처리 및 표준화

**포함 내용**:
- [ ] `ResponseEntity<T>` 사용
- [ ] HTTP 상태 코드 매핑
- [ ] 성공 응답 구조
- [ ] 에러 응답 구조
- [ ] Pagination 응답
- [ ] HATEOAS (선택적)

**참조 문서**:
- `03-application-layer/dto-patterns/01_request-response-dto.md`

**예제 코드**:
```java
@PostMapping
public ResponseEntity<OrderApiResponse> createOrder(
        @Valid @RequestBody OrderApiRequest request) {

    CreateOrderUseCase.Response response = createOrderUseCase.createOrder(
        orderApiMapper.toCommand(request)
    );

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(orderApiMapper.toApiResponse(response));
}
```

---

### 5️⃣ dto-patterns/01_api-request-dto.md

**목적**: API Request DTO 설계 패턴

**포함 내용**:
- [ ] Java Record 사용
- [ ] Bean Validation 적용
- [ ] Compact Constructor 검증
- [ ] 중첩 DTO 구조
- [ ] Jackson 어노테이션 (최소화)
- [ ] Immutability 보장

**참조 문서**:
- `03-application-layer/dto-patterns/01_request-response-dto.md` (전체 흐름)
- `06-java21-patterns/record-patterns/01_dto-with-records.md`

---

### 6️⃣ dto-patterns/02_api-response-dto.md

**목적**: API Response DTO 설계 패턴

**포함 내용**:
- [ ] Java Record 사용
- [ ] `from()` 정적 팩토리 메서드
- [ ] Jackson 직렬화 설정
- [ ] Pagination 응답
- [ ] Entity 직접 노출 금지
- [ ] Lazy Loading 방지

**참조 문서**:
- `03-application-layer/dto-patterns/01_request-response-dto.md`

---

### 7️⃣ dto-patterns/03_error-response.md

**목적**: 에러 응답 표준화

**포함 내용**:
- [ ] ErrorResponse DTO 구조
- [ ] 에러 코드 체계
- [ ] 검증 에러 응답 (FieldError)
- [ ] 비즈니스 예외 응답
- [ ] 시스템 예외 응답
- [ ] Timestamp, TraceId 포함

**참조 문서**:
- `exception-handling/01_global-exception-handler.md` (연계)

**예제 코드**:
```java
public record ErrorResponse(
    String code,
    String message,
    Instant timestamp,
    List<FieldError> fieldErrors
) {
    public static ErrorResponse from(BusinessException ex) {
        return new ErrorResponse(
            ex.getErrorCode().name(),
            ex.getMessage(),
            Instant.now(),
            null
        );
    }
}
```

---

### 8️⃣ mapper-patterns/01_api-to-usecase-mapper.md

**목적**: API DTO → UseCase DTO 변환

**포함 내용**:
- [ ] Mapper 역할 (Adapter Layer)
- [ ] Request → Command 변환
- [ ] Response ← Response 변환
- [ ] Assembler와 구분
- [ ] Stateless 구현
- [ ] 단순 매핑만 (비즈니스 로직 없음)

**참조 문서**:
- `03-application-layer/assembler-pattern/01_assembler-responsibility.md` (차이점)
- `03-application-layer/package-guide/01_application_package_guide.md` (전체 흐름)

**예제 코드**:
```java
@Component
public class OrderApiMapper {

    // ✅ API Request → UseCase Command
    public CreateOrderUseCase.Command toCommand(OrderApiRequest request) {
        return new CreateOrderUseCase.Command(
            request.customerId(),
            request.items().stream()
                .map(item -> new CreateOrderUseCase.Command.OrderItem(
                    item.productId(),
                    item.quantity(),
                    item.unitPrice()
                ))
                .toList(),
            request.notes()
        );
    }

    // ✅ UseCase Response → API Response
    public OrderApiResponse toApiResponse(CreateOrderUseCase.Response response) {
        return new OrderApiResponse(
            response.orderId(),
            response.status(),
            response.totalAmount(),
            response.createdAt()
        );
    }
}
```

---

### 9️⃣ mapper-patterns/02_mapper-responsibility.md

**목적**: Mapper vs Assembler 역할 구분

**포함 내용**:
- [ ] Mapper 역할 (Adapter Layer)
- [ ] Assembler 역할 (Application Layer)
- [ ] 계층별 책임 분리
- [ ] 데이터 흐름 다이어그램
- [ ] Anti-Pattern (역할 혼동)

**참조 문서**:
- `03-application-layer/assembler-pattern/01_assembler-responsibility.md`

**비교 표**:
| 구분 | Mapper (Adapter) | Assembler (Application) |
|------|------------------|-------------------------|
| **위치** | `adapter/in/web/mapper/` | `application/[context]/assembler/` |
| **변환** | API DTO ↔ UseCase DTO | UseCase DTO ↔ Domain |
| **복잡도** | 단순 매핑 | Value Object 변환, 조립 |

---

### 🔟 exception-handling/01_global-exception-handler.md

**목적**: 글로벌 예외 처리 전략

**포함 내용**:
- [ ] `@RestControllerAdvice` 사용
- [ ] `@ExceptionHandler` 메서드
- [ ] BusinessException 처리
- [ ] ValidationException 처리
- [ ] System Exception 처리
- [ ] 로깅 전략

**참조 문서**:
- `dto-patterns/03_error-response.md` (ErrorResponse 연계)

**예제 코드**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        return ResponseEntity
            .status(ex.getErrorCode().getStatus())
            .body(ErrorResponse.from(ex));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.from(ex));
    }
}
```

---

## 🎯 작성 시 일관성 체크리스트

### 문서 구조
- [ ] **헤더**: 제목, 목적, 위치, 관련 문서, 필수 버전 포함
- [ ] **이모지**: 📌 (핵심), ❌ (Anti-Pattern), ✅ (권장), 🎯 (예제)
- [ ] **섹션 순서**: 핵심 원칙 → Anti-Pattern → 권장 패턴 → 체크리스트
- [ ] **코드 예제**: Before/After 형식
- [ ] **체크리스트**: 실무 적용 검증 항목
- [ ] **메타 정보**: 작성자, 날짜, 버전

### 코드 예제
- [ ] Java 21+ 문법 사용 (Record, Pattern Matching)
- [ ] Spring Boot 3.0+ 어노테이션
- [ ] 주석 포함 (`// ✅`, `// ❌`)
- [ ] Javadoc 작성 (`@author`, `@since`)

### 상호 참조
- [ ] 관련 문서 링크 명시
- [ ] Application Layer와 연계 설명
- [ ] Domain Layer 규칙 준수 확인

### ArchUnit 규칙
- [ ] 정적 분석 규칙 스니펫 포함 (필요 시)
- [ ] 금지 패턴 검증 코드

---

## 🔄 세션별 작업 프로세스

### 작업 시작 시
1. ✅ 이 문서 읽기 (`00_IMPLEMENTATION_ROADMAP.md`)
2. ✅ 현재 작업 순서 확인 (Phase 1 → 2 → 3)
3. ✅ 관련 참조 문서 읽기 (Application/Persistence Layer 가이드)

### 작업 진행 시
1. ✅ 문서 템플릿 준수
2. ✅ 기존 패턴과 일관성 유지
3. ✅ 체크리스트 검증

### 작업 완료 시
1. ✅ 문서 품질 검증 (구조, 코드, 링크)
2. ✅ 다음 작업 순서 확인
3. ✅ 이 문서 업데이트 (진행 상황 표시)

---

## 📊 진행 상황 트래킹

### Phase 1: 핵심 구조 (7개 문서) ✅ 완료
- [x] `package-guide/01_rest_api_package_guide.md` ✅ 완료 (2025-10-17)
- [x] `controller-design/01_restful-api-design.md` ✅ 완료 (2025-10-17)
- [x] `controller-design/02_request-validation.md` ✅ 완료 (2025-10-17)
- [x] `controller-design/03_response-handling.md` ✅ 완료 (2025-10-17)
- [x] `dto-patterns/01_api-request-dto.md` ✅ 완료 (2025-10-17)
- [x] `dto-patterns/02_api-response-dto.md` ✅ 완료 (2025-10-17)
- [x] `dto-patterns/03_error-response.md` ✅ 완료 (2025-10-17)

### Phase 2: 변환 계층 (5개 문서) ✅ 완료
- [x] `mapper-patterns/01_api-to-usecase-mapper.md` ✅ 완료 (2025-10-17)
- [x] `mapper-patterns/02_mapper-responsibility.md` ✅ 완료 (2025-10-17)
- [x] `exception-handling/01_global-exception-handler.md` ✅ 완료 (2025-10-17)
- [x] `exception-handling/02_custom-error-codes.md` ✅ 완료 (2025-10-17)
- [x] `exception-handling/03_validation-exception.md` ✅ 완료 (2025-10-17)

### Phase 3: 보안 & 테스트 (6개 문서) 🔄 Testing 완료 (3/6)
- [ ] `security/01_authentication-filter.md`
- [ ] `security/02_authorization-annotation.md`
- [ ] `security/03_cors-configuration.md`
- [x] `testing/01_controller-unit-test.md` ✅ 완료 (2025-10-17)
- [x] `testing/02_integration-test.md` ✅ 완료 (2025-10-17)
- [x] `testing/03_rest-docs.md` ✅ 완료 (2025-10-17)

---

## 🚀 다음 작업

**현재 상태**: Phase 3 Testing 완료! 🎉 (15/18 완료)

**Phase 1 완료된 문서** ✅ (7/7):
1. ✅ `package-guide/01_rest_api_package_guide.md` - 전체 패키지 구조
2. ✅ `controller-design/01_restful-api-design.md` - RESTful API 설계 원칙
3. ✅ `controller-design/02_request-validation.md` - API 요청 검증 전략
4. ✅ `controller-design/03_response-handling.md` - API 응답 처리 및 표준화
5. ✅ `dto-patterns/01_api-request-dto.md` - API Request DTO 설계 패턴
6. ✅ `dto-patterns/02_api-response-dto.md` - API Response DTO 설계 패턴
7. ✅ `dto-patterns/03_error-response.md` - 에러 응답 DTO 표준화

**Phase 2 완료된 문서** ✅ (5/5):
8. ✅ `mapper-patterns/01_api-to-usecase-mapper.md` - API→UseCase 변환
9. ✅ `mapper-patterns/02_mapper-responsibility.md` - Mapper vs Assembler 역할
10. ✅ `exception-handling/01_global-exception-handler.md` - 글로벌 예외 처리
11. ✅ `exception-handling/02_custom-error-codes.md` - 커스텀 에러 코드 설계
12. ✅ `exception-handling/03_validation-exception.md` - Bean Validation 예외 처리

**Phase 3 Testing 완료된 문서** ✅ (3/3):
13. ✅ `testing/01_controller-unit-test.md` - Controller 단위 테스트
14. ✅ `testing/02_integration-test.md` - API 통합 테스트
15. ✅ `testing/03_rest-docs.md` - Spring REST Docs

**다음 단계**: Phase 3 Security (선택적 작업) - 3개 문서 남음
16. `security/01_authentication-filter.md` - 인증 필터 구현
   - Spring Security Filter 설정
   - JWT 토큰 검증
   - SecurityContext 설정
17. `security/02_authorization-annotation.md` - 권한 어노테이션
18. `security/03_cors-configuration.md` - CORS 설정

---

**작성자**: Development Team
**최초 작성일**: 2025-10-17
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
