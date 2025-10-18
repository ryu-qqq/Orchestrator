package com.ryuqq.orchestrator.core.contract;

import com.ryuqq.orchestrator.core.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Command Record 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class CommandTest {

    @Test
    void constructor_ValidValuesWithPayload_CreatesCommand() {
        // Given
        Domain domain = Domain.of("ORDER");
        EventType eventType = EventType.of("CREATE");
        BizKey bizKey = BizKey.of("ORDER-123");
        IdemKey idemKey = IdemKey.of("idem-abc");
        Payload payload = Payload.of("{\"amount\":50000}");

        // When
        Command command = new Command(domain, eventType, bizKey, idemKey, payload);

        // Then
        assertNotNull(command);
        assertEquals(domain, command.domain());
        assertEquals(eventType, command.eventType());
        assertEquals(bizKey, command.bizKey());
        assertEquals(idemKey, command.idemKey());
        assertEquals(payload, command.payload());
    }

    @Test
    void constructor_ValidValuesWithNullPayload_CreatesCommand() {
        // Given
        Domain domain = Domain.of("ORDER");
        EventType eventType = EventType.of("CREATE");
        BizKey bizKey = BizKey.of("ORDER-123");
        IdemKey idemKey = IdemKey.of("idem-abc");

        // When
        Command command = new Command(domain, eventType, bizKey, idemKey, null);

        // Then
        assertNotNull(command);
        assertNull(command.payload());
    }

    @Test
    void constructor_NullDomain_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Command(null, EventType.of("CREATE"), BizKey.of("123"), IdemKey.of("abc"), null)
        );
        assertTrue(exception.getMessage().contains("domain cannot be null"));
    }

    @Test
    void constructor_NullEventType_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Command(Domain.of("ORDER"), null, BizKey.of("123"), IdemKey.of("abc"), null)
        );
        assertTrue(exception.getMessage().contains("eventType cannot be null"));
    }

    @Test
    void constructor_NullBizKey_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Command(Domain.of("ORDER"), EventType.of("CREATE"), null, IdemKey.of("abc"), null)
        );
        assertTrue(exception.getMessage().contains("bizKey cannot be null"));
    }

    @Test
    void constructor_NullIdemKey_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Command(Domain.of("ORDER"), EventType.of("CREATE"), BizKey.of("123"), null, null)
        );
        assertTrue(exception.getMessage().contains("idemKey cannot be null"));
    }

    @Test
    void equals_SameValues_ReturnsTrue() {
        // Given
        Command command1 = createTestCommand();
        Command command2 = createTestCommand();

        // When & Then
        assertEquals(command1, command2);
    }

    @Test
    void equals_DifferentDomain_ReturnsFalse() {
        // Given
        Command command1 = new Command(
            Domain.of("ORDER"),
            EventType.of("CREATE"),
            BizKey.of("123"),
            IdemKey.of("abc"),
            null
        );
        Command command2 = new Command(
            Domain.of("PAYMENT"),
            EventType.of("CREATE"),
            BizKey.of("123"),
            IdemKey.of("abc"),
            null
        );

        // When & Then
        assertNotEquals(command1, command2);
    }

    @Test
    void hashCode_SameValues_ReturnsSameHashCode() {
        // Given
        Command command1 = createTestCommand();
        Command command2 = createTestCommand();

        // When & Then
        assertEquals(command1.hashCode(), command2.hashCode());
    }

    private Command createTestCommand() {
        return new Command(
            Domain.of("ORDER"),
            EventType.of("CREATE"),
            BizKey.of("ORDER-123"),
            IdemKey.of("idem-abc"),
            Payload.of("{\"amount\":50000}")
        );
    }
}
