package com.ryuqq.orchestrator.core.outcome;

import com.ryuqq.orchestrator.core.model.OpId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ok Record 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class OkTest {

    @Test
    void constructor_ValidOpIdAndMessage_CreatesOk() {
        // Given
        OpId opId = OpId.of("op-123");
        String message = "Success";

        // When
        Ok ok = new Ok(opId, message);

        // Then
        assertNotNull(ok);
        assertEquals(opId, ok.opId());
        assertEquals(message, ok.message());
    }

    @Test
    void constructor_ValidOpIdNullMessage_CreatesOk() {
        // Given
        OpId opId = OpId.of("op-123");

        // When
        Ok ok = new Ok(opId, null);

        // Then
        assertNotNull(ok);
        assertEquals(opId, ok.opId());
        assertNull(ok.message());
    }

    @Test
    void constructor_NullOpId_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Ok(null, "message")
        );
        assertTrue(exception.getMessage().contains("opId cannot be null"));
    }

    @Test
    void of_ValidOpId_CreatesOkWithoutMessage() {
        // Given
        OpId opId = OpId.of("op-123");

        // When
        Ok ok = Ok.of(opId);

        // Then
        assertNotNull(ok);
        assertEquals(opId, ok.opId());
        assertNull(ok.message());
    }

    @Test
    void of_NullOpId_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> Ok.of(null));
    }

    @Test
    void equals_SameValues_ReturnsTrue() {
        // Given
        OpId opId = OpId.of("op-123");
        Ok ok1 = new Ok(opId, "message");
        Ok ok2 = new Ok(opId, "message");

        // When & Then
        assertEquals(ok1, ok2);
    }

    @Test
    void equals_DifferentOpId_ReturnsFalse() {
        // Given
        Ok ok1 = new Ok(OpId.of("op-123"), "message");
        Ok ok2 = new Ok(OpId.of("op-456"), "message");

        // When & Then
        assertNotEquals(ok1, ok2);
    }

    @Test
    void hashCode_SameValues_ReturnsSameHashCode() {
        // Given
        OpId opId = OpId.of("op-123");
        Ok ok1 = new Ok(opId, "message");
        Ok ok2 = new Ok(opId, "message");

        // When & Then
        assertEquals(ok1.hashCode(), ok2.hashCode());
    }
}
