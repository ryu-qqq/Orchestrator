# Orchestrator Core SDK

> 외부 호출을 수반하는 업무 흐름을 표준화하기 위한 헥사고날 기반 코어 SDK

## 개요

Orchestrator는 외부 API(결제, 파일, 써드파티 등)가 개입하는 플로우에서 **업무 원자성**과 **최종 일관성**을 보장하는 Core-only 프레임워크입니다.

### 핵심 철학
- **헥사고날 아키텍처**: Core는 구체 기술 구현을 포함하지 않음 (JPA/Kafka/SQS 등)
- **강한 계약**: 상태머신, 멱등성, 시간 예산, 재시도 예산
- **3단계 수명주기**: S1(수락) → S2(실행) → S3(종결)

## 기술 스택

- **Java**: 21
- **Build Tool**: Gradle 8.5
- **Test Framework**: JUnit 5
- **Code Coverage**: JaCoCo

## 프로젝트 구조 (멀티모듈)

```
orchestrator/
├── orchestrator-core/              # Core SDK (순수 계약)
│   ├── src/main/java/
│   │   └── com/ryuqq/orchestrator/core/
│   │       ├── api/                # Orchestrator, Executor, Runtime
│   │       ├── model/              # OpId, Command, Outcome 등
│   │       ├── spi/                # Store, Bus, Protection SPI
│   │       └── policy/             # RetryPolicy 등
│   └── build.gradle
│
├── orchestrator-testkit/           # Contract Tests
│   ├── src/main/java/
│   │   └── com/ryuqq/orchestrator/testkit/
│   │       └── contracts/          # 7가지 시나리오
│   └── build.gradle
│
├── orchestrator-adapter-inmemory/  # 레퍼런스 어댑터 (선택적)
│   ├── src/main/java/
│   │   └── com/ryuqq/orchestrator/adapter/inmemory/
│   │       ├── store/              # InMemoryStore
│   │       ├── bus/                # InMemoryBus
│   │       └── protection/         # 샘플 Protection
│   └── build.gradle
│
├── build.gradle                    # 루트 빌드
├── settings.gradle                 # 멀티모듈 설정
└── README.md
```

### 모듈별 역할

| 모듈 | 의존성 | 목적 |
|------|--------|------|
| **orchestrator-core** | 없음 (순수) | 공개 API/SPI 정의 |
| **orchestrator-testkit** | core 의존 | 어댑터 적합성 검증 |
| **orchestrator-adapter-inmemory** | core + testkit | 참조 구현 예시 |

## 빌드 및 테스트

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# 테스트 커버리지 리포트
./gradlew jacocoTestReport
```

## 📖 문서 및 가이드

### 시작하기
- **[Quick Start 가이드](./docs/guides/01-quick-start.md)**: 30분 내에 첫 Operation 실행 (Hello World 예제)
- **[어댑터 구현 가이드](./docs/guides/02-adapter-implementation.md)**: Store, Bus, Protection 어댑터 구현 방법

### 설계 문서
- **[Orchestrator 설계 문서](./Orchestrator_guide.md)**: 전체 아키텍처 및 설계 철학
- **[PRD 문서](./docs/prd/)**: Epic별 상세 요구사항 문서

## Jira 프로젝트

- **프로젝트 키**: OR
- **에픽**:
  - OR-1: Core API 및 계약 구현
  - OR-6: 테스트킷 (Contract Tests) 구현
  - OR-9: 문서 및 개발자 가이드 ✅
  - OR-12: 레퍼런스 어댑터 구현

## 라이센스

TBD
