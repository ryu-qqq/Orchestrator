package com.ryuqq.orchestrator.core.contract;

import com.ryuqq.orchestrator.core.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Envelope Record 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class EnvelopeTest {

    @Test
    void constructor_ValidValues_CreatesEnvelope() {
        // Given
        OpId opId = OpId.of("op-123");
        Command command = createTestCommand();
        long acceptedAt = System.currentTimeMillis();

        // When
        Envelope envelope = new Envelope(opId, command, acceptedAt);

        // Then
        assertNotNull(envelope);
        assertEquals(opId, envelope.opId());
        assertEquals(command, envelope.command());
        assertEquals(acceptedAt, envelope.acceptedAt());
    }

    @Test
    void constructor_NullOpId_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Envelope(null, createTestCommand(), System.currentTimeMillis())
        );
        assertTrue(exception.getMessage().contains("opId cannot be null"));
    }

    @Test
    void constructor_NullCommand_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Envelope(OpId.of("op-123"), null, System.currentTimeMillis())
        );
        assertTrue(exception.getMessage().contains("command cannot be null"));
    }

    @Test
    void constructor_NegativeAcceptedAt_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Envelope(OpId.of("op-123"), createTestCommand(), -1)
        );
        assertTrue(exception.getMessage().contains("acceptedAt must be non-negative"));
    }

    @Test
    void constructor_ZeroAcceptedAt_CreatesEnvelope() {
        // When
        Envelope envelope = new Envelope(OpId.of("op-123"), createTestCommand(), 0);

        // Then
        assertNotNull(envelope);
        assertEquals(0, envelope.acceptedAt());
    }

    @Test
    void now_ValidValues_CreatesEnvelopeWithCurrentTime() {
        // Given
        OpId opId = OpId.of("op-123");
        Command command = createTestCommand();
        long beforeCreation = System.currentTimeMillis();

        // When
        Envelope envelope = Envelope.now(opId, command);
        long afterCreation = System.currentTimeMillis();

        // Then
        assertNotNull(envelope);
        assertEquals(opId, envelope.opId());
        assertEquals(command, envelope.command());
        assertTrue(envelope.acceptedAt() >= beforeCreation);
        assertTrue(envelope.acceptedAt() <= afterCreation);
    }

    @Test
    void now_NullOpId_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> Envelope.now(null, createTestCommand()));
    }

    @Test
    void now_NullCommand_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> Envelope.now(OpId.of("op-123"), null));
    }

    @Test
    void equals_SameValues_ReturnsTrue() {
        // Given
        OpId opId = OpId.of("op-123");
        Command command = createTestCommand();
        long acceptedAt = 1234567890L;

        Envelope envelope1 = new Envelope(opId, command, acceptedAt);
        Envelope envelope2 = new Envelope(opId, command, acceptedAt);

        // When & Then
        assertEquals(envelope1, envelope2);
    }

    @Test
    void equals_DifferentOpId_ReturnsFalse() {
        // Given
        Command command = createTestCommand();
        long acceptedAt = 1234567890L;

        Envelope envelope1 = new Envelope(OpId.of("op-123"), command, acceptedAt);
        Envelope envelope2 = new Envelope(OpId.of("op-456"), command, acceptedAt);

        // When & Then
        assertNotEquals(envelope1, envelope2);
    }

    @Test
    void equals_DifferentAcceptedAt_ReturnsFalse() {
        // Given
        OpId opId = OpId.of("op-123");
        Command command = createTestCommand();

        Envelope envelope1 = new Envelope(opId, command, 1000L);
        Envelope envelope2 = new Envelope(opId, command, 2000L);

        // When & Then
        assertNotEquals(envelope1, envelope2);
    }

    @Test
    void hashCode_SameValues_ReturnsSameHashCode() {
        // Given
        OpId opId = OpId.of("op-123");
        Command command = createTestCommand();
        long acceptedAt = 1234567890L;

        Envelope envelope1 = new Envelope(opId, command, acceptedAt);
        Envelope envelope2 = new Envelope(opId, command, acceptedAt);

        // When & Then
        assertEquals(envelope1.hashCode(), envelope2.hashCode());
    }

    private Command createTestCommand() {
        return new Command(
            Domain.of("ORDER"),
            EventType.of("CREATE"),
            BizKey.of("ORDER-123"),
            IdemKey.of("idem-abc"),
            null
        );
    }
}
