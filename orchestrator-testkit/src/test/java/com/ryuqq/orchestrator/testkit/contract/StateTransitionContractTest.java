package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract Test for Scenario 5: Backward State Transition Prohibition.
 *
 * <p>This test validates that once an operation reaches a terminal state (COMPLETED or FAILED),
 * it cannot be transitioned back to non-terminal states (IN_PROGRESS).</p>
 *
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>COMPLETED → IN_PROGRESS: IllegalStateException</li>
 *   <li>FAILED → IN_PROGRESS: IllegalStateException</li>
 *   <li>COMPLETED → FAILED: IllegalStateException</li>
 *   <li>FAILED → COMPLETED: IllegalStateException</li>
 *   <li>State is preserved after exception (no partial update)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class StateTransitionContractTest extends AbstractContractTest {

    @Test
    void testStateTransition_CompletedToInProgress_ThrowsException() {
        // Given: operation in COMPLETED state
        Envelope envelope = createTestEnvelope("BIZ-ST01", "IDEM-ST01");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, outcome);
        store.finalize(opId, OperationState.COMPLETED);

        assertOperationState(opId, OperationState.COMPLETED);

        // When/Then: attempting to transition back to IN_PROGRESS should fail
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.setState(opId, OperationState.IN_PROGRESS),
                "Should not allow backward transition from COMPLETED to IN_PROGRESS");

        // State remains COMPLETED
        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testStateTransition_FailedToInProgress_ThrowsException() {
        // Given: operation in FAILED state
        Envelope envelope = createTestEnvelope("BIZ-ST02", "IDEM-ST02");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, outcome);
        store.finalize(opId, OperationState.FAILED);

        assertOperationState(opId, OperationState.FAILED);

        // When/Then: attempting to transition back to IN_PROGRESS should fail
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.setState(opId, OperationState.IN_PROGRESS),
                "Should not allow backward transition from FAILED to IN_PROGRESS");

        // State remains FAILED
        assertOperationState(opId, OperationState.FAILED);
    }

    @Test
    void testStateTransition_CompletedToFailed_ThrowsException() {
        // Given: operation in COMPLETED state
        Envelope envelope = createTestEnvelope("BIZ-ST03", "IDEM-ST03");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, outcome);
        store.finalize(opId, OperationState.COMPLETED);

        assertOperationState(opId, OperationState.COMPLETED);

        // When/Then: attempting to finalize again with FAILED should fail
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.finalize(opId, OperationState.FAILED),
                "Should not allow re-finalization from COMPLETED to FAILED");

        assertTrue(exception.getMessage().contains("already finalized"),
                "Exception message should indicate operation was already finalized");

        // State remains COMPLETED
        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testStateTransition_FailedToCompleted_ThrowsException() {
        // Given: operation in FAILED state
        Envelope envelope = createTestEnvelope("BIZ-ST04", "IDEM-ST04");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, outcome);
        store.finalize(opId, OperationState.FAILED);

        assertOperationState(opId, OperationState.FAILED);

        // When/Then: attempting to finalize again with COMPLETED should fail
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.finalize(opId, OperationState.COMPLETED),
                "Should not allow re-finalization from FAILED to COMPLETED");

        assertTrue(exception.getMessage().contains("already finalized"),
                "Exception message should indicate operation was already finalized");

        // State remains FAILED
        assertOperationState(opId, OperationState.FAILED);
    }

    @Test
    void testStateTransition_StatePreservedAfterException() {
        // Given: operation in COMPLETED state
        Envelope envelope = createTestEnvelope("BIZ-ST05", "IDEM-ST05");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, outcome);
        store.finalize(opId, OperationState.COMPLETED);

        OperationState stateBefore = store.getState(opId);
        assertEquals(OperationState.COMPLETED, stateBefore);

        // When: attempt invalid transition
        assertThrows(IllegalStateException.class,
                () -> store.setState(opId, OperationState.IN_PROGRESS),
                "Should have thrown IllegalStateException");

        // Then: state is unchanged (no partial update)
        OperationState stateAfter = store.getState(opId);
        assertEquals(stateBefore, stateAfter,
                "State should be preserved after failed transition attempt");
        assertEquals(OperationState.COMPLETED, stateAfter);
    }

    @Test
    void testStateTransition_ValidForwardTransitions_Succeed() {
        // Given: new operation
        Envelope envelope = createTestEnvelope("BIZ-ST06", "IDEM-ST06");
        OpId opId = envelope.opId();

        // When/Then: IN_PROGRESS → COMPLETED (valid)
        store.setState(opId, OperationState.IN_PROGRESS);
        assertOperationState(opId, OperationState.IN_PROGRESS);

        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, Ok.of(opId));

        assertDoesNotThrow(() -> store.finalize(opId, OperationState.COMPLETED),
                "Valid forward transition should succeed");
        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testStateTransition_ValidFailedTransitions_Succeed() {
        // Given: new operation
        Envelope envelope = createTestEnvelope("BIZ-ST07", "IDEM-ST07");
        OpId opId = envelope.opId();

        // When/Then: IN_PROGRESS → FAILED (valid)
        store.setState(opId, OperationState.IN_PROGRESS);
        assertOperationState(opId, OperationState.IN_PROGRESS);

        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, Ok.of(opId));

        assertDoesNotThrow(() -> store.finalize(opId, OperationState.FAILED),
                "Valid forward transition to FAILED should succeed");
        assertOperationState(opId, OperationState.FAILED);
    }

    @Test
    void testStateTransition_ConcurrentBackwardAttempts_AllBlocked() throws InterruptedException {
        // Given: operation in COMPLETED state
        Envelope envelope = createTestEnvelope("BIZ-ST08", "IDEM-ST08");
        OpId opId = envelope.opId();

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, Ok.of(opId));
        store.finalize(opId, OperationState.COMPLETED);

        // When: multiple threads try backward transition concurrently
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        boolean[] exceptions = new boolean[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    store.setState(opId, OperationState.IN_PROGRESS);
                    exceptions[index] = false; // Should not reach here
                } catch (IllegalStateException e) {
                    exceptions[index] = true; // Expected
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: all threads should have received exceptions
        for (int i = 0; i < threadCount; i++) {
            assertTrue(exceptions[i],
                    "Thread " + i + " should have received IllegalStateException");
        }

        // State remains COMPLETED
        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testStateTransition_InProgressToInProgress_Allowed() {
        // Given: operation in IN_PROGRESS
        Envelope envelope = createTestEnvelope("BIZ-ST09", "IDEM-ST09");
        OpId opId = envelope.opId();

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // When/Then: re-setting to IN_PROGRESS is allowed (idempotent)
        assertDoesNotThrow(() -> store.setState(opId, OperationState.IN_PROGRESS),
                "Re-setting to same state should be allowed");

        assertOperationState(opId, OperationState.IN_PROGRESS);
    }
}
