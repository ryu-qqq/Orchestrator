package com.ryuqq.orchestrator.core.spi;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.outcome.Fail;

import java.util.List;

/**
 * Message Queue SPI for asynchronous operation processing.
 *
 * <p>This interface provides abstractions for message queue operations
 * required by the QueueWorkerRunner, Finalizer, and Reaper components.</p>
 *
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Publishing envelopes to the queue with optional delay</li>
 *   <li>Dequeuing batches of envelopes for processing</li>
 *   <li>Acknowledging successfully processed messages</li>
 *   <li>Negative acknowledging failed messages for retry</li>
 *   <li>Publishing permanently failed messages to Dead Letter Queue (DLQ)</li>
 * </ul>
 *
 * <p><strong>Implementation Requirements:</strong></p>
 * <ul>
 *   <li>Thread-safe: All methods must be safely callable from multiple threads</li>
 *   <li>Idempotent: ack/nack operations should be idempotent</li>
 *   <li>Visibility Timeout: Implement message visibility timeout for dequeued messages</li>
 *   <li>At-least-once Delivery: Messages may be delivered multiple times</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // Publish with delay for retry
 * bus.publish(envelope, 5000L); // 5 seconds delay
 *
 * // Dequeue batch for processing
 * List&lt;Envelope&gt; batch = bus.dequeue(10);
 *
 * // Process and acknowledge
 * for (Envelope envelope : batch) {
 *     try {
 *         processEnvelope(envelope);
 *         bus.ack(envelope);
 *     } catch (Exception e) {
 *         bus.nack(envelope);
 *     }
 * }
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface Bus {

    /**
     * Publishes an envelope to the message queue with optional delay.
     *
     * <p>This method is non-blocking and returns immediately after queueing the message.</p>
     *
     * <p><strong>Delay Behavior:</strong></p>
     * <ul>
     *   <li>delayMs = 0: Immediate delivery (standard queue behavior)</li>
     *   <li>delayMs > 0: Delayed delivery for retry scenarios</li>
     * </ul>
     *
     * @param envelope the envelope to publish
     * @param delayMs delay in milliseconds before the message becomes available for dequeue (0 for immediate)
     * @throws IllegalArgumentException if envelope is null or delayMs is negative
     */
    void publish(Envelope envelope, long delayMs);

    /**
     * Dequeues a batch of envelopes from the message queue.
     *
     * <p>This method retrieves up to {@code batchSize} messages and makes them
     * invisible to other consumers for a visibility timeout period.</p>
     *
     * <p><strong>Visibility Timeout:</strong></p>
     * <ul>
     *   <li>Messages remain invisible until ack/nack or timeout</li>
     *   <li>Timeout typically ranges from 30 seconds to 5 minutes</li>
     *   <li>Messages reappear in queue if not acknowledged before timeout</li>
     * </ul>
     *
     * @param batchSize maximum number of messages to retrieve (1-10 recommended)
     * @return list of dequeued envelopes (may be empty if queue is empty)
     * @throws IllegalArgumentException if batchSize is not positive
     */
    List<Envelope> dequeue(int batchSize);

    /**
     * Acknowledges successful processing of an envelope.
     *
     * <p>This method permanently removes the message from the queue,
     * indicating successful completion.</p>
     *
     * <p><strong>Idempotency:</strong> This method should be idempotent.
     * Multiple ack calls for the same envelope should not cause errors.</p>
     *
     * @param envelope the envelope to acknowledge
     * @throws IllegalArgumentException if envelope is null
     */
    void ack(Envelope envelope);

    /**
     * Negative acknowledges an envelope, making it available for retry.
     *
     * <p>This method returns the message to the queue immediately,
     * making it available for reprocessing by this or another consumer.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Temporary failures (e.g., network timeout, database contention)</li>
     *   <li>Processing cannot be completed within visibility timeout</li>
     *   <li>Explicit retry required based on business logic</li>
     * </ul>
     *
     * @param envelope the envelope to negative acknowledge
     * @throws IllegalArgumentException if envelope is null
     */
    void nack(Envelope envelope);

    /**
     * Publishes a permanently failed envelope to the Dead Letter Queue (DLQ).
     *
     * <p>This method removes the message from the main queue and stores it
     * in the DLQ along with failure metadata for manual investigation.</p>
     *
     * <p><strong>DLQ Scenarios:</strong></p>
     * <ul>
     *   <li>Retry budget exhausted (max attempts reached)</li>
     *   <li>Permanent business logic failure (e.g., invalid payload)</li>
     *   <li>Unrecoverable system errors</li>
     * </ul>
     *
     * @param envelope the envelope that permanently failed
     * @param fail the failure details (error code, message, cause)
     * @throws IllegalArgumentException if envelope or fail is null
     */
    void publishToDLQ(Envelope envelope, Fail fail);
}
