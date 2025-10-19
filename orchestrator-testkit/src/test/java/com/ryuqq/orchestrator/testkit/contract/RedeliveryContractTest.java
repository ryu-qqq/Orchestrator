package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract Test for Scenario 3: Long-Running/Redelivery.
 *
 * <p>This test validates that when a message is redelivered due to visibility timeout,
 * only the first processing succeeds and the second is ignored through concurrency control.</p>
 *
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>Visibility timeout → message redelivered → only first processing succeeds</li>
 *   <li>Concurrent processing of same message → only one succeeds</li>
 *   <li>finalize idempotency → duplicate finalize attempts fail gracefully</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class RedeliveryContractTest extends AbstractContractTest {

    @Test
    void testVisibilityTimeout_MessageRedelivered_FirstProcessingWins() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-001", "IDEM-001");
        OpId opId = envelope.opId();

        // Setup: publish message to queue
        bus.publish(envelope, 0);

        // When: first dequeue and start processing
        List<Envelope> batch1 = bus.dequeue(1);
        assertEquals(1, batch1.size(), "Should dequeue one message");
        assertEquals(envelope, batch1.get(0), "Should be the original envelope");

        // First processing starts
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // Simulate visibility timeout expiration before ack
        bus.expireVisibilityTimeout(envelope);

        // Second dequeue (message redelivered)
        List<Envelope> batch2 = bus.dequeue(1);
        assertEquals(1, batch2.size(), "Message should be redelivered after visibility timeout");

        // First processing completes
        store.writeAhead(opId, Ok.of(opId));
        store.finalize(opId, OperationState.COMPLETED);
        bus.ack(envelope);

        // Second processing attempts to finalize
        assertThrows(IllegalStateException.class,
                () -> store.finalize(opId, OperationState.COMPLETED),
                "Second processing should fail because operation already finalized");

        // Then: operation completed only once
        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testConcurrencyControl_SimultaneousProcessing_OnlyOneSucceeds() throws InterruptedException {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-002", "IDEM-002");
        OpId opId = envelope.opId();

        // Setup initial state
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // When: two threads try to finalize simultaneously
        Runnable finalizeTask = () -> {
            try {
                startLatch.await(); // Wait for signal to start simultaneously
                store.writeAhead(opId, Ok.of(opId));
                store.finalize(opId, OperationState.COMPLETED);
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                // Expected: only one should succeed
                failureCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        };

        Thread thread1 = new Thread(finalizeTask);
        Thread thread2 = new Thread(finalizeTask);

        thread1.start();
        thread2.start();

        // Start both threads simultaneously
        startLatch.countDown();
        doneLatch.await();

        // Then: only one finalize succeeded
        assertEquals(1, successCount.get(),
                "Only one thread should successfully finalize");
        assertEquals(1, failureCount.get(),
                "One thread should fail due to already finalized state");

        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testVisibilityTimeout_AckPreventsRedelivery() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-003", "IDEM-003");

        // Setup
        bus.publish(envelope, 0);

        // When: dequeue and ack immediately
        List<Envelope> batch = bus.dequeue(1);
        assertEquals(1, batch.size(), "Should dequeue one message");

        bus.ack(envelope);

        // Attempt to expire visibility timeout after ack
        boolean expired = bus.expireVisibilityTimeout(envelope);

        // Then: cannot expire after ack
        assertFalse(expired, "Should not expire visibility timeout after ack");

        // Queue should be empty
        assertEquals(0, bus.queueSize(), "Queue should be empty after ack");
        assertEquals(0, bus.inFlightSize(), "No messages should be in-flight after ack");
    }

    @Test
    void testVisibilityTimeout_NackCausesRedelivery() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-004", "IDEM-004");

        // Setup
        bus.publish(envelope, 0);

        // When: dequeue and nack
        List<Envelope> batch1 = bus.dequeue(1);
        assertEquals(1, batch1.size(), "Should dequeue one message");

        bus.nack(envelope);

        // Then: message should be back in queue
        assertEquals(1, bus.queueSize(), "Message should be back in queue after nack");
        assertEquals(0, bus.inFlightSize(), "No messages should be in-flight after nack");

        // Can dequeue again
        List<Envelope> batch2 = bus.dequeue(1);
        assertEquals(1, batch2.size(), "Should be able to dequeue again after nack");
    }

    @Test
    void testVisibilityTimeout_AutomaticExpiration() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-005", "IDEM-005");

        // Setup: use short visibility timeout for testing
        InMemoryBus shortTimeoutBus = new InMemoryBus(100); // 100ms timeout
        shortTimeoutBus.publish(envelope, 0);

        // When: dequeue and wait for visibility timeout
        List<Envelope> batch1 = shortTimeoutBus.dequeue(1);
        assertEquals(1, batch1.size(), "Should dequeue one message");
        assertEquals(1, shortTimeoutBus.inFlightSize(), "Message should be in-flight");

        // Sleep longer than visibility timeout
        sleep(150);

        // Process visibility timeouts
        int expiredCount = shortTimeoutBus.processVisibilityTimeouts();

        // Then: message should be expired and redelivered
        assertEquals(1, expiredCount, "One message should have expired");
        assertEquals(0, shortTimeoutBus.inFlightSize(), "No messages should be in-flight after expiration");
        assertEquals(1, shortTimeoutBus.queueSize(), "Message should be back in queue");
    }

    @Test
    void testLongRunningOperation_CanBeDetected() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-006", "IDEM-006");
        OpId opId = envelope.opId();

        // Setup: operation in IN_PROGRESS for long time
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // Wait a bit to simulate time passing
        sleep(100);

        // When: scan for long-running operations
        List<OpId> longRunningOps = store.scanInProgress(50, 10);

        // Then: operation should be detected as long-running
        assertEquals(1, longRunningOps.size(), "Should detect one long-running operation");
        assertEquals(opId, longRunningOps.get(0), "Should be our test operation");
    }

    @Test
    void testFinalizeIdempotency_MultipleAttemptsHandled() {
        // Given
        Envelope envelope = createTestEnvelope("BIZ-007", "IDEM-007");
        OpId opId = envelope.opId();

        // Setup
        store.setState(opId, OperationState.IN_PROGRESS);
        store.storeEnvelope(opId, envelope);

        // When: first finalize succeeds
        store.writeAhead(opId, Ok.of(opId));
        store.finalize(opId, OperationState.COMPLETED);

        // Then: subsequent finalize attempts fail gracefully
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.finalize(opId, OperationState.COMPLETED),
                "Duplicate finalize should throw IllegalStateException");

        assertTrue(exception.getMessage().contains("already finalized"),
                "Exception message should indicate operation was already finalized");

        // State remains COMPLETED
        assertOperationState(opId, OperationState.COMPLETED);
    }

    @Test
    void testRedelivery_DifferentOperationsIndependent() {
        // Given: two different operations
        Envelope envelope1 = createTestEnvelope("BIZ-008", "IDEM-008");
        Envelope envelope2 = createTestEnvelope("BIZ-009", "IDEM-009");

        // Setup
        bus.publish(envelope1, 0);
        bus.publish(envelope2, 0);

        // When: dequeue both
        List<Envelope> batch = bus.dequeue(2);
        assertEquals(2, batch.size(), "Should dequeue both messages");

        // Process first, expire second
        bus.ack(envelope1);
        bus.expireVisibilityTimeout(envelope2);

        // Then: only second message redelivered
        assertEquals(1, bus.queueSize(), "Only second message should be in queue");
        assertEquals(0, bus.inFlightSize(), "No messages should be in-flight");
    }
}
