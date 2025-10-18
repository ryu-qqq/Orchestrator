package com.ryuqq.orchestrator.adapter.runner;

/**
 * Exponential Backoff with Jitter 계산기.
 *
 * <p>재시도 간격을 지수적으로 증가시키되, Jitter를 추가하여
 * Thundering Herd Problem을 방지합니다.</p>
 *
 * <p><strong>알고리즘:</strong></p>
 * <pre>
 * delay = min(baseDelay * 2^(attemptCount-1) + jitter, maxDelay)
 * jitter = random(0, exponential * jitterFactor)
 * </pre>
 *
 * <p><strong>예시 (baseDelay=1000ms, jitterFactor=0.1):</strong></p>
 * <ul>
 *   <li>attemptCount=1: 1000ms + jitter(0-100ms) = 1000-1100ms (2^0 * base)</li>
 *   <li>attemptCount=2: 2000ms + jitter(0-200ms) = 2000-2200ms (2^1 * base)</li>
 *   <li>attemptCount=3: 4000ms + jitter(0-400ms) = 4000-4400ms (2^2 * base)</li>
 *   <li>attemptCount=10: 512000ms (capped at maxDelay=300000ms) (2^9 * base)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class BackoffCalculator {

    private final long baseDelayMs;
    private final long maxDelayMs;
    private final double jitterFactor;

    /**
     * 기본 설정으로 생성.
     *
     * <p>기본값: baseDelay=1000ms, maxDelay=300000ms, jitterFactor=0.1</p>
     */
    public BackoffCalculator() {
        this(1000, 300000, 0.1);
    }

    /**
     * 커스텀 설정으로 생성.
     *
     * @param baseDelayMs 기본 지연 시간 (밀리초, 양수여야 함)
     * @param maxDelayMs 최대 지연 시간 (밀리초, baseDelayMs 이상이어야 함)
     * @param jitterFactor Jitter 비율 (0.0 ~ 1.0)
     * @throws IllegalArgumentException 파라미터 검증 실패 시
     */
    public BackoffCalculator(long baseDelayMs, long maxDelayMs, double jitterFactor) {
        if (baseDelayMs <= 0) {
            throw new IllegalArgumentException(
                "baseDelayMs must be positive (current: " + baseDelayMs + ")"
            );
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException(
                "maxDelayMs must be >= baseDelayMs (base: " + baseDelayMs + ", max: " + maxDelayMs + ")"
            );
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException(
                "jitterFactor must be between 0.0 and 1.0 (current: " + jitterFactor + ")"
            );
        }

        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.jitterFactor = jitterFactor;
    }

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
        // attemptCount=1일 때 baseDelayMs, attemptCount=2일 때 2*baseDelayMs, ...
        long exponential = Math.min(
            baseDelayMs * (1L << (attemptCount - 1)),
            maxDelayMs
        );

        // 2. Jitter 추가 (0 ~ exponential * jitterFactor)
        long jitter = (long) (exponential * jitterFactor * Math.random());

        // 3. 최대값 제한
        return Math.min(exponential + jitter, maxDelayMs);
    }

    /**
     * 기본 지연 시간 조회.
     *
     * @return 기본 지연 시간 (밀리초)
     */
    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    /**
     * 최대 지연 시간 조회.
     *
     * @return 최대 지연 시간 (밀리초)
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    /**
     * Jitter 비율 조회.
     *
     * @return Jitter 비율 (0.0 ~ 1.0)
     */
    public double getJitterFactor() {
        return jitterFactor;
    }
}
