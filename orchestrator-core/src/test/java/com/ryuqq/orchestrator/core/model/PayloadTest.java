package com.ryuqq.orchestrator.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Payload Value Object 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class PayloadTest {

    @Test
    void of_ValidJsonValue_CreatesPayload() {
        // Given
        String jsonValue = "{\"orderId\":123,\"amount\":50000}";

        // When
        Payload payload = Payload.of(jsonValue);

        // Then
        assertNotNull(payload);
        assertEquals(jsonValue, payload.getValue());
        assertFalse(payload.isEmpty());
    }

    @Test
    void of_NullValue_CreatesPayload() {
        // When
        Payload payload = Payload.of(null);

        // Then
        assertNotNull(payload);
        assertNull(payload.getValue());
        assertTrue(payload.isEmpty());
    }

    @Test
    void of_EmptyString_CreatesPayload() {
        // When
        Payload payload = Payload.of("");

        // Then
        assertNotNull(payload);
        assertEquals("", payload.getValue());
        assertTrue(payload.isEmpty());
    }

    @Test
    void empty_CreatesEmptyPayload() {
        // When
        Payload payload = Payload.empty();

        // Then
        assertNotNull(payload);
        assertEquals("", payload.getValue());
        assertTrue(payload.isEmpty());
    }

    @Test
    void isEmpty_NullValue_ReturnsTrue() {
        // Given
        Payload payload = Payload.of(null);

        // When & Then
        assertTrue(payload.isEmpty());
    }

    @Test
    void isEmpty_EmptyString_ReturnsTrue() {
        // Given
        Payload payload = Payload.of("");

        // When & Then
        assertTrue(payload.isEmpty());
    }

    @Test
    void isEmpty_NonEmptyValue_ReturnsFalse() {
        // Given
        Payload payload = Payload.of("data");

        // When & Then
        assertFalse(payload.isEmpty());
    }

    @Test
    void equals_SameValue_ReturnsTrue() {
        // Given
        Payload payload1 = Payload.of("data");
        Payload payload2 = Payload.of("data");

        // When & Then
        assertEquals(payload1, payload2);
    }

    @Test
    void equals_BothNull_ReturnsTrue() {
        // Given
        Payload payload1 = Payload.of(null);
        Payload payload2 = Payload.of(null);

        // When & Then
        assertEquals(payload1, payload2);
    }

    @Test
    void equals_DifferentValue_ReturnsFalse() {
        // Given
        Payload payload1 = Payload.of("data1");
        Payload payload2 = Payload.of("data2");

        // When & Then
        assertNotEquals(payload1, payload2);
    }

    @Test
    void hashCode_SameValue_ReturnsSameHashCode() {
        // Given
        Payload payload1 = Payload.of("data");
        Payload payload2 = Payload.of("data");

        // When & Then
        assertEquals(payload1.hashCode(), payload2.hashCode());
    }

    @Test
    void hashCode_NullValue_ReturnsZero() {
        // Given
        Payload payload = Payload.of(null);

        // When & Then
        assertEquals(0, payload.hashCode());
    }

    @Test
    void toString_NonNullValue_ContainsLength() {
        // Given
        Payload payload = Payload.of("test");

        // When
        String result = payload.toString();

        // Then
        assertTrue(result.contains("4 chars"));
        assertTrue(result.contains("Payload"));
    }

    @Test
    void toString_NullValue_ContainsNull() {
        // Given
        Payload payload = Payload.of(null);

        // When
        String result = payload.toString();

        // Then
        assertTrue(result.contains("null"));
    }
}
