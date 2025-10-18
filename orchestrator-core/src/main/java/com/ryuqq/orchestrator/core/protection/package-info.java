/**
 * Protection SPI (Service Provider Interface) 패키지.
 *
 * <p>Orchestrator Core SDK의 보호 메커니즘 확장점을 제공합니다.
 * 외부 API 호출 시 발생할 수 있는 장애를 격리하고 시스템 안정성을 보장하기 위한
 * Circuit Breaker, Timeout Policy, Rate Limiter, Bulkhead, Hedge Policy 등의
 * Protection Hook 인터페이스를 정의합니다.</p>
 *
 * <h2>Protection Hook 체인 순서</h2>
 *
 * <p>Protection Hook은 다음 순서로 적용됩니다:</p>
 * <pre>
 * 1. TimeoutPolicy   → 타임아웃 타이머 시작
 * 2. CircuitBreaker  → OPEN 상태 시 즉시 실패
 * 3. Bulkhead        → 동시 실행 수 제한
 * 4. RateLimiter     → 초당 요청 수 제한
 * 5. Executor        → 실제 작업 실행
 * 6. HedgePolicy     → Executor 내부에서 병렬 요청 관리
 * </pre>
 *
 * <h3>체인 순서 선정 이유</h3>
 * <ul>
 *   <li><strong>Timeout First:</strong> 전체 작업의 상한선 설정</li>
 *   <li><strong>Circuit Breaker:</strong> 빠른 실패로 불필요한 리소스 소비 방지</li>
 *   <li><strong>Bulkhead:</strong> 리소스 진입 제어 (Semaphore)</li>
 *   <li><strong>Rate Limiter:</strong> QPS 제어 (마지막 체크포인트)</li>
 *   <li><strong>Executor:</strong> 실제 작업 실행</li>
 *   <li><strong>Hedge:</strong> Executor 내부에서 병렬 요청 관리</li>
 * </ul>
 *
 * <h2>NoOp 구현</h2>
 *
 * <p>모든 Protection SPI는 {@code noop} 하위 패키지에 NoOp (No Operation) 기본 구현을 제공합니다.
 * NoOp 구현은 다음과 같은 특징을 가집니다:</p>
 * <ul>
 *   <li>모든 요청을 항상 허용 ({@code tryAcquire()} 계열 메서드는 항상 {@code true} 반환)</li>
 *   <li>상태 추적 및 통계 기록을 하지 않음 ({@code record*()} 계열 메서드는 빈 구현)</li>
 *   <li>개발/테스트 환경에서 보호 없이 빠른 실행</li>
 *   <li>프로덕션 환경에서는 실제 구현으로 교체</li>
 * </ul>
 *
 * <h2>사용 예시</h2>
 *
 * <h3>개발/테스트 환경 (NoOp 사용)</h3>
 * <pre>{@code
 * CircuitBreaker cb = new NoOpCircuitBreaker();
 * TimeoutPolicy timeout = new NoOpTimeoutPolicy();
 * RateLimiter limiter = new NoOpRateLimiter();
 * Bulkhead bulkhead = new NoOpBulkhead();
 * HedgePolicy hedge = new NoOpHedgePolicy();
 *
 * // 모든 Protection이 항상 허용 → 빠른 실행
 * }</pre>
 *
 * <h3>프로덕션 환경 (실제 구현 사용)</h3>
 * <pre>{@code
 * // Resilience4j 어댑터 사용 (adapter-protection 모듈)
 * CircuitBreaker cb = new Resilience4jCircuitBreakerAdapter(...);
 * TimeoutPolicy timeout = new Resilience4jTimeoutPolicyAdapter(...);
 * RateLimiter limiter = new Resilience4jRateLimiterAdapter(...);
 * Bulkhead bulkhead = new Resilience4jBulkheadAdapter(...);
 * HedgePolicy hedge = new CustomHedgePolicyImpl(...);
 *
 * // 실제 보호 메커니즘 적용
 * }</pre>
 *
 * <h2>확장 방법</h2>
 *
 * <p>Protection SPI는 어댑터 패턴을 활용하여 다양한 구현으로 교체할 수 있습니다:</p>
 * <ul>
 *   <li><strong>Resilience4j:</strong> 검증된 Circuit Breaker 라이브러리</li>
 *   <li><strong>Alibaba Sentinel:</strong> 트래픽 제어 및 Circuit Breaking</li>
 *   <li><strong>Custom Implementation:</strong> 프로젝트 요구사항에 맞춘 커스텀 구현</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 * @see com.ryuqq.orchestrator.core.protection.CircuitBreaker
 * @see com.ryuqq.orchestrator.core.protection.TimeoutPolicy
 * @see com.ryuqq.orchestrator.core.protection.RateLimiter
 * @see com.ryuqq.orchestrator.core.protection.Bulkhead
 * @see com.ryuqq.orchestrator.core.protection.HedgePolicy
 * @see com.ryuqq.orchestrator.core.protection.noop
 */
package com.ryuqq.orchestrator.core.protection;
