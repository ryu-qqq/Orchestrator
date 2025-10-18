# Claude Code Slash Commands

이 디렉토리는 Claude Code에서 사용 가능한 슬래시 커맨드들을 포함합니다.

## 📋 사용 가능한 커맨드

### 🔨 코드 생성 커맨드

#### `/code-gen-domain`
**목적**: DDD Aggregate 자동 생성

**사용법**:
```
/code-gen-domain Order
/code-gen-domain Payment @prd/payment-feature.md
```

**생성되는 파일**:
- `{Aggregate}.java` - Aggregate Root
- `{Aggregate}Id.java` - Typed ID (record)
- `{Aggregate}Status.java` - Status Enum

**자동 주입 규칙**:
- ❌ Lombok 금지
- ✅ Law of Demeter
- ✅ Tell, Don't Ask 패턴
- ✅ Pure Java (Spring/JPA 없음)

---

#### `/code-gen-usecase`
**목적**: Application UseCase 자동 생성

**사용법**:
```
/code-gen-usecase PlaceOrder
/code-gen-usecase CancelOrder @prd/order-management.md
```

**생성되는 파일**:
- `{UseCase}UseCase.java` - UseCase 서비스
- `{UseCase}Command.java` - Input DTO (record)
- `{UseCase}Result.java` - Output DTO (record)
- `{Aggregate}Assembler.java` - Domain ↔ DTO 변환

**자동 주입 규칙**:
- ❌ `@Transactional` 내 외부 API 호출 금지
- ❌ Private/Final 메서드에 `@Transactional` 금지
- ✅ DTO 변환 패턴
- ✅ 트랜잭션 짧게 유지

---

#### `/code-gen-controller`
**목적**: REST API Controller 자동 생성

**사용법**:
```
/code-gen-controller Order
/code-gen-controller Payment @prd/payment-api.md
```

**생성되는 파일**:
- `{Resource}Controller.java` - REST Controller
- `{Resource}CreateRequest.java` - Request DTO
- `{Resource}Response.java` - Response DTO
- `{Resource}ApiMapper.java` - API ↔ UseCase 변환

**자동 주입 규칙**:
- ✅ @RestController 사용
- ✅ @Valid 유효성 검증
- ✅ HTTP 상태 코드 표준화
- ❌ Domain 객체 직접 노출 금지

---

## 🔧 규칙 주입 시스템

모든 커맨드는 `.claude/cache/rules/` 디렉토리의 JSON Cache를 기반으로 레이어별 규칙을 자동으로 주입합니다.

---

## 🚀 사용 예시

### 전체 기능 생성 워크플로우

```bash
# 1. Domain Aggregate 생성
/code-gen-domain Order @prd/order-management.md

# 2. UseCase 생성
/code-gen-usecase PlaceOrder @prd/order-management.md

# 3. Controller 생성
/code-gen-controller Order @prd/order-api-spec.md
```

---

**✅ 모든 커맨드는 프로젝트의 엔터프라이즈 표준을 따릅니다.**
