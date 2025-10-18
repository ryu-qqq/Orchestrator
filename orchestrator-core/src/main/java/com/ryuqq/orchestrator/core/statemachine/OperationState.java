package com.ryuqq.orchestrator.core.statemachine;

/**
 * Operation의 생명주기 상태.
 *
 * <p><strong>상태 전이 규칙:</strong></p>
 * <ul>
 *   <li>PENDING → IN_PROGRESS (수락)</li>
 *   <li>IN_PROGRESS → COMPLETED (성공)</li>
 *   <li>IN_PROGRESS → FAILED (실패)</li>
 *   <li><strong>역방향 전이 불가 (불변식)</strong></li>
 * </ul>
 *
 * <p><strong>상태 전이 다이어그램:</strong></p>
 * <pre>
 * PENDING
 *    │
 *    ▼ (수락)
 * IN_PROGRESS
 *    │
 *    ├─► COMPLETED (성공)
 *    │
 *    └─► FAILED (실패)
 *
 * 금지된 전이:
 * - COMPLETED → IN_PROGRESS ❌
 * - COMPLETED → PENDING ❌
 * - FAILED → IN_PROGRESS ❌
 * - FAILED → PENDING ❌
 * - COMPLETED ↔ FAILED ❌
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public enum OperationState {

    /**
     * 대기 중 (아직 실행 시작 안 됨).
     */
    PENDING,

    /**
     * 실행 중.
     */
    IN_PROGRESS,

    /**
     * 완료 (성공).
     */
    COMPLETED,

    /**
     * 실패 (영구).
     */
    FAILED;

    /**
     * 종료 상태인지 확인.
     *
     * <p>종료 상태(COMPLETED, FAILED)에서는 더 이상 다른 상태로 전이할 수 없습니다.</p>
     *
     * @return COMPLETED 또는 FAILED인 경우 true
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
