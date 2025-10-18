package com.ryuqq.orchestrator.core.outcome;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Retry Record 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class RetryTest {

    @Test
    void constructor_ValidValues_CreatesRetry() {
        // Given
        String reason = "Network timeout";
        int attemptCount = 3;
        long nextRetryAfterMillis = 5000;

        // When
        Retry retry = new Retry(reason, attemptCount, nextRetryAfterMillis);

        // Then
        assertNotNull(retry);
        assertEquals(reason, retry.reason());
        assertEquals(attemptCount, retry.attemptCount());
        assertEquals(nextRetryAfterMillis, retry.nextRetryAfterMillis());
    }

    @Test
    void constructor_NullReason_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Retry(null, 1, 1000)
        );
        assertTrue(exception.getMessage().contains("reason cannot be null"));
    }

    @Test
    void constructor_BlankReason_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Retry("   ", 1, 1000)
        );
        assertTrue(exception.getMessage().contains("reason cannot be null or blank"));
    }

    @Test
    void constructor_ZeroAttemptCount_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Retry("timeout", 0, 1000)
        );
        assertTrue(exception.getMessage().contains("attemptCount must be positive"));
    }

    @Test
    void constructor_NegativeAttemptCount_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Retry("timeout", -1, 1000)
        );
        assertTrue(exception.getMessage().contains("attemptCount must be positive"));
    }

    @Test
    void constructor_NegativeNextRetryAfterMillis_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Retry("timeout", 1, -1)
        );
        assertTrue(exception.getMessage().contains("nextRetryAfterMillis must be non-negative"));
    }

    @Test
    void constructor_ZeroNextRetryAfterMillis_CreatesRetry() {
        // When
        Retry retry = new Retry("immediate retry", 1, 0);

        // Then
        assertNotNull(retry);
        assertEquals(0, retry.nextRetryAfterMillis());
    }

    @Test
    void equals_SameValues_ReturnsTrue() {
        // Given
        Retry retry1 = new Retry("timeout", 3, 5000);
        Retry retry2 = new Retry("timeout", 3, 5000);

        // When & Then
        assertEquals(retry1, retry2);
    }

    @Test
    void equals_DifferentReason_ReturnsFalse() {
        // Given
        Retry retry1 = new Retry("timeout", 3, 5000);
        Retry retry2 = new Retry("rate limit", 3, 5000);

        // When & Then
        assertNotEquals(retry1, retry2);
    }

    @Test
    void hashCode_SameValues_ReturnsSameHashCode() {
        // Given
        Retry retry1 = new Retry("timeout", 3, 5000);
        Retry retry2 = new Retry("timeout", 3, 5000);

        // When & Then
        assertEquals(retry1.hashCode(), retry2.hashCode());
    }
}
