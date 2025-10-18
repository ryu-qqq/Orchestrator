package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.HedgePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NoOpHedgePolicy 유닛 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
@DisplayName("NoOpHedgePolicy 테스트")
class NoOpHedgePolicyTest {

    @Test
    @DisplayName("shouldHedge() 는 항상 false를 반환한다")
    void shouldHedge_항상_false_반환() {
        // given
        HedgePolicy policy = new NoOpHedgePolicy();
        OpId opId = OpId.of("test-op");

        // when
        boolean result = policy.shouldHedge(opId);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("getHedgeDelayMs() 는 0을 반환한다")
    void getHedgeDelayMs_0_반환() {
        // given
        HedgePolicy policy = new NoOpHedgePolicy();
        OpId opId = OpId.of("test-op");

        // when
        long delay = policy.getHedgeDelayMs(opId);

        // then
        assertEquals(0, delay);
    }

    @Test
    @DisplayName("getMaxHedges() 는 0을 반환한다")
    void getMaxHedges_0_반환() {
        // given
        HedgePolicy policy = new NoOpHedgePolicy();
        OpId opId = OpId.of("test-op");

        // when
        int maxHedges = policy.getMaxHedges(opId);

        // then
        assertEquals(0, maxHedges);
    }

    @Test
    @DisplayName("recordHedgeAttempt() 는 예외 없이 실행된다")
    void recordHedgeAttempt_예외_없이_실행() {
        // given
        HedgePolicy policy = new NoOpHedgePolicy();
        OpId opId = OpId.of("test-op");

        // when & then
        assertDoesNotThrow(() -> policy.recordHedgeAttempt(opId, 1));
    }

    @Test
    @DisplayName("recordSuccess() 는 예외 없이 실행된다")
    void recordSuccess_예외_없이_실행() {
        // given
        HedgePolicy policy = new NoOpHedgePolicy();
        OpId opId = OpId.of("test-op");

        // when & then
        assertDoesNotThrow(() -> policy.recordSuccess(opId, false));
        assertDoesNotThrow(() -> policy.recordSuccess(opId, true));
    }

    @Test
    @DisplayName("여러 번 호출해도 항상 동일한 동작을 한다")
    void 여러번_호출_동일_동작() {
        // given
        HedgePolicy policy = new NoOpHedgePolicy();
        OpId opId = OpId.of("test-op");

        // when & then
        for (int i = 0; i < 100; i++) {
            assertFalse(policy.shouldHedge(opId));
            assertEquals(0, policy.getHedgeDelayMs(opId));
            assertEquals(0, policy.getMaxHedges(opId));

            final int hedgeNumber = i;
            assertDoesNotThrow(() -> policy.recordHedgeAttempt(opId, hedgeNumber));

            final boolean wasHedge = i % 2 == 0;
            assertDoesNotThrow(() -> policy.recordSuccess(opId, wasHedge));
        }
    }
}
