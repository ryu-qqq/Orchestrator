package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.CircuitBreaker;
import com.ryuqq.orchestrator.core.protection.CircuitBreakerState;

/**
 * Circuit Breaker NoOp 구현.
 *
 * <p>모든 요청을 항상 허용하며, 상태 추적을 하지 않습니다.
 * 개발 및 테스트 환경에서 사용하거나, 보호 없이 실행하고자 할 때 사용합니다.</p>
 *
 * <p><strong>동작 방식:</strong></p>
 * <ul>
 *   <li>tryAcquire(): 항상 true 반환</li>
 *   <li>recordSuccess(): 아무 동작 안 함</li>
 *   <li>recordFailure(): 아무 동작 안 함</li>
 *   <li>getState(): 항상 CLOSED 반환</li>
 *   <li>reset(): 아무 동작 안 함</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpCircuitBreaker implements CircuitBreaker {

    @Override
    public boolean tryAcquire(OpId opId) {
        return true;
    }

    @Override
    public void recordSuccess(OpId opId) {
        // NoOp
    }

    @Override
    public void recordFailure(OpId opId, Throwable throwable) {
        // NoOp
    }

    @Override
    public CircuitBreakerState getState() {
        return CircuitBreakerState.CLOSED;
    }

    @Override
    public void reset() {
        // NoOp
    }
}
