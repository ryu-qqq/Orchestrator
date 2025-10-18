package com.ryuqq.orchestrator.adapter.runner;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Fail;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.outcome.Retry;
import com.ryuqq.orchestrator.core.spi.Store;
import com.ryuqq.orchestrator.core.spi.WriteAheadState;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Finalizer 컴포넌트.
 *
 * <p>writeAhead 성공 후 finalize 실패한 항목을 주기적으로 복구합니다.</p>
 *
 * <p><strong>복구 시나리오:</strong></p>
 * <pre>
 * 1. QueueWorker가 writeAhead(opId, outcome) 성공
 * 2. finalize(opId, state) 호출 전 크래시 발생
 *    → WriteAheadLog에 PENDING 상태로 남음
 * 3. Finalizer가 주기적으로 scanWA(PENDING) 실행
 * 4. PENDING 항목 발견 → getWriteAheadOutcome() → finalize() 완료
 * </pre>
 *
 * <p><strong>책임:</strong></p>
 * <ul>
 *   <li>PENDING 상태 WriteAheadLog 스캔</li>
 *   <li>Outcome에 따라 적절한 OperationState로 finalize</li>
 *   <li>예외 발생 시에도 계속 진행 (멱등성 보장)</li>
 * </ul>
 *
 * <p><strong>멱등성:</strong></p>
 * <ul>
 *   <li>동일 OpId를 여러 번 finalize해도 안전</li>
 *   <li>이미 finalize된 항목은 Store가 무시하거나 예외 발생</li>
 *   <li>분산 환경에서 여러 Finalizer 인스턴스 실행 가능</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class Finalizer {

    private static final Logger log = LoggerFactory.getLogger(Finalizer.class);
    private final Store store;
    private final FinalizerConfig config;

    /**
     * 생성자.
     *
     * @param store 저장소
     * @param config 설정
     * @throws IllegalArgumentException 의존성이 null인 경우
     */
    public Finalizer(Store store, FinalizerConfig config) {
        if (store == null) {
            throw new IllegalArgumentException("store cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.store = store;
        this.config = config;
    }

    /**
     * PENDING 항목 스캔 및 복구.
     *
     * <p>주기적으로 호출되어야 합니다 (예: @Scheduled).</p>
     *
     * <p><strong>처리 흐름:</strong></p>
     * <pre>
     * 1. scanWA(PENDING, batchSize) → [OpId1, OpId2, ...]
     * 2. For each OpId:
     *    a. getWriteAheadOutcome(opId) → Outcome (Ok, Retry, Fail)
     *    b. Determine target state:
     *       - Ok → COMPLETED
     *       - Fail → FAILED
     *       - Retry → FAILED (이상 케이스, 로그 경고)
     *    c. finalize(opId, targetState)
     * 3. 성공/실패 카운트 로깅
     * </pre>
     */
    public void scan() {
        log.info("Finalizer scan started");

        // 1. PENDING 항목 스캔
        List<OpId> pendingOpIds = store.scanWA(
            WriteAheadState.PENDING,
            config.batchSize()
        );

        // 2. 각 항목 복구 시도
        int recovered = 0;
        for (OpId opId : pendingOpIds) {
            if (tryFinalize(opId)) {
                recovered++;
            }
        }

        // 3. 결과 로깅
        log.info("Finalizer scan completed: {} recovered out of {} pending", recovered, pendingOpIds.size());
    }

    /**
     * 개별 OpId finalize 시도.
     *
     * <p>예외 발생 시에도 계속 진행하여 다른 항목 복구를 방해하지 않습니다.</p>
     *
     * @param opId Operation ID
     * @return 복구 성공 여부
     */
    private boolean tryFinalize(OpId opId) {
        try {
            // 1. WriteAheadLog에서 Outcome 조회
            Outcome outcome = store.getWriteAheadOutcome(opId);

            // 2. Outcome에 따라 target state 결정
            OperationState targetState = switch (outcome) {
                case Ok ok -> OperationState.COMPLETED;
                case Fail fail -> OperationState.FAILED;
                case Retry retry -> {
                    // Retry는 PENDING 상태로 남아있으면 안 됨 (이상 케이스)
                    log.warn("Unexpected Retry in WriteAheadLog for {}", opId);
                    yield OperationState.FAILED;
                }
            };

            // 3. finalize 시도
            store.finalize(opId, targetState);
            log.info("Finalizer recovered {}: {} → {}", opId, OperationState.IN_PROGRESS, targetState);
            return true;

        } catch (Exception e) {
            log.error("Failed to finalize {} in Finalizer scan", opId, e);
            return false;
        }
    }
}
