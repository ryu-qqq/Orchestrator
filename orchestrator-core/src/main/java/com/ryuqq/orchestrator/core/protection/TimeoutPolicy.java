package com.ryuqq.orchestrator.core.protection;

import com.ryuqq.orchestrator.core.model.OpId;

/**
 * Timeout Policy SPI.
 *
 * <p>외부 API 호출의 최대 허용 시간을 설정하여 무한 대기를 방지합니다.</p>
 *
 * <p><strong>타임아웃 적용 방식:</strong></p>
 * <ul>
 *   <li>perAttemptTimeout: 각 재시도마다 적용되는 타임아웃</li>
 *   <li>totalTimeout: 모든 재시도를 포함한 전체 타임아웃 (향후 확장)</li>
 * </ul>
 *
 * <p><strong>사용 예시:</strong></p>
 * <pre>{@code
 * TimeoutPolicy policy = ...;
 * OpId opId = OpId.of("external-api-call");
 *
 * long timeout = policy.getPerAttemptTimeoutMs(opId);
 * if (timeout > 0) {
 *     CompletableFuture<Result> future = CompletableFuture.supplyAsync(() -> externalApi.call());
 *     try {
 *         Result result = future.get(timeout, TimeUnit.MILLISECONDS);
 *         return result;
 *     } catch (TimeoutException e) {
 *         policy.recordTimeout(opId, timeout);
 *         throw e;
 *     }
 * }
 * }</pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface TimeoutPolicy {

    /**
     * 시도당(per-attempt) 타임아웃 시간 조회.
     *
     * <p>이 시간 내에 작업이 완료되지 않으면 TimeoutException이 발생합니다.</p>
     *
     * @param opId Operation ID
     * @return 타임아웃 시간 (밀리초), 0은 타임아웃 없음을 의미
     */
    long getPerAttemptTimeoutMs(OpId opId);

    /**
     * 타임아웃 발생 기록.
     *
     * <p>타임아웃이 발생했음을 기록하여 통계 및 모니터링에 활용합니다.</p>
     *
     * @param opId Operation ID
     * @param elapsedMs 실제 경과 시간 (밀리초)
     */
    void recordTimeout(OpId opId, long elapsedMs);
}
