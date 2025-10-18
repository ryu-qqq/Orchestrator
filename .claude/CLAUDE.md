# Spring Standards Project - Claude Code Configuration

이 프로젝트는 **Spring Boot 3.5.x + Java 21** 기반의 헥사고날 아키텍처 엔터프라이즈 표준 프로젝트입니다.

---

## 🚀 혁신: Dynamic Hooks + Cache 시스템

이 프로젝트의 핵심 차별점은 **AI 기반 자동 규칙 주입 및 실시간 검증 시스템**입니다.

### 시스템 아키텍처

```
docs/coding_convention/ (90개 마크다운 규칙)
         ↓
build-rule-cache.py (Cache 빌드)
         ↓
.claude/cache/rules/ (90개 JSON + index.json)
         ↓
user-prompt-submit.sh (키워드 감지 → Layer 매핑)
         ↓
inject-rules.py (Layer별 규칙 자동 주입)
         ↓
Claude Code (규칙 준수 코드 생성)
         ↓
after-tool-use.sh (생성 직후 검증)
         ↓
validation-helper.py (Cache 기반 실시간 검증)
```

### 성능 메트릭

| 메트릭 | 기존 방식 | Cache 시스템 | 개선율 |
|--------|----------|-------------|--------|
| 토큰 사용량 | 50,000 | 500-1,000 | **90% 절감** |
| 검증 속도 | 561ms | 148ms | **73.6% 향상** |
| 문서 로딩 | 2-3초 | <100ms | **95% 향상** |

---

## 📚 코딩 규칙 (docs/coding_convention/)

### 레이어별 규칙 구조

```
docs/coding_convention/
├── 01-adapter-rest-api-layer/  (18개 규칙)
│   ├── controller-design/
│   ├── dto-patterns/
│   ├── exception-handling/
│   ├── mapper-patterns/
│   ├── package-guide/
│   └── testing/
│
├── 02-domain-layer/  (15개 규칙)
│   ├── aggregate-design/
│   ├── law-of-demeter/  ⭐ Law of Demeter 엄격 적용
│   ├── package-guide/
│   └── testing/
│
├── 03-application-layer/  (18개 규칙)
│   ├── assembler-pattern/
│   ├── dto-patterns/
│   ├── package-guide/
│   ├── testing/
│   ├── transaction-management/  ⭐ Transaction 경계 엄격 관리
│   └── usecase-design/
│
├── 04-persistence-layer/  (10개 규칙)
│   ├── jpa-entity-design/  ⭐ Long FK 전략 (관계 어노테이션 금지)
│   ├── package-guide/
│   ├── querydsl-optimization/
│   ├── repository-patterns/
│   └── testing/
│
├── 05-testing/  (12개 규칙)
│   ├── archunit-rules/
│   └── integration-testing/
│
├── 06-java21-patterns/  (8개 규칙)
│   ├── record-patterns/
│   ├── sealed-classes/
│   └── virtual-threads/
│
├── 07-enterprise-patterns/  (5개 규칙)
│   ├── caching/
│   ├── event-driven/
│   └── resilience/
│
└── 08-error-handling/  (5개 규칙)
    ├── error-handling-strategy/
    ├── domain-exception-design/
    ├── global-exception-handler/
    ├── error-response-format/
    └── errorcode-management/
```

**총 90개 규칙 → JSON Cache로 변환 → O(1) 검색 및 주입**

---

## 🏗️ 프로젝트 핵심 원칙

### 1. 아키텍처 패턴
- **헥사고날 아키텍처** (Ports & Adapters) - 의존성 역전
- **도메인 주도 설계** (DDD) - Aggregate 중심 설계
- **CQRS** - Command/Query 분리

### 2. 코드 품질 규칙 (Zero-Tolerance)
- **Lombok 금지** - Plain Java 사용 (Domain layer에서 특히 엄격)
- **Law of Demeter** - Getter 체이닝 금지 (`order.getCustomer().getAddress()` ❌)
- **Long FK 전략** - JPA 관계 어노테이션 금지, Long userId 사용
- **Transaction 경계** - `@Transactional` 내 외부 API 호출 절대 금지

### 3. Spring 프록시 제약사항 (중요!)
⚠️ **다음 경우 `@Transactional`이 작동하지 않습니다:**
- Private 메서드
- Final 클래스/메서드
- 같은 클래스 내부 호출 (`this.method()`)

---

## 🔧 자동화 시스템

### 1. Dynamic Hooks + Cache 

**위치**: `.claude/hooks/`, `.claude/cache/`, `.claude/commands/lib/`

#### Cache 빌드
```bash
# 90개 마크다운 → 90개 JSON + index.json (약 5초)
python3 .claude/hooks/scripts/build-rule-cache.py
```

#### 자동 규칙 주입 (user-prompt-submit.sh)
- **키워드 감지**: "domain", "usecase", "controller", "entity" 등
- **Layer 매핑**: domain, application, adapter-rest, adapter-persistence
- **inject-rules.py 호출**: Layer별 JSON 규칙 자동 주입

#### 실시간 검증 (after-tool-use.sh)
- **코드 생성 직후 검증**: Write/Edit 도구 사용 후 즉시 실행
- **validation-helper.py 호출**: Cache 기반 고속 검증
- **위반 시 경고**: 구체적인 수정 방법 제시

### 2. Slash Commands

**코드 생성**:
- `/code-gen-domain <name>` - Domain Aggregate 생성 (규칙 자동 주입 + 검증)
- `/code-gen-usecase <name>` - Application UseCase 생성
- `/code-gen-controller <name>` - REST Controller 생성

**검증**:
- `/validate-domain <file>` - Domain layer 파일 검증
- `/validate-architecture [dir]` - 전체 또는 특정 모듈 아키텍처 검증

**기타**:
- `/gemini-review [pr-number]` - Gemini 코드 리뷰 분석
- `/jira-task` - Jira 태스크 분석 및 브랜치 생성

### 3. Git Pre-commit Hooks (별도 시스템)

**위치**: `hooks/pre-commit`, `hooks/validators/`

- **트랜잭션 경계 검증**: `@Transactional` 내 외부 API 호출 차단
- **프록시 제약사항 검증**: Private/Final 메서드 `@Transactional` 차단
- **최종 안전망 역할**: 커밋 시 강제 검증

### 4. ArchUnit Tests

**위치**: `application/src/test/java/com/company/template/architecture/`

- **아키텍처 규칙 자동 검증**: 레이어 의존성, 네이밍 규칙
- **빌드 시 자동 실행**: 위반 시 빌드 실패

---

## 🎯 개발 워크플로우 (Cache 시스템 활용)

### 1. 코드 생성 워크플로우

```bash
# 1. Slash Command로 코드 생성 (자동 규칙 주입)
/code-gen-domain Order

# 2. 자동 실행 흐름:
#    - inject-rules.py: Domain layer 규칙 주입
#    - Claude: 규칙 준수 코드 생성
#    - after-tool-use.sh: 즉시 검증
#    - validation-helper.py: Cache 기반 검증

# 3. 검증 결과 확인
# ✅ Validation Passed: 모든 규칙 준수
# ❌ Validation Failed: 위반 규칙 상세 표시
```

### 2. 수동 검증 워크플로우

```bash
# 특정 파일 검증
/validate-domain domain/src/main/java/.../Order.java

# 전체 프로젝트 검증
/validate-architecture

# 특정 모듈만 검증
/validate-architecture domain
```

### 3. Cache 업데이트 워크플로우

```bash
# 1. 규칙 문서 수정
vim docs/coding_convention/02-domain-layer/law-of-demeter/01_getter-chaining-prohibition.md

# 2. Cache 재빌드
python3 .claude/hooks/scripts/build-rule-cache.py

# 3. 확인
cat .claude/cache/rules/domain-layer-law-of-demeter-01_getter-chaining-prohibition.json
```

---

## 🚨 Zero-Tolerance 규칙

다음 규칙은 **예외 없이** 반드시 준수해야 합니다:

### 1. Lombok 금지
- ❌ `@Data`, `@Builder`, `@Getter`, `@Setter` 등 모두 금지
- ✅ Pure Java getter/setter 직접 작성
- **검증**: validation-helper.py가 자동 감지

### 2. Law of Demeter (Getter 체이닝 금지)
- ❌ `order.getCustomer().getAddress().getZip()`
- ✅ `order.getCustomerZipCode()` (Tell, Don't Ask)
- **검증**: Anti-pattern 정규식 매칭

### 3. Long FK 전략 (JPA 관계 금지)
- ❌ `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`
- ✅ `private Long userId;` (Long FK 사용)
- **검증**: JPA 관계 어노테이션 감지

### 4. Transaction 경계
- ❌ `@Transactional` 내 외부 API 호출 (RestTemplate, WebClient 등)
- ✅ 트랜잭션은 짧게 유지, 외부 호출은 트랜잭션 밖에서
- **검증**: Git pre-commit hook

### 5. Javadoc 필수
- ❌ `@author`, `@since` 없는 public 클래스/메서드
- ✅ 모든 public 클래스/메서드에 Javadoc 포함
- **검증**: Checkstyle

### 6. Scope 준수
- ❌ 요청하지 않은 추가 기능 구현
- ✅ 요청된 코드만 정확히 작성
- **검증**: 수동 코드 리뷰

---

## 📖 참고 문서

### 튜토리얼
- [Getting Started](../docs/tutorials/01-getting-started.md) - 시작 가이드 (5분)

### Dynamic Hooks 시스템
- [DYNAMIC_HOOKS_GUIDE.md](../docs/DYNAMIC_HOOKS_GUIDE.md) - 전체 시스템 가이드
- [Cache README](./.claude/cache/rules/README.md) - Cache 시스템 상세
- [Validation Helper](./hooks/scripts/validation-helper.py) - 검증 엔진

### Slash Commands
- [Commands README](./commands/README.md) - 모든 명령어 설명
- [Code Gen Domain](./commands/code-gen-domain.md) - Domain 생성
- [Validate Domain](./commands/validate-domain.md) - Domain 검증

### 코딩 규칙
- [Coding Convention](../docs/coding_convention/) - 90개 규칙 (Layer별)

---

## 🎓 학습 경로

### Day 1: 시스템 이해
1. README.md 읽기 (프로젝트 개요)
2. docs/tutorials/01-getting-started.md (실습)
3. Cache 빌드 및 첫 코드 생성 테스트

### Week 1: 핵심 규칙 숙지
1. Domain Layer 규칙 (Law of Demeter, Lombok 금지)
2. Application Layer 규칙 (Transaction 경계)
3. Persistence Layer 규칙 (Long FK 전략)

### Month 1: 고급 패턴
1. DDD Aggregate 설계
2. CQRS 패턴 적용
3. Event-Driven Architecture

---

**✅ 이 프로젝트의 모든 코드는 위 표준을 따라야 합니다.**

**💡 핵심**: Dynamic Hooks + Cache 시스템이 자동으로 규칙을 주입하고 검증하므로, 개발자는 비즈니스 로직에 집중할 수 있습니다!
