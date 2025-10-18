package com.ryuqq.orchestrator.adapter.runner;

/**
 * Exponential Backoff with Jitter 계산기.
 *
 * <p>재시도 간격을 지수적으로 증가시키되, Jitter를 추가하여
 * Thundering Herd Problem을 방지합니다.</p>
 *
 * <p><strong>알고리즘:</strong></p>
 * <pre>
 * delay = min(baseDelay * 2^attemptCount + jitter, maxDelay)
 * jitter = random(0, exponential * jitterFactor)
 * </pre>
 *
 * <p><strong>예시 (baseDelay=1000ms, jitterFactor=0.1):</strong></p>
 * <ul>
 *   <li>attemptCount=1: 1000ms + jitter(0-100ms) = 1000-1100ms</li>
 *   <li>attemptCount=2: 2000ms + jitter(0-200ms) = 2000-2200ms</li>
 *   <li>attemptCount=3: 4000ms + jitter(0-400ms) = 4000-4400ms</li>
 *   <li>attemptCount=10: 1024000ms (capped at maxDelay=300000ms)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class BackoffCalculator {

    private static final long BASE_DELAY_MS = 1000;      // 1초
    private static final long MAX_DELAY_MS = 300000;     // 5분
    private static final double JITTER_FACTOR = 0.1;     // 10% jitter

    /**
     * 재시도 지연 시간 계산.
     *
     * <p>지수적 백오프와 무작위 jitter를 적용하여
     * 재시도 간격을 계산합니다.</p>
     *
     * @param attemptCount 현재 재시도 횟수 (1부터 시작)
     * @return 재시도 전 대기 시간 (밀리초)
     * @throws IllegalArgumentException attemptCount가 양수가 아닌 경우
     */
    public long calculate(int attemptCount) {
        if (attemptCount <= 0) {
            throw new IllegalArgumentException(
                "attemptCount must be positive (current: " + attemptCount + ")"
            );
        }

        // 1. 지수적 백오프 (overflow 방지를 위해 min 적용)
        long exponential = Math.min(
            BASE_DELAY_MS * (1L << attemptCount),
            MAX_DELAY_MS
        );

        // 2. Jitter 추가 (0 ~ exponential * jitterFactor)
        long jitter = (long) (exponential * JITTER_FACTOR * Math.random());

        // 3. 최대값 제한
        return Math.min(exponential + jitter, MAX_DELAY_MS);
    }

    /**
     * 기본 지연 시간 조회.
     *
     * @return 기본 지연 시간 (밀리초)
     */
    public long getBaseDelayMs() {
        return BASE_DELAY_MS;
    }

    /**
     * 최대 지연 시간 조회.
     *
     * @return 최대 지연 시간 (밀리초)
     */
    public long getMaxDelayMs() {
        return MAX_DELAY_MS;
    }

    /**
     * Jitter 비율 조회.
     *
     * @return Jitter 비율 (0.0 ~ 1.0)
     */
    public double getJitterFactor() {
        return JITTER_FACTOR;
    }
}
