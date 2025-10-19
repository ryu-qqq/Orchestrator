package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Fail;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.spi.WriteAheadState;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract Test for Scenario 4: External Success → Internal Failure Recovery.
 *
 * <p>This test validates that Finalizer can detect and recover operations
 * where external API succeeded (writeAhead) but internal finalization failed.</p>
 *
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>writeAhead succeeds but finalize fails → WAL remains PENDING</li>
 *   <li>Finalizer scans PENDING WAL entries and recovers them</li>
 *   <li>After recovery, operation state is COMPLETED and WAL is COMPLETED</li>
 *   <li>Finalizer is idempotent (can be run multiple times safely)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class RecoveryContractTest extends AbstractContractTest {

    @Test
    void testRecovery_WriteAheadSucceeds_FinalizeNeverCalled_WALRemainsPending() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-R01", "IDEM-R01");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        // Setup: operation in IN_PROGRESS
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // When: writeAhead succeeds but finalize is never called (simulating crash)
        store.writeAhead(opId, outcome);

        // Then: WAL remains PENDING, operation still IN_PROGRESS
        assertWALState(opId, WriteAheadState.PENDING);
        assertOperationState(opId, OperationState.IN_PROGRESS);
    }

    @Test
    void testRecovery_FinalizerScans_PendingWAL_AndRecovers() {
        // Given: writeAhead succeeded but finalize failed
        Envelope envelope = createTestEnvelope("BIZ-R02", "IDEM-R02");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, outcome);

        // Verify initial state
        assertWALState(opId, WriteAheadState.PENDING);
        assertOperationState(opId, OperationState.IN_PROGRESS);

        // When: Finalizer scans for PENDING WAL entries
        List<OpId> pendingOps = store.scanWA(WriteAheadState.PENDING, 100);

        // Then: our operation is in the pending list
        assertTrue(pendingOps.contains(opId),
                "Pending WAL entry should be detected by scan");

        // When: Finalizer recovers the operation
        for (OpId pendingOpId : pendingOps) {
            if (pendingOpId.equals(opId)) {
                // Finalizer logic: complete the operation
                Outcome recoveredOutcome = store.getWriteAheadOutcome(pendingOpId);
                assertNotNull(recoveredOutcome, "WAL outcome should exist");

                // Determine terminal state based on outcome
                OperationState finalState = recoveredOutcome instanceof Ok
                        ? OperationState.COMPLETED
                        : OperationState.FAILED;

                store.finalize(pendingOpId, finalState);
            }
        }

        // Then: operation is now COMPLETED and WAL is COMPLETED
        assertOperationState(opId, OperationState.COMPLETED);
        assertWALState(opId, WriteAheadState.COMPLETED);
    }

    @Test
    void testRecovery_FinalizerIsIdempotent_SafeToRunMultipleTimes() {
        // Given: writeAhead succeeded but finalize failed
        Envelope envelope = createTestEnvelope("BIZ-R03", "IDEM-R03");
        OpId opId = envelope.opId();
        Outcome outcome = Ok.of(opId);

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, outcome);

        // When: Finalizer runs first time
        List<OpId> pendingOps = store.scanWA(WriteAheadState.PENDING, 100);
        for (OpId pendingOpId : pendingOps) {
            if (pendingOpId.equals(opId)) {
                store.finalize(pendingOpId, OperationState.COMPLETED);
            }
        }

        assertOperationState(opId, OperationState.COMPLETED);
        assertWALState(opId, WriteAheadState.COMPLETED);

        // When: Finalizer runs second time (idempotency check)
        List<OpId> pendingOpsSecond = store.scanWA(WriteAheadState.PENDING, 100);

        // Then: our operation is no longer in pending list
        assertFalse(pendingOpsSecond.contains(opId),
                "COMPLETED WAL should not appear in PENDING scan");

        // State remains the same (idempotent)
        assertOperationState(opId, OperationState.COMPLETED);
        assertWALState(opId, WriteAheadState.COMPLETED);
    }

    @Test
    void testRecovery_MultipleFailedOperations_AllRecovered() {
        // Given: multiple operations with writeAhead succeeded but finalize failed
        OpId opId1 = OpId.of("op-r04-1");
        OpId opId2 = OpId.of("op-r04-2");
        OpId opId3 = OpId.of("op-r04-3");

        Envelope envelope1 = createTestEnvelope("BIZ-R04-1", "IDEM-R04-1");
        Envelope envelope2 = createTestEnvelope("BIZ-R04-2", "IDEM-R04-2");
        Envelope envelope3 = createTestEnvelope("BIZ-R04-3", "IDEM-R04-3");

        // Setup all operations with writeAhead but no finalize
        store.setState(opId1, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId1, envelope1);
        store.writeAhead(opId1, Ok.of(opId1));

        store.setState(opId2, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId2, envelope2);
        store.writeAhead(opId2, Ok.of(opId2));

        store.setState(opId3, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId3, envelope3);
        store.writeAhead(opId3, Fail.of("ERR-TEST", "test failure"));

        // When: Finalizer scans and recovers all pending operations
        List<OpId> pendingOps = store.scanWA(WriteAheadState.PENDING, 100);

        assertEquals(3, pendingOps.size(),
                "Should find 3 pending WAL entries");

        for (OpId pendingOpId : pendingOps) {
            Outcome recoveredOutcome = store.getWriteAheadOutcome(pendingOpId);
            OperationState finalState = recoveredOutcome instanceof Ok
                    ? OperationState.COMPLETED
                    : OperationState.FAILED;
            store.finalize(pendingOpId, finalState);
        }

        // Then: all operations are finalized correctly
        assertOperationState(opId1, OperationState.COMPLETED);
        assertOperationState(opId2, OperationState.COMPLETED);
        assertOperationState(opId3, OperationState.FAILED);

        assertWALState(opId1, WriteAheadState.COMPLETED);
        assertWALState(opId2, WriteAheadState.COMPLETED);
        assertWALState(opId3, WriteAheadState.COMPLETED);

        // Verify no more pending operations
        List<OpId> remainingPending = store.scanWA(WriteAheadState.PENDING, 100);
        assertTrue(remainingPending.isEmpty(),
                "All pending operations should be recovered");
    }

    @Test
    void testRecovery_ScanWithBatchSize_RespectsLimit() {
        // Given: create 10 pending operations
        for (int i = 0; i < 10; i++) {
            OpId opId = OpId.of("op-r05-" + i);
            Envelope envelope = createTestEnvelope("BIZ-R05-" + i, "IDEM-R05-" + i);

            store.setState(opId, OperationState.IN_PROGRESS);
            store.storeEnvelope(opId, envelope);
            store.writeAhead(opId, Ok.of(opId));
        }

        // When: scan with batchSize of 5
        List<OpId> firstBatch = store.scanWA(WriteAheadState.PENDING, 5);

        // Then: only 5 operations returned
        assertEquals(5, firstBatch.size(),
                "Batch size should be respected");

        // When: recover first batch
        for (OpId opId : firstBatch) {
            store.finalize(opId, OperationState.COMPLETED);
        }

        // Then: remaining 5 operations still pending
        List<OpId> secondBatch = store.scanWA(WriteAheadState.PENDING, 5);
        assertEquals(5, secondBatch.size(),
                "Remaining operations should be available in next batch");
    }

    @Test
    void testRecovery_FinalizerConcurrency_SafeUnderConcurrentAccess() throws InterruptedException {
        // Given: create pending operations
        int operationCount = 10;
        for (int i = 0; i < operationCount; i++) {
            OpId opId = OpId.of("op-r06-" + i);
            Envelope envelope = createTestEnvelope("BIZ-R06-" + i, "IDEM-R06-" + i);

            store.setState(opId, OperationState.IN_PROGRESS);
            store.storeEnvelope(opId, envelope);
            store.writeAhead(opId, Ok.of(opId));
        }

        AtomicInteger recoveredCount = new AtomicInteger(0);
        AtomicBoolean hasError = new AtomicBoolean(false);

        // When: simulate concurrent finalizers using ExecutorService
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        executorService.submit(() -> {
            try {
                List<OpId> pending = store.scanWA(WriteAheadState.PENDING, 100);
                for (OpId opId : pending) {
                    try {
                        store.finalize(opId, OperationState.COMPLETED);
                        recoveredCount.incrementAndGet();
                    } catch (IllegalStateException e) {
                        // Expected: another finalizer already processed this
                        if (!e.getMessage().contains("already finalized")) {
                            hasError.set(true);
                        }
                    }
                }
            } catch (Exception e) {
                hasError.set(true);
            }
        });

        executorService.submit(() -> {
            try {
                List<OpId> pending = store.scanWA(WriteAheadState.PENDING, 100);
                for (OpId opId : pending) {
                    try {
                        store.finalize(opId, OperationState.COMPLETED);
                        recoveredCount.incrementAndGet();
                    } catch (IllegalStateException e) {
                        // Expected: another finalizer already processed this
                        if (!e.getMessage().contains("already finalized")) {
                            hasError.set(true);
                        }
                    }
                }
            } catch (Exception e) {
                hasError.set(true);
            }
        });

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // Then: all operations recovered exactly once (concurrent finalizers compete)
        assertFalse(hasError.get(), "No unexpected errors should occur");
        assertEquals(operationCount, recoveredCount.get(),
                "All operations should be processed exactly once");

        // Verify all operations are finalized
        List<OpId> remainingPending = store.scanWA(WriteAheadState.PENDING, 100);
        assertTrue(remainingPending.isEmpty(),
                "All operations should be finalized");
    }

    @Test
    void testRecovery_FailOutcome_FinalizesAsFailed() {
        // Given: writeAhead with Fail outcome
        Envelope envelope = createTestEnvelope("BIZ-R07", "IDEM-R07");
        OpId opId = envelope.opId();
        Outcome failOutcome = Fail.of("API-ERROR", "External API returned error");

        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, failOutcome);

        // When: Finalizer recovers
        List<OpId> pendingOps = store.scanWA(WriteAheadState.PENDING, 100);
        for (OpId pendingOpId : pendingOps) {
            if (pendingOpId.equals(opId)) {
                Outcome recoveredOutcome = store.getWriteAheadOutcome(pendingOpId);
                OperationState finalState = recoveredOutcome instanceof Fail
                        ? OperationState.FAILED
                        : OperationState.COMPLETED;
                store.finalize(pendingOpId, finalState);
            }
        }

        // Then: operation finalized as FAILED
        assertOperationState(opId, OperationState.FAILED);
        assertWALState(opId, WriteAheadState.COMPLETED);

        Outcome retrievedOutcome = store.getWriteAheadOutcome(opId);
        assertTrue(retrievedOutcome instanceof Fail,
                "Outcome should remain as Fail");
    }
}
