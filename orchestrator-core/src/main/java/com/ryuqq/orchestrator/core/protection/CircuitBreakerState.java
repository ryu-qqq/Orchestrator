package com.ryuqq.orchestrator.core.protection;

/**
 * Circuit Breaker 상태.
 *
 * <p>Circuit Breaker는 외부 API 호출의 실패율을 추적하고,
 * 임계값 초과 시 요청을 차단하여 장애 전파를 방지합니다.</p>
 *
 * <p><strong>상태 전이:</strong></p>
 * <pre>
 * CLOSED (정상)
 *   │
 *   ▼ (실패율 임계값 초과)
 * OPEN (차단)
 *   │
 *   ▼ (대기 시간 경과)
 * HALF_OPEN (반개방)
 *   │
 *   ├─► 성공 → CLOSED
 *   └─► 실패 → OPEN
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public enum CircuitBreakerState {

    /**
     * 정상 상태 (요청 통과).
     *
     * <p>모든 요청이 정상적으로 처리되며, 실패율을 추적합니다.
     * 실패율이 임계값을 초과하면 OPEN 상태로 전이합니다.</p>
     */
    CLOSED,

    /**
     * 차단 상태 (요청 즉시 거부).
     *
     * <p>모든 요청을 즉시 거부하여 장애가 전파되는 것을 방지합니다.
     * 대기 시간이 경과하면 HALF_OPEN 상태로 전이합니다.</p>
     */
    OPEN,

    /**
     * 반개방 상태 (일부 요청만 통과하여 테스트).
     *
     * <p>제한된 수의 요청만 통과시켜 외부 API의 복구 여부를 테스트합니다.
     * 테스트 요청이 성공하면 CLOSED, 실패하면 다시 OPEN으로 전이합니다.</p>
     */
    HALF_OPEN
}
