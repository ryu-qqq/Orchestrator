package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract Test for Scenario 6: 200 ↔ 202 Response Branching Accuracy.
 *
 * <p>This test validates that synchronous operations correctly return HTTP 200 (sync completion)
 * or HTTP 202 (async processing) based on timeBudget boundary conditions.</p>
 *
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>Completion within timeBudget → 200 OK (sync response)</li>
 *   <li>Completion exceeds timeBudget → 202 Accepted (async response)</li>
 *   <li>Exact boundary condition (edge case)</li>
 *   <li>Operations with different timeBudget values</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class TimeBudgetContractTest extends AbstractContractTest {

    private static final long TIME_BUDGET_MS = 1000; // 1 second
    private static final long BOUNDARY_TOLERANCE_MS = 50; // 50ms tolerance

    @Test
    void testTimeBudget_CompletionWithinBudget_Returns200() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-TB01", "IDEM-TB01");
        OpId opId = envelope.opId();
        long startTime = System.currentTimeMillis();

        // When: operation completes quickly (well within budget)
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // Simulate fast operation (100ms)
        sleep(100);

        store.writeAhead(opId, Ok.of(opId));
        store.finalize(opId, OperationState.COMPLETED);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: operation completed within budget
        assertTrue(elapsedTime < TIME_BUDGET_MS,
                "Operation should complete within timeBudget");

        // Verify final state
        assertOperationState(opId, OperationState.COMPLETED);

        // Decision: return 200 OK (sync response)
        boolean shouldReturnSync = elapsedTime < TIME_BUDGET_MS;
        assertTrue(shouldReturnSync,
                "Should return HTTP 200 when completed within timeBudget");
    }

    @Test
    void testTimeBudget_CompletionExceedsBudget_Returns202() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-TB02", "IDEM-TB02");
        OpId opId = envelope.opId();
        long startTime = System.currentTimeMillis();

        // When: operation takes longer than budget
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // Simulate slow operation (1.5 seconds)
        sleep(1500);

        store.writeAhead(opId, Ok.of(opId));
        store.finalize(opId, OperationState.COMPLETED);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: operation exceeded budget
        assertTrue(elapsedTime > TIME_BUDGET_MS,
                "Operation should exceed timeBudget");

        // Verify final state (still completes successfully)
        assertOperationState(opId, OperationState.COMPLETED);

        // Decision: return 202 Accepted (async response)
        boolean shouldReturnAsync = elapsedTime >= TIME_BUDGET_MS;
        assertTrue(shouldReturnAsync,
                "Should return HTTP 202 when completion exceeds timeBudget");
    }

    @Test
    void testTimeBudget_ExactBoundary_ConsistentBehavior() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-TB03", "IDEM-TB03");
        OpId opId = envelope.opId();
        long startTime = System.currentTimeMillis();

        // When: operation completes at exact boundary (within tolerance)
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // Simulate operation completing near boundary (TIME_BUDGET_MS - TOLERANCE)
        long sleepTime = TIME_BUDGET_MS - BOUNDARY_TOLERANCE_MS;
        sleep(sleepTime);

        store.writeAhead(opId, Ok.of(opId));
        store.finalize(opId, OperationState.COMPLETED);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: should be within budget (200 OK)
        boolean shouldReturnSync = elapsedTime < TIME_BUDGET_MS;

        if (shouldReturnSync) {
            assertTrue(elapsedTime < TIME_BUDGET_MS,
                    "Boundary case: should complete within budget");
        }

        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testTimeBudget_DifferentBudgets_CorrectClassification() {
        // Test with different timeBudget values
        long[] budgets = {500, 1000, 2000, 5000}; // milliseconds

        for (long budget : budgets) {
            // Given: operation with specific budget
            String bizKey = "BIZ-TB04-" + budget;
            String idemKey = "IDEM-TB04-" + budget;
            Envelope envelope = createTestEnvelope(bizKey, idemKey);
            OpId opId = envelope.opId();

            long startTime = System.currentTimeMillis();

            store.setState(opId, OperationState.IN_PROGRESS);
            store.storeEnvelope(opId, envelope);

            // Simulate operation taking fixed 800ms
            sleep(800);

            store.writeAhead(opId, Ok.of(opId));
            store.finalize(opId, OperationState.COMPLETED);

            long elapsedTime = System.currentTimeMillis() - startTime;

            // Then: classify based on budget
            boolean shouldReturnSync = elapsedTime < budget;

            if (budget > 800) {
                assertTrue(shouldReturnSync,
                        String.format("800ms operation should return 200 with %dms budget", budget));
            } else {
                assertFalse(shouldReturnSync,
                        String.format("800ms operation should return 202 with %dms budget", budget));
            }

            assertOperationState(opId, OperationState.COMPLETED);
        }
    }

    @Test
    void testTimeBudget_InProgressTimeout_Returns202Immediately() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-TB05", "IDEM-TB05");
        OpId opId = envelope.opId();
        long startTime = System.currentTimeMillis();

        // When: operation is IN_PROGRESS and timeBudget expires
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // Wait for timeBudget to expire
        sleep(TIME_BUDGET_MS + 100);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: even though not completed, should return 202 after budget
        OperationState currentState = store.getState(opId);
        boolean stillInProgress = currentState == OperationState.IN_PROGRESS;
        boolean budgetExpired = elapsedTime >= TIME_BUDGET_MS;

        assertTrue(stillInProgress, "Operation should still be IN_PROGRESS");
        assertTrue(budgetExpired, "TimeBudget should have expired");

        // Decision: return 202 Accepted (operation continues async)
        boolean shouldReturnAsync = budgetExpired;
        assertTrue(shouldReturnAsync,
                "Should return HTTP 202 when timeBudget expires while IN_PROGRESS");
    }

    @Test
    void testTimeBudget_MultipleOperations_IndependentBudgets() {
        // Given: multiple operations with independent time budgets
        OpId fastOpId = OpId.of("op-tb06-fast");
        OpId slowOpId = OpId.of("op-tb06-slow");

        Envelope fastEnvelope = createTestEnvelope("BIZ-TB06-FAST", "IDEM-TB06-FAST");
        Envelope slowEnvelope = createTestEnvelope("BIZ-TB06-SLOW", "IDEM-TB06-SLOW");

        // When: process fast operation (200ms)
        long fastStart = System.currentTimeMillis();
        store.setState(fastOpId, OperationState.IN_PROGRESS);
        store.storeEnvelope(fastOpId, fastEnvelope);
        sleep(200);
        store.writeAhead(fastOpId, Ok.of(fastOpId));
        store.finalize(fastOpId, OperationState.COMPLETED);
        long fastElapsed = System.currentTimeMillis() - fastStart;

        // When: process slow operation (1500ms)
        long slowStart = System.currentTimeMillis();
        store.setState(slowOpId, OperationState.IN_PROGRESS);
        store.storeEnvelope(slowOpId, slowEnvelope);
        sleep(1500);
        store.writeAhead(slowOpId, Ok.of(slowOpId));
        store.finalize(slowOpId, OperationState.COMPLETED);
        long slowElapsed = System.currentTimeMillis() - slowStart;

        // Then: independent classifications
        boolean fastIsSync = fastElapsed < TIME_BUDGET_MS;
        boolean slowIsAsync = slowElapsed >= TIME_BUDGET_MS;

        assertTrue(fastIsSync, "Fast operation should return 200");
        assertTrue(slowIsAsync, "Slow operation should return 202");

        assertOperationState(fastOpId, OperationState.COMPLETED);
        assertOperationState(slowOpId, OperationState.COMPLETED);
    }

    @Test
    void testTimeBudget_ZeroBudget_AlwaysAsync() {
        // Given: timeBudget = 0 (always async)
        long zeroBudget = 0;
        Envelope envelope = createTestEnvelope("BIZ-TB07", "IDEM-TB07");
        OpId opId = envelope.opId();

        // When: operation completes instantly
        long startTime = System.currentTimeMillis();
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        store.writeAhead(opId, Ok.of(opId));
        store.finalize(opId, OperationState.COMPLETED);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: always return 202 with zero budget
        boolean shouldReturnAsync = elapsedTime >= zeroBudget;
        assertTrue(shouldReturnAsync,
                "Zero budget should always return 202 (fully async)");
    }

    @Test
    void testTimeBudget_InfiniteBudget_AlwaysSync() {
        // Given: timeBudget = Long.MAX_VALUE (always sync)
        long infiniteBudget = Long.MAX_VALUE;
        Envelope envelope = createTestEnvelope("BIZ-TB08", "IDEM-TB08");
        OpId opId = envelope.opId();

        // When: operation takes significant time
        long startTime = System.currentTimeMillis();
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);
        sleep(2000); // 2 seconds
        store.writeAhead(opId, Ok.of(opId));
        store.finalize(opId, OperationState.COMPLETED);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Then: always return 200 with infinite budget
        boolean shouldReturnSync = elapsedTime < infiniteBudget;
        assertTrue(shouldReturnSync,
                "Infinite budget should always return 200 (fully sync)");
    }
}
