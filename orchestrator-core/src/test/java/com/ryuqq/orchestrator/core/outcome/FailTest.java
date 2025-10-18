package com.ryuqq.orchestrator.core.outcome;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fail Record 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class FailTest {

    @Test
    void constructor_ValidValues_CreatesFail() {
        // Given
        String errorCode = "PAY-001";
        String message = "Payment failed";
        String cause = "Insufficient funds";

        // When
        Fail fail = new Fail(errorCode, message, cause);

        // Then
        assertNotNull(fail);
        assertEquals(errorCode, fail.errorCode());
        assertEquals(message, fail.message());
        assertEquals(cause, fail.cause());
    }

    @Test
    void constructor_NullCause_CreatesFail() {
        // Given
        String errorCode = "PAY-001";
        String message = "Payment failed";

        // When
        Fail fail = new Fail(errorCode, message, null);

        // Then
        assertNotNull(fail);
        assertEquals(errorCode, fail.errorCode());
        assertEquals(message, fail.message());
        assertNull(fail.cause());
    }

    @Test
    void constructor_NullErrorCode_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Fail(null, "message", "cause")
        );
        assertTrue(exception.getMessage().contains("errorCode cannot be null"));
    }

    @Test
    void constructor_BlankErrorCode_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Fail("   ", "message", "cause")
        );
        assertTrue(exception.getMessage().contains("errorCode cannot be null or blank"));
    }

    @Test
    void constructor_NullMessage_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Fail("ERR-001", null, "cause")
        );
        assertTrue(exception.getMessage().contains("message cannot be null"));
    }

    @Test
    void constructor_BlankMessage_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Fail("ERR-001", "   ", "cause")
        );
        assertTrue(exception.getMessage().contains("message cannot be null or blank"));
    }

    @Test
    void of_ValidErrorCodeAndMessage_CreatesFailWithoutCause() {
        // Given
        String errorCode = "PAY-001";
        String message = "Payment failed";

        // When
        Fail fail = Fail.of(errorCode, message);

        // Then
        assertNotNull(fail);
        assertEquals(errorCode, fail.errorCode());
        assertEquals(message, fail.message());
        assertNull(fail.cause());
    }

    @Test
    void of_NullErrorCode_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> Fail.of(null, "message"));
    }

    @Test
    void of_NullMessage_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> Fail.of("ERR-001", null));
    }

    @Test
    void equals_SameValues_ReturnsTrue() {
        // Given
        Fail fail1 = new Fail("ERR-001", "error", "cause");
        Fail fail2 = new Fail("ERR-001", "error", "cause");

        // When & Then
        assertEquals(fail1, fail2);
    }

    @Test
    void equals_DifferentErrorCode_ReturnsFalse() {
        // Given
        Fail fail1 = new Fail("ERR-001", "error", "cause");
        Fail fail2 = new Fail("ERR-002", "error", "cause");

        // When & Then
        assertNotEquals(fail1, fail2);
    }

    @Test
    void hashCode_SameValues_ReturnsSameHashCode() {
        // Given
        Fail fail1 = new Fail("ERR-001", "error", "cause");
        Fail fail2 = new Fail("ERR-001", "error", "cause");

        // When & Then
        assertEquals(fail1.hashCode(), fail2.hashCode());
    }
}
