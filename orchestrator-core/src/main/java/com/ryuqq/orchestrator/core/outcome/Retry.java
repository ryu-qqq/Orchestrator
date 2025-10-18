package com.ryuqq.orchestrator.core.outcome;

/**
 * 재시도 가능한 일시적 실패.
 *
 * <p>일시적인 오류로 인해 실패했으나, 재시도하면 성공할 가능성이 있는 경우를 나타냅니다.</p>
 *
 * <p><strong>예시:</strong></p>
 * <ul>
 *   <li>네트워크 타임아웃</li>
 *   <li>외부 서비스 일시 장애 (503 Service Unavailable)</li>
 *   <li>Rate Limit 초과 (429 Too Many Requests)</li>
 * </ul>
 *
 * @param reason 재시도 사유
 * @param attemptCount 현재까지 시도 횟수 (1 이상)
 * @param nextRetryAfterMillis 다음 재시도까지 대기 시간 (밀리초, 0 이상)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Retry(
    String reason,
    int attemptCount,
    long nextRetryAfterMillis
) implements Outcome {

    /**
     * Compact Constructor.
     *
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public Retry {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be null or blank");
        }
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive (current: " + attemptCount + ")");
        }
        if (nextRetryAfterMillis < 0) {
            throw new IllegalArgumentException("nextRetryAfterMillis must be non-negative (current: " + nextRetryAfterMillis + ")");
        }
    }
}
