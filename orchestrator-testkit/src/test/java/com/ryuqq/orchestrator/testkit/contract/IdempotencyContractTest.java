package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.model.IdempotencyKey;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract Test for Scenario 2: Idempotency (Duplicate Consumption).
 *
 * <p>This test validates that processing the same message multiple times
 * results in only one external API call and consistent final state.</p>
 *
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>Same IdempotencyKey twice → same OpId returned</li>
 *   <li>External API called only once despite duplicate messages</li>
 *   <li>Final state identical after duplicate processing</li>
 *   <li>Concurrent duplicate requests → only one OpId created</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class IdempotencyContractTest extends AbstractContractTest {

    @Test
    void testIdempotency_SameKeyTwice_SameOpIdReturned() {
        // Given
        IdempotencyKey key = createIdempotencyKey("IDEM-001");

        // When: getOrCreate called twice with same key
        OpId opId1 = idempotencyManager.getOrCreate(key);
        OpId opId2 = idempotencyManager.getOrCreate(key);

        // Then: same OpId returned
        assertEquals(opId1, opId2,
                "Same IdempotencyKey should always return the same OpId");
    }

    @Test
    void testIdempotency_SameMessageTwice_ExternalApiCalledOnce() {
        // Given
        IdempotencyKey key = createIdempotencyKey("IDEM-002");
        AtomicInteger apiCallCount = new AtomicInteger(0);

        // Simulate external API call
        Runnable externalApiCall = apiCallCount::incrementAndGet;

        // When: process same message twice
        OpId opId1 = idempotencyManager.getOrCreate(key);

        // First processing - check completion status before processing
        OperationState currentState = null;
        try {
            currentState = store.getState(opId1);
        } catch (IllegalStateException e) {
            // Operation doesn't exist yet, will be created
        }

        if (currentState != OperationState.COMPLETED) {
            store.setState(opId1, OperationState.IN_PROGRESS);
            externalApiCall.run(); // External API call
            store.writeAhead(opId1, Ok.of(opId1));
            store.finalize(opId1, OperationState.COMPLETED);
        }

        // Second processing with same key
        OpId opId2 = idempotencyManager.getOrCreate(key);
        try {
            OperationState state2 = store.getState(opId2);
            // Skip processing if already completed
            if (state2 != OperationState.COMPLETED) {
                externalApiCall.run();
                store.writeAhead(opId2, Ok.of(opId2));
                store.finalize(opId2, OperationState.COMPLETED);
            }
        } catch (IllegalStateException e) {
            // Expected: operation already exists and is completed
        }

        // Then: external API called only once
        assertEquals(1, apiCallCount.get(),
                "External API should be called only once despite duplicate message");
        assertEquals(opId1, opId2, "Both processes should use same OpId");
    }

    @Test
    void testIdempotency_DuplicateProcessing_FinalStateIdentical() {
        // Given
        IdempotencyKey key = createIdempotencyKey("IDEM-003");

        // When: first processing
        OpId opId1 = idempotencyManager.getOrCreate(key);
        store.setState(opId1, OperationState.IN_PROGRESS);
        store.writeAhead(opId1, Ok.of(opId1));
        store.finalize(opId1, OperationState.COMPLETED);

        OperationState stateAfterFirst = store.getState(opId1);

        // Second processing attempt with same key
        OpId opId2 = idempotencyManager.getOrCreate(key);
        assertEquals(opId1, opId2, "Should return same OpId");

        // Check if already completed
        OperationState currentState = store.getState(opId2);
        if (currentState == OperationState.COMPLETED) {
            // Skip processing - already completed
        }

        OperationState stateAfterSecond = store.getState(opId2);

        // Then: final state is identical
        assertEquals(stateAfterFirst, stateAfterSecond,
                "Final state should be identical after duplicate processing");
        assertEquals(OperationState.COMPLETED, stateAfterSecond,
                "Operation should remain COMPLETED");
    }

    @Test
    void testIdempotency_ConcurrentDuplicates_OnlyOneOpIdCreated() throws InterruptedException {
        // Given
        IdempotencyKey key = createIdempotencyKey("IDEM-004");
        OpId[] opIds = new OpId[2];

        // When: concurrent getOrCreate with same key
        Thread thread1 = new Thread(() -> opIds[0] = idempotencyManager.getOrCreate(key));
        Thread thread2 = new Thread(() -> opIds[1] = idempotencyManager.getOrCreate(key));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then: both threads get the same OpId
        assertNotNull(opIds[0], "Thread 1 should have created/retrieved OpId");
        assertNotNull(opIds[1], "Thread 2 should have created/retrieved OpId");
        assertEquals(opIds[0], opIds[1],
                "Concurrent requests with same key should get same OpId");

        // Verify only one mapping exists
        assertEquals(1, idempotencyManager.size(),
                "Only one mapping should exist for the key");
    }

    @Test
    void testIdempotency_DifferentKeys_DifferentOpIds() {
        // Given
        IdempotencyKey key1 = createIdempotencyKey("IDEM-005");
        IdempotencyKey key2 = createIdempotencyKey("IDEM-006");

        // When
        OpId opId1 = idempotencyManager.getOrCreate(key1);
        OpId opId2 = idempotencyManager.getOrCreate(key2);

        // Then: different keys produce different OpIds
        assertNotEquals(opId1, opId2,
                "Different IdempotencyKeys should produce different OpIds");
    }

    @Test
    void testIdempotency_FindBeforeCreate_ReturnsNull() {
        // Given
        IdempotencyKey key = createIdempotencyKey("IDEM-007");

        // When: find before create
        OpId foundOpId = idempotencyManager.find(key);

        // Then: should return null
        assertNull(foundOpId,
                "find() should return null for non-existent key");
    }

    @Test
    void testIdempotency_FindAfterCreate_ReturnsOpId() {
        // Given
        IdempotencyKey key = createIdempotencyKey("IDEM-008");

        // When: create then find
        OpId createdOpId = idempotencyManager.getOrCreate(key);
        OpId foundOpId = idempotencyManager.find(key);

        // Then: find returns the created OpId
        assertNotNull(foundOpId, "find() should return the created OpId");
        assertEquals(createdOpId, foundOpId,
                "find() should return the same OpId as getOrCreate()");
    }

    @Test
    void testIdempotency_NullKey_ThrowsException() {
        // When/Then
        assertThrows(IllegalArgumentException.class,
                () -> idempotencyManager.getOrCreate(null),
                "getOrCreate() should throw IllegalArgumentException for null key");

        assertThrows(IllegalArgumentException.class,
                () -> idempotencyManager.find(null),
                "find() should throw IllegalArgumentException for null key");
    }
}
