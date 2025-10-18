package com.ryuqq.orchestrator.core.model;

import com.ryuqq.orchestrator.core.contract.Command;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdempotencyKey Record 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class IdempotencyKeyTest {

    @Test
    void constructor_ValidValues_CreatesIdempotencyKey() {
        // Given
        Domain domain = Domain.of("ORDER");
        EventType eventType = EventType.of("CREATE");
        BizKey bizKey = BizKey.of("ORDER-123");
        IdemKey idemKey = IdemKey.of("idem-abc");

        // When
        IdempotencyKey key = new IdempotencyKey(domain, eventType, bizKey, idemKey);

        // Then
        assertNotNull(key);
        assertEquals(domain, key.domain());
        assertEquals(eventType, key.eventType());
        assertEquals(bizKey, key.bizKey());
        assertEquals(idemKey, key.idemKey());
    }

    @Test
    void constructor_NullDomain_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new IdempotencyKey(null, EventType.of("CREATE"), BizKey.of("123"), IdemKey.of("abc"))
        );
        assertTrue(exception.getMessage().contains("All fields are required"));
    }

    @Test
    void constructor_NullEventType_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> new IdempotencyKey(Domain.of("ORDER"), null, BizKey.of("123"), IdemKey.of("abc")));
    }

    @Test
    void constructor_NullBizKey_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> new IdempotencyKey(Domain.of("ORDER"), EventType.of("CREATE"), null, IdemKey.of("abc")));
    }

    @Test
    void constructor_NullIdemKey_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> new IdempotencyKey(Domain.of("ORDER"), EventType.of("CREATE"), BizKey.of("123"), null));
    }

    @Test
    void from_ValidCommand_CreatesIdempotencyKey() {
        // Given
        Command command = new Command(
            Domain.of("ORDER"),
            EventType.of("CREATE"),
            BizKey.of("ORDER-123"),
            IdemKey.of("idem-abc"),
            null
        );

        // When
        IdempotencyKey key = IdempotencyKey.from(command);

        // Then
        assertNotNull(key);
        assertEquals(command.domain(), key.domain());
        assertEquals(command.eventType(), key.eventType());
        assertEquals(command.bizKey(), key.bizKey());
        assertEquals(command.idemKey(), key.idemKey());
    }

    @Test
    void from_NullCommand_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> IdempotencyKey.from(null)
        );
        assertTrue(exception.getMessage().contains("Command cannot be null"));
    }

    @Test
    void equals_SameValues_ReturnsTrue() {
        // Given
        IdempotencyKey key1 = createTestKey();
        IdempotencyKey key2 = createTestKey();

        // When & Then
        assertEquals(key1, key2);
    }

    @Test
    void equals_DifferentIdemKey_ReturnsFalse() {
        // Given
        IdempotencyKey key1 = new IdempotencyKey(
            Domain.of("ORDER"),
            EventType.of("CREATE"),
            BizKey.of("123"),
            IdemKey.of("abc")
        );
        IdempotencyKey key2 = new IdempotencyKey(
            Domain.of("ORDER"),
            EventType.of("CREATE"),
            BizKey.of("123"),
            IdemKey.of("xyz")
        );

        // When & Then
        assertNotEquals(key1, key2);
    }

    @Test
    void hashCode_SameValues_ReturnsSameHashCode() {
        // Given
        IdempotencyKey key1 = createTestKey();
        IdempotencyKey key2 = createTestKey();

        // When & Then
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    private IdempotencyKey createTestKey() {
        return new IdempotencyKey(
            Domain.of("ORDER"),
            EventType.of("CREATE"),
            BizKey.of("ORDER-123"),
            IdemKey.of("idem-abc")
        );
    }
}
