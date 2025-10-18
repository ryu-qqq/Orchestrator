package com.ryuqq.orchestrator.core.protection;

/**
 * Bulkhead 설정.
 *
 * <p>Bulkhead의 동작을 제어하는 설정 정보입니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class BulkheadConfig {

    private final int maxConcurrentCalls;
    private final int maxWaitDurationMs;

    /**
     * Bulkhead 설정 생성.
     *
     * @param maxConcurrentCalls 최대 동시 실행 수 (예: 10)
     * @param maxWaitDurationMs 최대 대기 시간 (밀리초)
     */
    public BulkheadConfig(int maxConcurrentCalls, int maxWaitDurationMs) {
        if (maxConcurrentCalls <= 0) {
            throw new IllegalArgumentException("maxConcurrentCalls must be positive");
        }
        if (maxWaitDurationMs < 0) {
            throw new IllegalArgumentException("maxWaitDurationMs cannot be negative");
        }
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.maxWaitDurationMs = maxWaitDurationMs;
    }

    /**
     * 최대 동시 실행 수 조회.
     *
     * @return 최대 동시 실행 수
     */
    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    /**
     * 최대 대기 시간 조회.
     *
     * @return 최대 대기 시간 (밀리초)
     */
    public int getMaxWaitDurationMs() {
        return maxWaitDurationMs;
    }
}
