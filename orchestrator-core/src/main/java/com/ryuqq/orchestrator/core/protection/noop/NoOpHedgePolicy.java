package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.HedgePolicy;

/**
 * Hedge Policy NoOp 구현.
 *
 * <p>Hedging을 적용하지 않습니다.
 * 개발 및 테스트 환경에서 사용하거나, Hedging 없이 실행하고자 할 때 사용합니다.</p>
 *
 * <p><strong>동작 방식:</strong></p>
 * <ul>
 *   <li>shouldHedge(): 항상 false 반환 (Hedging 비활성화)</li>
 *   <li>getHedgeDelayMs(): 0 반환</li>
 *   <li>getMaxHedges(): 0 반환</li>
 *   <li>recordHedgeAttempt(): 아무 동작 안 함</li>
 *   <li>recordSuccess(): 아무 동작 안 함</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpHedgePolicy implements HedgePolicy {

    @Override
    public boolean shouldHedge(OpId opId) {
        return false;
    }

    @Override
    public long getHedgeDelayMs(OpId opId) {
        return 0;
    }

    @Override
    public int getMaxHedges(OpId opId) {
        return 0;
    }

    @Override
    public void recordHedgeAttempt(OpId opId, int hedgeNumber) {
        // NoOp
    }

    @Override
    public void recordSuccess(OpId opId, boolean wasHedge) {
        // NoOp
    }
}
