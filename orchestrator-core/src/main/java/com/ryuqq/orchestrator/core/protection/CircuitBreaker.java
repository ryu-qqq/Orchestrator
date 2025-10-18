package com.ryuqq.orchestrator.core.protection;

import com.ryuqq.orchestrator.core.model.OpId;

/**
 * Circuit Breaker SPI.
 *
 * <p>외부 API 호출의 실패율을 추적하고, 임계값 초과 시 빠르게 실패(Fail-Fast)하여
 * 장애가 전체 시스템으로 전파되는 것을 방지합니다.</p>
 *
 * <p><strong>Circuit Breaker 패턴:</strong></p>
 * <ul>
 *   <li>CLOSED: 정상 동작, 실패율 추적</li>
 *   <li>OPEN: 요청 차단, 빠른 실패</li>
 *   <li>HALF_OPEN: 제한된 요청으로 복구 테스트</li>
 * </ul>
 *
 * <p><strong>사용 예시:</strong></p>
 * <pre>{@code
 * CircuitBreaker cb = ...;
 * OpId opId = OpId.of("external-api-call");
 *
 * if (!cb.tryAcquire(opId)) {
 *     // Circuit Breaker OPEN 상태
 *     return new Fail("CB-OPEN", "Circuit Breaker is OPEN");
 * }
 *
 * try {
 *     // 외부 API 호출
 *     Result result = externalApi.call();
 *     cb.recordSuccess(opId);
 *     return result;
 * } catch (Exception e) {
 *     cb.recordFailure(opId, e);
 *     throw e;
 * }
 * }</pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface CircuitBreaker {

    /**
     * Circuit Breaker 통과 허용 여부 확인.
     *
     * <p>현재 Circuit Breaker 상태에 따라 요청 통과를 허용할지 결정합니다.</p>
     *
     * <ul>
     *   <li>CLOSED: 항상 true 반환 (정상 통과)</li>
     *   <li>OPEN: 항상 false 반환 (즉시 차단)</li>
     *   <li>HALF_OPEN: 제한된 수의 요청만 true (테스트 통과)</li>
     * </ul>
     *
     * @param opId Operation ID (통계 및 로깅용)
     * @return true: 요청 통과 허용, false: 요청 차단
     */
    boolean tryAcquire(OpId opId);

    /**
     * 실행 성공 기록.
     *
     * <p>외부 API 호출 성공 시 호출되어 Circuit Breaker 상태를 갱신합니다.</p>
     *
     * <ul>
     *   <li>CLOSED: 성공 카운터 증가</li>
     *   <li>HALF_OPEN: 연속 성공 임계값 도달 시 CLOSED로 전이</li>
     * </ul>
     *
     * @param opId Operation ID
     */
    void recordSuccess(OpId opId);

    /**
     * 실행 실패 기록.
     *
     * <p>외부 API 호출 실패 시 호출되어 Circuit Breaker 상태를 갱신합니다.</p>
     *
     * <ul>
     *   <li>CLOSED: 실패율 계산 후 임계값 초과 시 OPEN으로 전이</li>
     *   <li>HALF_OPEN: 즉시 OPEN으로 전이</li>
     * </ul>
     *
     * @param opId Operation ID
     * @param throwable 발생한 예외
     */
    void recordFailure(OpId opId, Throwable throwable);

    /**
     * 현재 Circuit Breaker 상태 조회.
     *
     * @return CLOSED, OPEN, HALF_OPEN 중 하나
     */
    CircuitBreakerState getState();

    /**
     * Circuit Breaker를 CLOSED 상태로 강제 리셋.
     *
     * <p>수동 복구 또는 테스트 목적으로 사용됩니다.
     * 프로덕션 환경에서는 신중하게 사용해야 합니다.</p>
     */
    void reset();
}
