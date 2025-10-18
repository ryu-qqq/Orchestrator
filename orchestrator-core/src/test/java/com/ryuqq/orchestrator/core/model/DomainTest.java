package com.ryuqq.orchestrator.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain Value Object 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class DomainTest {

    @Test
    void of_ValidUppercaseValue_CreatesDomain() {
        // Given
        String value = "ORDER";

        // When
        Domain domain = Domain.of(value);

        // Then
        assertNotNull(domain);
        assertEquals(value, domain.getValue());
    }

    @Test
    void of_ValidValueWithUnderscore_CreatesDomain() {
        // Given
        String value = "FILE_UPLOAD";

        // When
        Domain domain = Domain.of(value);

        // Then
        assertEquals(value, domain.getValue());
    }

    @Test
    void of_NullValue_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Domain.of(null)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void of_BlankValue_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> Domain.of("   "));
    }

    @Test
    void of_ValueExceeds50Characters_ThrowsException() {
        // Given
        String value = "A".repeat(51);

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Domain.of(value)
        );
        assertTrue(exception.getMessage().contains("cannot exceed 50"));
    }

    @Test
    void of_LowercaseValue_ThrowsException() {
        // Given
        String value = "order";

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Domain.of(value)
        );
        assertTrue(exception.getMessage().contains("uppercase letters and underscores"));
    }

    @Test
    void of_MixedCaseValue_ThrowsException() {
        // Given
        String value = "Order";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> Domain.of(value));
    }

    @Test
    void of_ValueWithHyphen_ThrowsException() {
        // Given
        String value = "ORDER-PAYMENT";

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Domain.of(value)
        );
        assertTrue(exception.getMessage().contains("uppercase letters and underscores"));
    }

    @Test
    void equals_SameValue_ReturnsTrue() {
        // Given
        Domain domain1 = Domain.of("ORDER");
        Domain domain2 = Domain.of("ORDER");

        // When & Then
        assertEquals(domain1, domain2);
    }

    @Test
    void equals_DifferentValue_ReturnsFalse() {
        // Given
        Domain domain1 = Domain.of("ORDER");
        Domain domain2 = Domain.of("PAYMENT");

        // When & Then
        assertNotEquals(domain1, domain2);
    }

    @Test
    void hashCode_SameValue_ReturnsSameHashCode() {
        // Given
        Domain domain1 = Domain.of("ORDER");
        Domain domain2 = Domain.of("ORDER");

        // When & Then
        assertEquals(domain1.hashCode(), domain2.hashCode());
    }
}
