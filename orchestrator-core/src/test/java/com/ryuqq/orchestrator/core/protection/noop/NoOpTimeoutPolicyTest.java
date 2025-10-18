package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.TimeoutPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NoOpTimeoutPolicy 유닛 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
@DisplayName("NoOpTimeoutPolicy 테스트")
class NoOpTimeoutPolicyTest {

    @Test
    @DisplayName("getPerAttemptTimeoutMs() 는 항상 0을 반환한다")
    void getPerAttemptTimeoutMs_항상_0_반환() {
        // given
        TimeoutPolicy policy = new NoOpTimeoutPolicy();
        OpId opId = OpId.of("test-op");

        // when
        long timeout = policy.getPerAttemptTimeoutMs(opId);

        // then
        assertEquals(0, timeout);
    }

    @Test
    @DisplayName("recordTimeout() 은 예외 없이 실행된다")
    void recordTimeout_예외_없이_실행() {
        // given
        TimeoutPolicy policy = new NoOpTimeoutPolicy();
        OpId opId = OpId.of("test-op");

        // when & then
        assertDoesNotThrow(() -> policy.recordTimeout(opId, 1000));
    }

    @Test
    @DisplayName("여러 번 호출해도 항상 0을 반환한다")
    void 여러번_호출_항상_0_반환() {
        // given
        TimeoutPolicy policy = new NoOpTimeoutPolicy();
        OpId opId = OpId.of("test-op");

        // when & then
        for (int i = 0; i < 100; i++) {
            assertEquals(0, policy.getPerAttemptTimeoutMs(opId));

            final long elapsed = i * 100L;
            assertDoesNotThrow(() -> policy.recordTimeout(opId, elapsed));
        }
    }
}
