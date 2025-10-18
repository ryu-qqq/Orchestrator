package com.ryuqq.orchestrator.core.executor;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.statemachine.OperationState;

/**
 * Operation 실행자.
 *
 * <p>Envelope을 받아 비동기로 실행하고, 상태와 결과를 관리합니다.</p>
 *
 * <p><strong>책임:</strong></p>
 * <ul>
 *   <li>Envelope 실행 (비동기 시작)</li>
 *   <li>Operation 상태 조회 (PENDING, IN_PROGRESS, COMPLETED, FAILED)</li>
 *   <li>Operation 결과 조회 (Outcome: Ok, Retry, Fail)</li>
 * </ul>
 *
 * <p><strong>동시성:</strong></p>
 * <ul>
 *   <li>구현체는 thread-safe해야 합니다.</li>
 *   <li>getState()와 getOutcome()은 동시 호출 가능해야 합니다.</li>
 * </ul>
 *
 * <p><strong>사용 예시:</strong></p>
 * <pre>
 * // 1. 실행 시작 (비블로킹)
 * executor.execute(envelope);
 *
 * // 2. 상태 폴링
 * OperationState state = executor.getState(opId);
 * if (state.isTerminal()) {
 *     // 3. 결과 조회 (종료 상태일 때만)
 *     Outcome outcome = executor.getOutcome(opId);
 * }
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface Executor {

    /**
     * Envelope 실행 시작.
     *
     * <p>이 메서드는 비블로킹으로 즉시 반환되며,
     * 실제 실행은 백그라운드에서 비동기로 수행됩니다.</p>
     *
     * @param envelope 실행할 Envelope
     * @throws IllegalArgumentException envelope가 null이거나 유효하지 않은 경우
     */
    void execute(Envelope envelope);

    /**
     * Operation 상태 조회.
     *
     * <p>현재 Operation의 상태를 반환합니다.
     * 이 메서드는 thread-safe해야 합니다.</p>
     *
     * @param opId Operation ID
     * @return 현재 상태 (PENDING, IN_PROGRESS, COMPLETED, FAILED)
     * @throws IllegalArgumentException opId가 null인 경우
     * @throws IllegalStateException opId에 해당하는 Operation이 존재하지 않는 경우
     */
    OperationState getState(OpId opId);

    /**
     * Operation 결과 조회.
     *
     * <p><strong>주의:</strong> 이 메서드는 Operation이 종료 상태(COMPLETED, FAILED)일 때만 호출해야 합니다.</p>
     *
     * @param opId Operation ID
     * @return 실행 결과 (Ok, Retry, Fail)
     * @throws IllegalArgumentException opId가 null인 경우
     * @throws IllegalStateException opId에 해당하는 Operation이 존재하지 않거나 아직 종료되지 않은 경우
     */
    Outcome getOutcome(OpId opId);
}
