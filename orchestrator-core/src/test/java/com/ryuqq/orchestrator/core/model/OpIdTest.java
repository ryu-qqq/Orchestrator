package com.ryuqq.orchestrator.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpId Value Object 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class OpIdTest {

    @Test
    void of_ValidValue_CreatesOpId() {
        // Given
        String value = "op-12345";

        // When
        OpId opId = OpId.of(value);

        // Then
        assertNotNull(opId);
        assertEquals(value, opId.getValue());
    }

    @Test
    void of_ValidValueWithHyphens_CreatesOpId() {
        // Given
        String value = "op-order-123-payment-456";

        // When
        OpId opId = OpId.of(value);

        // Then
        assertEquals(value, opId.getValue());
    }

    @Test
    void of_ValidValueWithUnderscores_CreatesOpId() {
        // Given
        String value = "op_order_123_payment_456";

        // When
        OpId opId = OpId.of(value);

        // Then
        assertEquals(value, opId.getValue());
    }

    @Test
    void of_NullValue_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> OpId.of(null)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void of_BlankValue_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> OpId.of("   ")
        );
        assertTrue(exception.getMessage().contains("cannot be null or blank"));
    }

    @Test
    void of_EmptyValue_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> OpId.of("")
        );
        assertTrue(exception.getMessage().contains("cannot be null or blank"));
    }

    @Test
    void of_ValueExceeds255Characters_ThrowsException() {
        // Given
        String value = "a".repeat(256);

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> OpId.of(value)
        );
        assertTrue(exception.getMessage().contains("cannot exceed 255"));
    }

    @Test
    void of_ValueWithInvalidCharacters_ThrowsException() {
        // Given: 공백 포함
        String valueWithSpace = "op id 123";

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> OpId.of(valueWithSpace)
        );
        assertTrue(exception.getMessage().contains("invalid characters"));
    }

    @Test
    void of_ValueWithSpecialCharacters_ThrowsException() {
        // Given: 특수문자 포함
        String valueWithSpecial = "op@id#123";

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> OpId.of(valueWithSpecial)
        );
        assertTrue(exception.getMessage().contains("invalid characters"));
    }

    @Test
    void equals_SameValue_ReturnsTrue() {
        // Given
        OpId opId1 = OpId.of("op-123");
        OpId opId2 = OpId.of("op-123");

        // When & Then
        assertEquals(opId1, opId2);
    }

    @Test
    void equals_DifferentValue_ReturnsFalse() {
        // Given
        OpId opId1 = OpId.of("op-123");
        OpId opId2 = OpId.of("op-456");

        // When & Then
        assertNotEquals(opId1, opId2);
    }

    @Test
    void equals_SameInstance_ReturnsTrue() {
        // Given
        OpId opId = OpId.of("op-123");

        // When & Then
        assertEquals(opId, opId);
    }

    @Test
    void equals_NullObject_ReturnsFalse() {
        // Given
        OpId opId = OpId.of("op-123");

        // When & Then
        assertNotEquals(null, opId);
    }

    @Test
    void hashCode_SameValue_ReturnsSameHashCode() {
        // Given
        OpId opId1 = OpId.of("op-123");
        OpId opId2 = OpId.of("op-123");

        // When & Then
        assertEquals(opId1.hashCode(), opId2.hashCode());
    }

    @Test
    void toString_ContainsValue() {
        // Given
        String value = "op-123";
        OpId opId = OpId.of(value);

        // When
        String result = opId.toString();

        // Then
        assertTrue(result.contains(value));
        assertTrue(result.contains("OpId"));
    }

    @Test
    void immutability_ValueCannotBeChanged() {
        // Given
        OpId opId = OpId.of("op-123");
        String originalValue = opId.getValue();

        // When: getValue()로 얻은 값 참조
        String retrievedValue = opId.getValue();

        // Then: 원본과 동일 (String은 불변이므로 자동 보장)
        assertEquals(originalValue, retrievedValue);
        assertEquals(originalValue, opId.getValue());
    }
}
