package com.ryuqq.orchestrator.adapter.runner;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.spi.Bus;
import com.ryuqq.orchestrator.core.spi.Store;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Reaper 컴포넌트.
 *
 * <p>장기 IN_PROGRESS 상태인 작업을 감지하고 리컨실합니다.</p>
 *
 * <p><strong>리컨실 시나리오:</strong></p>
 * <pre>
 * 1. Executor.execute() 호출 → 외부 API 요청
 * 2. 네트워크 장애/타임아웃 → 응답 수신 실패
 * 3. 상태: IN_PROGRESS로 유지 (Outcome 생성 안 됨)
 * 4. Reaper가 주기적 스캔 (예: 5분마다)
 * 5. timeoutThreshold 초과 IN_PROGRESS 작업 발견 (예: 10분 이상)
 * 6. 리컨실 전략 적용:
 *    - RETRY: Envelope 재게시 (재실행)
 *    - FAIL: finalize(FAILED) (실패 처리)
 * </pre>
 *
 * <p><strong>책임:</strong></p>
 * <ul>
 *   <li>장기 IN_PROGRESS 작업 스캔</li>
 *   <li>리컨실 전략에 따른 복구 처리</li>
 *   <li>예외 발생 시에도 계속 진행 (멱등성 보장)</li>
 * </ul>
 *
 * <p><strong>멱등성:</strong></p>
 * <ul>
 *   <li>RETRY: Envelope 재게시는 멱등함 (중복 처리는 작업 자체의 멱등성에 의존)</li>
 *   <li>FAIL: finalize()는 멱등함 (이미 종료된 작업은 Store가 처리)</li>
 *   <li>분산 환경에서 여러 Reaper 인스턴스 실행 가능</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class Reaper {

    private static final Logger log = LoggerFactory.getLogger(Reaper.class);
    private final Bus bus;
    private final Store store;
    private final ReaperConfig config;

    /**
     * 생성자.
     *
     * @param bus 메시지 버스
     * @param store 저장소
     * @param config 설정
     * @throws IllegalArgumentException 의존성이 null인 경우
     */
    public Reaper(Bus bus, Store store, ReaperConfig config) {
        if (bus == null) {
            throw new IllegalArgumentException("bus cannot be null");
        }
        if (store == null) {
            throw new IllegalArgumentException("store cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.bus = bus;
        this.store = store;
        this.config = config;
    }

    /**
     * 장기 IN_PROGRESS 작업 스캔 및 리컨실.
     *
     * <p>주기적으로 호출되어야 합니다 (예: @Scheduled).</p>
     *
     * <p><strong>처리 흐름:</strong></p>
     * <pre>
     * 1. scanInProgress(timeoutThreshold, batchSize) → [OpId1, OpId2, ...]
     * 2. For each OpId:
     *    a. getEnvelope(opId) → Envelope
     *    b. Apply reconcile strategy:
     *       - RETRY: bus.publish(envelope, 0)
     *       - FAIL: store.finalize(opId, FAILED)
     * 3. 성공/실패 카운트 로깅
     * </pre>
     */
    public void scan() {
        log.info("Reaper scan started");

        // 1. 장기 IN_PROGRESS 작업 스캔
        List<OpId> stuckOpIds = store.scanInProgress(
            config.timeoutThresholdMs(),
            config.batchSize()
        );

        // 2. 각 항목 리컨실 시도
        int reconciled = 0;
        for (OpId opId : stuckOpIds) {
            if (tryReconcile(opId)) {
                reconciled++;
            }
        }

        // 3. 결과 로깅
        log.info("Reaper scan completed: {} reconciled out of {} stuck", reconciled, stuckOpIds.size());
    }

    /**
     * 개별 OpId 리컨실 시도.
     *
     * <p>예외 발생 시에도 계속 진행하여 다른 항목 복구를 방해하지 않습니다.</p>
     *
     * @param opId Operation ID
     * @return 리컨실 성공 여부
     */
    private boolean tryReconcile(OpId opId) {
        try {
            // 1. 리컨실 전략 확인
            ReconcileStrategy strategy = config.defaultStrategy();

            // 2. 전략에 따라 처리 (RETRY에서만 envelope 조회)
            switch (strategy) {
                case RETRY -> {
                    Envelope envelope = store.getEnvelope(opId);
                    reconcileRetry(opId, envelope);
                }
                case FAIL -> reconcileFail(opId);
            }

            log.info("Reaper reconciled {} with strategy: {}", opId, strategy);
            return true;

        } catch (Exception e) {
            log.error("Failed to reconcile {} in Reaper scan", opId, e);
            return false;
        }
    }

    /**
     * RETRY 전략 리컨실: Envelope 재게시.
     *
     * @param opId Operation ID
     * @param envelope 원본 Envelope
     */
    private void reconcileRetry(OpId opId, Envelope envelope) {
        // Envelope 재게시 (지연 없이 즉시 실행)
        bus.publish(envelope, 0);
        log.info("Reaper re-published envelope for {}", opId);
    }

    /**
     * FAIL 전략 리컨실: finalize(FAILED).
     *
     * @param opId Operation ID
     */
    private void reconcileFail(OpId opId) {
        // 실패 처리
        store.finalize(opId, OperationState.FAILED);
        log.info("Reaper marked {} as FAILED", opId);
    }
}
