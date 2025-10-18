package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.TimeoutPolicy;

/**
 * Timeout Policy NoOp 구현.
 *
 * <p>타임아웃을 적용하지 않습니다 (0 반환).
 * 개발 및 테스트 환경에서 사용하거나, 타임아웃 없이 실행하고자 할 때 사용합니다.</p>
 *
 * <p><strong>동작 방식:</strong></p>
 * <ul>
 *   <li>getPerAttemptTimeoutMs(): 항상 0 반환 (타임아웃 없음)</li>
 *   <li>recordTimeout(): 아무 동작 안 함</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpTimeoutPolicy implements TimeoutPolicy {

    @Override
    public long getPerAttemptTimeoutMs(OpId opId) {
        return 0;
    }

    @Override
    public void recordTimeout(OpId opId, long elapsedMs) {
        // NoOp
    }
}
