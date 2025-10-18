package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.Bulkhead;
import com.ryuqq.orchestrator.core.protection.BulkheadConfig;

/**
 * Bulkhead NoOp 구현.
 *
 * <p>동시 실행 수 제한을 적용하지 않습니다.
 * 개발 및 테스트 환경에서 사용하거나, Bulkhead 없이 실행하고자 할 때 사용합니다.</p>
 *
 * <p><strong>동작 방식:</strong></p>
 * <ul>
 *   <li>tryAcquire(): 항상 true 반환</li>
 *   <li>tryAcquire(timeout): 항상 true 반환</li>
 *   <li>release(): 아무 동작 안 함</li>
 *   <li>getCurrentConcurrency(): 항상 0 반환</li>
 *   <li>getConfig(): 무제한 설정 반환</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpBulkhead implements Bulkhead {

    private static final BulkheadConfig UNLIMITED_CONFIG =
        new BulkheadConfig(Integer.MAX_VALUE, 0);

    @Override
    public boolean tryAcquire(OpId opId) {
        return true;
    }

    @Override
    public boolean tryAcquire(OpId opId, long timeoutMs) {
        return true;
    }

    @Override
    public void release(OpId opId) {
        // NoOp
    }

    @Override
    public int getCurrentConcurrency() {
        return 0;
    }

    @Override
    public BulkheadConfig getConfig() {
        return UNLIMITED_CONFIG;
    }
}
