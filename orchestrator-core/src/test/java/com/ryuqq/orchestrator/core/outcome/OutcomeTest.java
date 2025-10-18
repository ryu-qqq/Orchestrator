package com.ryuqq.orchestrator.core.outcome;

import com.ryuqq.orchestrator.core.model.OpId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outcome Sealed Interface 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class OutcomeTest {

    @Test
    void ok_IsOk_ReturnsTrue() {
        // Given
        Outcome outcome = Ok.of(OpId.of("op-123"));

        // When & Then
        assertTrue(outcome.isOk());
        assertFalse(outcome.isRetry());
        assertFalse(outcome.isFail());
    }

    @Test
    void retry_IsRetry_ReturnsTrue() {
        // Given
        Outcome outcome = new Retry("timeout", 1, 1000);

        // When & Then
        assertFalse(outcome.isOk());
        assertTrue(outcome.isRetry());
        assertFalse(outcome.isFail());
    }

    @Test
    void fail_IsFail_ReturnsTrue() {
        // Given
        Outcome outcome = Fail.of("ERR-001", "error message");

        // When & Then
        assertFalse(outcome.isOk());
        assertFalse(outcome.isRetry());
        assertTrue(outcome.isFail());
    }

    @Test
    void patternMatching_ExhaustiveSwitch_HandlesAllCases() {
        // Given
        Outcome ok = Ok.of(OpId.of("op-1"));
        Outcome retry = new Retry("timeout", 1, 1000);
        Outcome fail = Fail.of("ERR-001", "error");

        // When & Then
        String okResult = switch (ok) {
            case Ok o -> "ok";
            case Retry r -> "retry";
            case Fail f -> "fail";
        };
        assertEquals("ok", okResult);

        String retryResult = switch (retry) {
            case Ok o -> "ok";
            case Retry r -> "retry";
            case Fail f -> "fail";
        };
        assertEquals("retry", retryResult);

        String failResult = switch (fail) {
            case Ok o -> "ok";
            case Retry r -> "retry";
            case Fail f -> "fail";
        };
        assertEquals("fail", failResult);
    }

    @Test
    void patternMatching_WithDataExtraction_Works() {
        // Given
        Outcome outcome = new Retry("network timeout", 3, 5000);

        // When
        String result = switch (outcome) {
            case Ok ok -> "Success: " + ok.message();
            case Retry retry -> "Retry #" + retry.attemptCount() + " after " + retry.nextRetryAfterMillis() + "ms";
            case Fail fail -> "Failed: " + fail.errorCode();
        };

        // Then
        assertEquals("Retry #3 after 5000ms", result);
    }

    @Test
    void instanceofCheck_WorksCorrectly() {
        // Given
        Outcome ok = Ok.of(OpId.of("op-1"));
        Outcome retry = new Retry("timeout", 1, 1000);
        Outcome fail = Fail.of("ERR-001", "error");

        // When & Then
        assertTrue(ok instanceof Ok);
        assertFalse(ok instanceof Retry);
        assertFalse(ok instanceof Fail);

        assertTrue(retry instanceof Retry);
        assertFalse(retry instanceof Ok);

        assertTrue(fail instanceof Fail);
    }
}
