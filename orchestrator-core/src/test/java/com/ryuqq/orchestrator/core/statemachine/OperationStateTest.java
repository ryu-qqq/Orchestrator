package com.ryuqq.orchestrator.core.statemachine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OperationState Enum 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class OperationStateTest {

    @Test
    void isTerminal_Pending_ReturnsFalse() {
        // When & Then
        assertFalse(OperationState.PENDING.isTerminal());
    }

    @Test
    void isTerminal_InProgress_ReturnsFalse() {
        // When & Then
        assertFalse(OperationState.IN_PROGRESS.isTerminal());
    }

    @Test
    void isTerminal_Completed_ReturnsTrue() {
        // When & Then
        assertTrue(OperationState.COMPLETED.isTerminal());
    }

    @Test
    void isTerminal_Failed_ReturnsTrue() {
        // When & Then
        assertTrue(OperationState.FAILED.isTerminal());
    }

    @Test
    void values_ContainsAllStates() {
        // When
        OperationState[] states = OperationState.values();

        // Then
        assertEquals(4, states.length);
        assertTrue(contains(states, OperationState.PENDING));
        assertTrue(contains(states, OperationState.IN_PROGRESS));
        assertTrue(contains(states, OperationState.COMPLETED));
        assertTrue(contains(states, OperationState.FAILED));
    }

    @Test
    void valueOf_ValidName_ReturnsState() {
        // When & Then
        assertEquals(OperationState.PENDING, OperationState.valueOf("PENDING"));
        assertEquals(OperationState.IN_PROGRESS, OperationState.valueOf("IN_PROGRESS"));
        assertEquals(OperationState.COMPLETED, OperationState.valueOf("COMPLETED"));
        assertEquals(OperationState.FAILED, OperationState.valueOf("FAILED"));
    }

    @Test
    void valueOf_InvalidName_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> OperationState.valueOf("INVALID"));
    }

    private boolean contains(OperationState[] states, OperationState target) {
        for (OperationState state : states) {
            if (state == target) {
                return true;
            }
        }
        return false;
    }
}
