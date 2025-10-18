package com.ryuqq.orchestrator.core.protection;

import com.ryuqq.orchestrator.core.model.OpId;

/**
 * Hedge Policy SPI.
 *
 * <p>외부 API 호출 시 일정 시간 후 추가 요청을 발송하여,
 * 먼저 응답하는 쪽의 결과를 사용함으로써 지연 시간을 최소화합니다.</p>
 *
 * <p><strong>Hedging 전략:</strong></p>
 * <pre>
 * 1. 첫 번째 요청 발송 (t=0ms)
 * 2. hedgeDelayMs 대기 (예: 50ms)
 * 3. 첫 번째 요청 미완료 시 두 번째 요청 발송 (t=50ms)
 * 4. 먼저 응답하는 쪽 결과 사용
 * 5. 나머지 요청 취소
 * </pre>
 *
 * <p><strong>사용 예시:</strong></p>
 * <pre>{@code
 * HedgePolicy policy = ...;
 * OpId opId = OpId.of("external-api-call");
 *
 * if (!policy.shouldHedge(opId)) {
 *     // Hedging 비활성화, 일반 요청만 발송
 *     return externalApi.call();
 * }
 *
 * long hedgeDelay = policy.getHedgeDelayMs(opId);
 * CompletableFuture<Result> primary = CompletableFuture.supplyAsync(() -> externalApi.call());
 *
 * CompletableFuture<Result> hedge = primary.applyToEither(
 *     CompletableFuture.runAsync(() -> {
 *         Thread.sleep(hedgeDelay);
 *     }).thenCompose(v -> CompletableFuture.supplyAsync(() -> {
 *         policy.recordHedgeAttempt(opId, 1);
 *         return externalApi.call();
 *     })),
 *     Function.identity()
 * );
 *
 * Result result = hedge.get();
 * policy.recordSuccess(opId, result.wasHedge());
 * return result;
 * }</pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface HedgePolicy {

    /**
     * Hedging 적용 여부 확인.
     *
     * <p>특정 Operation에 대해 Hedging을 적용할지 여부를 결정합니다.</p>
     *
     * @param opId Operation ID
     * @return true: Hedging 적용, false: 적용 안 함
     */
    boolean shouldHedge(OpId opId);

    /**
     * Hedge 요청 발송까지 대기 시간 조회.
     *
     * <p>첫 번째 요청 발송 후 몇 ms 후에 Hedge 요청을 발송할지 결정합니다.</p>
     *
     * @param opId Operation ID
     * @return Hedge 요청 발송 대기 시간 (밀리초)
     */
    long getHedgeDelayMs(OpId opId);

    /**
     * 최대 Hedge 요청 수 조회.
     *
     * <p>원본 요청 외에 추가로 발송할 Hedge 요청의 최대 수를 결정합니다.
     * 일반적으로 1-2개가 적절합니다.</p>
     *
     * @param opId Operation ID
     * @return 최대 Hedge 요청 수 (기본: 1)
     */
    int getMaxHedges(OpId opId);

    /**
     * Hedge 요청 발송 기록.
     *
     * <p>Hedge 요청이 발송되었음을 기록하여 통계 및 모니터링에 활용합니다.</p>
     *
     * @param opId Operation ID
     * @param hedgeNumber Hedge 요청 번호 (1부터 시작)
     */
    void recordHedgeAttempt(OpId opId, int hedgeNumber);

    /**
     * Hedge 성공 기록 (어느 요청이 성공했는지).
     *
     * <p>원본 또는 Hedge 요청 중 어느 것이 먼저 성공했는지 기록합니다.</p>
     *
     * @param opId Operation ID
     * @param wasHedge true: Hedge 요청 성공, false: 원본 요청 성공
     */
    void recordSuccess(OpId opId, boolean wasHedge);
}
