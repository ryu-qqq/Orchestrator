package com.ryuqq.orchestrator.core.statemachine;

import org.junit.jupiter.api.Test;

import static com.ryuqq.orchestrator.core.statemachine.OperationState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * StateTransition 테스트.
 *
 * <p>PRD Acceptance Criteria 3: 상태 전이 테스트</p>
 * <ul>
 *   <li>COMPLETED → IN_PROGRESS 시도 시 IllegalStateException</li>
 *   <li>FAILED → IN_PROGRESS 시도 시 IllegalStateException</li>
 *   <li>COMPLETED → FAILED 시도 시 IllegalStateException</li>
 *   <li>FAILED → COMPLETED 시도 시 IllegalStateException</li>
 *   <li>정상 전이 (PENDING → IN_PROGRESS → COMPLETED) 성공</li>
 *   <li>정상 전이 (PENDING → IN_PROGRESS → FAILED) 성공</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class StateTransitionTest {

    // ========== 정상 전이 테스트 ==========

    @Test
    void validate_PendingToInProgress_Succeeds() {
        // When & Then
        assertDoesNotThrow(() -> StateTransition.validate(PENDING, IN_PROGRESS));
    }

    @Test
    void validate_InProgressToCompleted_Succeeds() {
        // When & Then
        assertDoesNotThrow(() -> StateTransition.validate(IN_PROGRESS, COMPLETED));
    }

    @Test
    void validate_InProgressToFailed_Succeeds() {
        // When & Then
        assertDoesNotThrow(() -> StateTransition.validate(IN_PROGRESS, FAILED));
    }

    @Test
    void transition_NormalFlowToCompleted_Succeeds() {
        // Given
        OperationState state = PENDING;

        // When
        state = StateTransition.transition(state, IN_PROGRESS);
        state = StateTransition.transition(state, COMPLETED);

        // Then
        assertEquals(COMPLETED, state);
        assertTrue(state.isTerminal());
    }

    @Test
    void transition_NormalFlowToFailed_Succeeds() {
        // Given
        OperationState state = PENDING;

        // When
        state = StateTransition.transition(state, IN_PROGRESS);
        state = StateTransition.transition(state, FAILED);

        // Then
        assertEquals(FAILED, state);
        assertTrue(state.isTerminal());
    }

    // ========== 불법 전이 테스트: COMPLETED에서 ==========

    @Test
    void validate_CompletedToInProgress_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(COMPLETED, IN_PROGRESS)
        );
        assertTrue(exception.getMessage().contains("terminal state"));
        assertTrue(exception.getMessage().contains("COMPLETED"));
        assertTrue(exception.getMessage().contains("IN_PROGRESS"));
    }

    @Test
    void validate_CompletedToPending_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(COMPLETED, PENDING)
        );
        assertTrue(exception.getMessage().contains("terminal state"));
    }

    @Test
    void validate_CompletedToFailed_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(COMPLETED, FAILED)
        );
        assertTrue(exception.getMessage().contains("terminal state"));
    }

    @Test
    void validate_CompletedToCompleted_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(COMPLETED, COMPLETED)
        );
        assertTrue(exception.getMessage().contains("terminal state"));
    }

    // ========== 불법 전이 테스트: FAILED에서 ==========

    @Test
    void validate_FailedToInProgress_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(FAILED, IN_PROGRESS)
        );
        assertTrue(exception.getMessage().contains("terminal state"));
        assertTrue(exception.getMessage().contains("FAILED"));
        assertTrue(exception.getMessage().contains("IN_PROGRESS"));
    }

    @Test
    void validate_FailedToPending_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(FAILED, PENDING)
        );
        assertTrue(exception.getMessage().contains("terminal state"));
    }

    @Test
    void validate_FailedToCompleted_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(FAILED, COMPLETED)
        );
        assertTrue(exception.getMessage().contains("terminal state"));
    }

    @Test
    void validate_FailedToFailed_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(FAILED, FAILED)
        );
        assertTrue(exception.getMessage().contains("terminal state"));
    }

    // ========== 불법 전이 테스트: PENDING에서 ==========

    @Test
    void validate_PendingToCompleted_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(PENDING, COMPLETED)
        );
        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    @Test
    void validate_PendingToFailed_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(PENDING, FAILED)
        );
        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    @Test
    void validate_PendingToPending_ThrowsException() {
        // When & Then
        assertThrows(IllegalStateException.class,
            () -> StateTransition.validate(PENDING, PENDING));
    }

    // ========== 불법 전이 테스트: IN_PROGRESS에서 ==========

    @Test
    void validate_InProgressToPending_ThrowsException() {
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> StateTransition.validate(IN_PROGRESS, PENDING)
        );
        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    @Test
    void validate_InProgressToInProgress_ThrowsException() {
        // When & Then
        assertThrows(IllegalStateException.class,
            () -> StateTransition.validate(IN_PROGRESS, IN_PROGRESS));
    }

    // ========== Null 검증 테스트 ==========

    @Test
    void validate_NullFromState_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> StateTransition.validate(null, IN_PROGRESS)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void validate_NullToState_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> StateTransition.validate(PENDING, null)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void validate_BothNullStates_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> StateTransition.validate(null, null)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    // ========== transition() 메서드 테스트 ==========

    @Test
    void transition_ValidTransition_ReturnsNewState() {
        // Given
        OperationState current = PENDING;

        // When
        OperationState next = StateTransition.transition(current, IN_PROGRESS);

        // Then
        assertEquals(IN_PROGRESS, next);
    }

    @Test
    void transition_InvalidTransition_ThrowsException() {
        // Given
        OperationState current = COMPLETED;

        // When & Then
        assertThrows(IllegalStateException.class,
            () -> StateTransition.transition(current, IN_PROGRESS));
    }

    @Test
    void transition_NullState_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> StateTransition.transition(null, IN_PROGRESS));
    }

    // ========== 시나리오 테스트 ==========

    @Test
    void scenario_CompleteOperationLifecycle_Succeeds() {
        // Given: 새 Operation
        OperationState state = PENDING;

        // When: 실행 시작
        state = StateTransition.transition(state, IN_PROGRESS);
        assertEquals(IN_PROGRESS, state);

        // When: 성공 완료
        state = StateTransition.transition(state, COMPLETED);

        // Then
        assertEquals(COMPLETED, state);
        assertTrue(state.isTerminal());

        // When: COMPLETED에서 다시 전이 시도
        OperationState finalState = state;
        assertThrows(IllegalStateException.class,
            () -> StateTransition.transition(finalState, IN_PROGRESS));
    }

    @Test
    void scenario_FailedOperationLifecycle_Succeeds() {
        // Given
        OperationState state = PENDING;

        // When
        state = StateTransition.transition(state, IN_PROGRESS);
        state = StateTransition.transition(state, FAILED);

        // Then
        assertEquals(FAILED, state);
        assertTrue(state.isTerminal());

        // When: FAILED에서 재시도 불가
        OperationState finalState = state;
        assertThrows(IllegalStateException.class,
            () -> StateTransition.transition(finalState, IN_PROGRESS));
    }
}
