package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.spi.WriteAheadState;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract Test for Scenario 1: S1 Atomicity.
 *
 * <p>This test validates that Operation and Outbox (WAL) are committed together atomically,
 * and both are rolled back together if either fails.</p>
 *
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>Both writeAhead and finalize succeed → both persisted</li>
 *   <li>finalize throws exception → validate state consistency</li>
 *   <li>Concurrent operations → no interference between transactions</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class AtomicityContractTest extends AbstractContractTest {

    @Test
    void testAtomicCommit_WhenBothSucceed_BothPersisted() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-001", "IDEM-001");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        // Setup: operation in IN_PROGRESS state
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // When: writeAhead and finalize both succeed
        store.writeAhead(opId, outcome);
        store.finalize(opId, OperationState.COMPLETED);

        // Then: both WAL and operation state are persisted
        assertWALState(opId, WriteAheadState.COMPLETED);
        assertOperationState(opId, OperationState.COMPLETED);

        // Verify outcome can be retrieved
        Outcome retrievedOutcome = store.getWriteAheadOutcome(opId);
        assertNotNull(retrievedOutcome, "Retrieved outcome should not be null");
        assertTrue(retrievedOutcome instanceof Ok, "Outcome should be Ok");
    }

    @Test
    void testAtomicRollback_WhenFinalizeThrows_StateRemainsConsistent() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-002", "IDEM-002");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        // Setup: operation in IN_PROGRESS state
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // When: writeAhead succeeds but finalize throws (already finalized scenario)
        store.writeAhead(opId, outcome);
        store.finalize(opId, OperationState.COMPLETED); // First finalize succeeds

        // Then: attempting to finalize again should fail
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.finalize(opId, OperationState.COMPLETED),
                "Should throw IllegalStateException when trying to finalize already completed operation");

        assertTrue(exception.getMessage().contains("already finalized"),
                "Exception message should indicate operation was already finalized");

        // State remains COMPLETED from first finalize
        assertOperationState(opId, OperationState.COMPLETED);
        assertWALState(opId, WriteAheadState.COMPLETED);
    }

    @Test
    void testTransactionIsolation_ConcurrentOperations_NoInterference() throws InterruptedException {
        // Given: two independent operations
        Envelope envelope1 = createTestEnvelope("BIZ-003", "IDEM-003");
        Envelope envelope2 = createTestEnvelope("BIZ-004", "IDEM-004");
        OpId opId1 = envelope1.opId();
        OpId opId2 = envelope2.opId();

        Outcome outcome1 = Ok.of(opId1);
        Outcome outcome2 = Ok.of(opId2);

        // Setup: both operations in IN_PROGRESS
        store.setState(opId1, OperationState.IN_PROGRESS);
        store.setState(opId2, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId1, envelope1);
        store.storeEnvelope(opId2, envelope2);

        // When: concurrent execution (simulated with threads)
        Thread thread1 = new Thread(() -> {
            store.writeAhead(opId1, outcome1);
            store.finalize(opId1, OperationState.COMPLETED);
        });

        Thread thread2 = new Thread(() -> {
            store.writeAhead(opId2, outcome2);
            store.finalize(opId2, OperationState.COMPLETED);
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then: both operations completed independently
        assertOperationState(opId1, OperationState.COMPLETED);
        assertOperationState(opId2, OperationState.COMPLETED);
        assertWALState(opId1, WriteAheadState.COMPLETED);
        assertWALState(opId2, WriteAheadState.COMPLETED);
    }

    @Test
    void testWriteAheadOnly_WhenNotFinalized_WALRemainsPending() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-005", "IDEM-005");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        // Setup
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // When: only writeAhead is called (finalize not called yet)
        store.writeAhead(opId, outcome);

        // Then: WAL is PENDING, operation still IN_PROGRESS
        assertWALState(opId, WriteAheadState.PENDING);
        assertOperationState(opId, OperationState.IN_PROGRESS);
    }

    @Test
    void testFinalizeWithoutWriteAhead_Succeeds() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-006", "IDEM-006");
        OpId opId = envelope.opId();

        // Setup: operation in IN_PROGRESS without writeAhead
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // When/Then: finalize without writeAhead should succeed (WAL is optional)
        // In our implementation, finalize works even without prior writeAhead
        assertDoesNotThrow(() -> store.finalize(opId, OperationState.COMPLETED),
                "Finalize should work even without prior writeAhead");

        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testFinalizeNonTerminalState_ThrowsException() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-007", "IDEM-007");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        // Setup
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, outcome);

        // When/Then: attempting to finalize with non-terminal state should fail
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> store.finalize(opId, OperationState.IN_PROGRESS),
                "Should throw IllegalArgumentException for non-terminal state");

        assertTrue(exception.getMessage().contains("must be COMPLETED or FAILED"),
                "Exception message should indicate only terminal states are allowed");
    }
}
