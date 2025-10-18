package com.ryuqq.orchestrator.core.protection.noop;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.CircuitBreaker;
import com.ryuqq.orchestrator.core.protection.CircuitBreakerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NoOpCircuitBreaker 유닛 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
@DisplayName("NoOpCircuitBreaker 테스트")
class NoOpCircuitBreakerTest {

    @Test
    @DisplayName("tryAcquire() 는 항상 true를 반환한다")
    void tryAcquire_항상_true_반환() {
        // given
        CircuitBreaker cb = new NoOpCircuitBreaker();
        OpId opId = OpId.of("test-op");

        // when
        boolean result = cb.tryAcquire(opId);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("getState() 는 항상 CLOSED를 반환한다")
    void getState_항상_CLOSED_반환() {
        // given
        CircuitBreaker cb = new NoOpCircuitBreaker();

        // when
        CircuitBreakerState state = cb.getState();

        // then
        assertEquals(CircuitBreakerState.CLOSED, state);
    }

    @Test
    @DisplayName("recordSuccess() 는 예외 없이 실행된다")
    void recordSuccess_예외_없이_실행() {
        // given
        CircuitBreaker cb = new NoOpCircuitBreaker();
        OpId opId = OpId.of("test-op");

        // when & then
        assertDoesNotThrow(() -> cb.recordSuccess(opId));
    }

    @Test
    @DisplayName("recordFailure() 는 예외 없이 실행된다")
    void recordFailure_예외_없이_실행() {
        // given
        CircuitBreaker cb = new NoOpCircuitBreaker();
        OpId opId = OpId.of("test-op");
        Throwable error = new RuntimeException("test error");

        // when & then
        assertDoesNotThrow(() -> cb.recordFailure(opId, error));
    }

    @Test
    @DisplayName("reset() 은 예외 없이 실행된다")
    void reset_예외_없이_실행() {
        // given
        CircuitBreaker cb = new NoOpCircuitBreaker();

        // when & then
        assertDoesNotThrow(() -> cb.reset());
    }

    @Test
    @DisplayName("여러 번 호출해도 항상 동일한 동작을 한다")
    void 여러번_호출_동일_동작() {
        // given
        CircuitBreaker cb = new NoOpCircuitBreaker();
        OpId opId = OpId.of("test-op");

        // when & then
        for (int i = 0; i < 100; i++) {
            assertTrue(cb.tryAcquire(opId));
            assertEquals(CircuitBreakerState.CLOSED, cb.getState());
            assertDoesNotThrow(() -> cb.recordSuccess(opId));
            assertDoesNotThrow(() -> cb.recordFailure(opId, new RuntimeException()));
        }
    }
}
