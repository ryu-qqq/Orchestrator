package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.RateLimiter;
import com.ryuqq.orchestrator.core.protection.RateLimiterConfig;

/**
 * Rate Limiter NoOp 구현.
 *
 * <p>모든 요청을 항상 허용합니다.
 * 개발 및 테스트 환경에서 사용하거나, Rate Limiting 없이 실행하고자 할 때 사용합니다.</p>
 *
 * <p><strong>동작 방식:</strong></p>
 * <ul>
 *   <li>tryAcquire(): 항상 true 반환</li>
 *   <li>tryAcquire(timeout): 항상 true 반환</li>
 *   <li>getConfig(): 무제한 설정 반환</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class NoOpRateLimiter implements RateLimiter {

    private static final RateLimiterConfig UNLIMITED_CONFIG =
        new RateLimiterConfig(Double.MAX_VALUE, Integer.MAX_VALUE);

    @Override
    public boolean tryAcquire(OpId opId) {
        return true;
    }

    @Override
    public boolean tryAcquire(OpId opId, long timeoutMs) {
        return true;
    }

    @Override
    public RateLimiterConfig getConfig() {
        return UNLIMITED_CONFIG;
    }
}
