package com.ryuqq.orchestrator.core.protection;

import com.ryuqq.orchestrator.core.model.OpId;

/**
 * Rate Limiter SPI.
 *
 * <p>초당 요청 수를 제한하여 외부 API 과부하 및 내부 리소스 고갈을 방지합니다.</p>
 *
 * <p><strong>Rate Limiting 알고리즘:</strong></p>
 * <ul>
 *   <li>Token Bucket: 일정 속도로 토큰 생성, 요청 시 토큰 소비</li>
 *   <li>Fixed Window: 시간 윈도우 내 요청 수 카운트</li>
 *   <li>Sliding Window: 이동 윈도우로 더 정확한 제한</li>
 * </ul>
 *
 * <p><strong>사용 예시:</strong></p>
 * <pre>{@code
 * RateLimiter limiter = ...;
 * OpId opId = OpId.of("external-api-call");
 *
 * if (!limiter.tryAcquire(opId)) {
 *     // Rate Limit 초과
 *     return new Fail("RATE-LIMIT", "Rate limit exceeded");
 * }
 *
 * // 요청 처리
 * Result result = externalApi.call();
 * return result;
 * }</pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface RateLimiter {

    /**
     * Rate Limiter 통과 허용 여부 확인 (비블로킹).
     *
     * <p>현재 Rate Limit 내에서 요청 처리가 가능한지 즉시 확인합니다.
     * 허용되지 않으면 false를 반환하며, 대기하지 않습니다.</p>
     *
     * @param opId Operation ID (통계 및 로깅용)
     * @return true: 요청 허용, false: Rate Limit 초과
     */
    boolean tryAcquire(OpId opId);

    /**
     * Rate Limiter 통과 허용 여부 확인 (타임아웃 대기).
     *
     * <p>지정된 시간 동안 대기하여 Rate Limit 허용을 시도합니다.
     * 대기 시간 내에 허용되면 true, 타임아웃 시 false를 반환합니다.</p>
     *
     * @param opId Operation ID
     * @param timeoutMs 최대 대기 시간 (밀리초)
     * @return true: 요청 허용, false: 타임아웃 또는 인터럽트
     * @throws InterruptedException 대기 중 인터럽트 발생
     */
    boolean tryAcquire(OpId opId, long timeoutMs) throws InterruptedException;

    /**
     * Rate Limiter 설정 정보 조회.
     *
     * @return Rate Limiter 설정 (QPS 등)
     */
    RateLimiterConfig getConfig();
}
