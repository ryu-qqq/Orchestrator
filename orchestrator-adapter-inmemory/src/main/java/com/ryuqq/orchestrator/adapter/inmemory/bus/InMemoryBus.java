package com.ryuqq.orchestrator.adapter.inmemory.bus;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Fail;
import com.ryuqq.orchestrator.core.spi.Bus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * In-memory implementation of {@link Bus} SPI for testing and reference purposes.
 *
 * <p>This implementation provides thread-safe message queue operations
 * using {@link DelayQueue} for delayed message delivery and visibility timeout simulation.</p>
 *
 * <p><strong>Architecture:</strong></p>
 * <ul>
 *   <li><strong>Main Queue:</strong> DelayQueue&lt;DelayedEnvelope&gt; - Delayed message delivery with timestamp-based ordering</li>
 *   <li><strong>In-Flight Tracking:</strong> ConcurrentHashMap&lt;OpId, EnvelopeWrapper&gt; - Visibility timeout management</li>
 *   <li><strong>Dead Letter Queue:</strong> CopyOnWriteArrayList&lt;DLQEntry&gt; - Permanently failed messages</li>
 * </ul>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Delayed message delivery (publish with delayMs)</li>
 *   <li>Visibility timeout simulation (30 seconds default)</li>
 *   <li>Dead Letter Queue (DLQ) for permanently failed messages</li>
 *   <li>At-least-once delivery semantics</li>
 *   <li>Thread-safe operations using concurrent data structures</li>
 * </ul>
 *
 * <p><strong>Performance Characteristics:</strong></p>
 * <ul>
 *   <li><strong>publish:</strong> O(log N) - DelayQueue insertion</li>
 *   <li><strong>dequeue:</strong> O(M log N) where M = batchSize - DelayQueue polling</li>
 *   <li><strong>ack/nack:</strong> O(1) - ConcurrentHashMap operations</li>
 *   <li><strong>publishToDLQ:</strong> O(1) - CopyOnWriteArrayList append</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // Create bus with default 30-second visibility timeout
 * Bus bus = new InMemoryBus();
 *
 * // Publish message with 5-second delay (for retry scenarios)
 * bus.publish(envelope, 5000);
 *
 * // Dequeue batch of messages
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
public class InMemoryBus implements Bus {

    /**
     * Default visibility timeout: 30 seconds.
     *
     * <p>Messages dequeued from the queue remain invisible to other consumers
     * for this duration. If not acknowledged within this time, they automatically
     * return to the queue for redelivery.</p>
     */
    private static final long DEFAULT_VISIBILITY_TIMEOUT_MS = 30_000L;

    /**
     * Main message queue with delayed delivery support.
     *
     * <p>DelayQueue provides timestamp-based ordering and automatic delay handling.
     * Messages become available for dequeue only after their delay period expires.</p>
     */
    private final DelayQueue<DelayedEnvelope> queue;

    /**
     * Tracks in-flight messages with their visibility timeout deadlines.
     *
     * <p>Key: OpId, Value: EnvelopeWrapper with visibility timeout timestamp.
     * Messages in this map are invisible to other consumers until:</p>
     * <ul>
     *   <li>Acknowledged (ack) - permanently removed</li>
     *   <li>Negative acknowledged (nack) - returned to queue</li>
     *   <li>Visibility timeout expired - automatically returned to queue</li>
     * </ul>
     */
    private final ConcurrentHashMap<OpId, EnvelopeWrapper> inFlight;

    /**
     * Dead Letter Queue for permanently failed messages.
     *
     * <p>CopyOnWriteArrayList provides thread-safe iteration and snapshot isolation.
     * Used for messages that have exceeded retry limits or encountered permanent failures.</p>
     */
    private final List<DLQEntry> dlq;

    /**
     * Visibility timeout duration in milliseconds.
     *
     * <p>Determines how long a dequeued message remains invisible to other consumers.</p>
     */
    private final long visibilityTimeoutMs;

    /**
     * Creates a new InMemoryBus with default visibility timeout (30 seconds).
     */
    public InMemoryBus() {
        this(DEFAULT_VISIBILITY_TIMEOUT_MS);
    }

    /**
     * Creates a new InMemoryBus with custom visibility timeout.
     *
     * @param visibilityTimeoutMs visibility timeout in milliseconds
     * @throws IllegalArgumentException if visibilityTimeoutMs is not positive
     */
    public InMemoryBus(long visibilityTimeoutMs) {
        if (visibilityTimeoutMs <= 0) {
            throw new IllegalArgumentException("visibilityTimeoutMs must be positive, but was: " + visibilityTimeoutMs);
        }

        this.queue = new DelayQueue<>();
        this.inFlight = new ConcurrentHashMap<>();
        this.dlq = new CopyOnWriteArrayList<>();
        this.visibilityTimeoutMs = visibilityTimeoutMs;
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Creates DelayedEnvelope with calculated availability timestamp</li>
     *   <li>DelayQueue automatically orders messages by availability time</li>
     *   <li>delayMs = 0: immediate delivery (message available immediately)</li>
     *   <li>delayMs > 0: delayed delivery for retry scenarios</li>
     *   <li>Performance: O(log N) for insertion into DelayQueue</li>
     * </ul>
     */
    @Override
    public void publish(Envelope envelope, long delayMs) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs cannot be negative, but was: " + delayMs);
        }

        queue.put(new DelayedEnvelope(envelope, delayMs));
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Polls DelayQueue for messages whose delay has expired</li>
     *   <li>Marks dequeued messages as in-flight with visibility timeout</li>
     *   <li>Returns empty list if no messages available</li>
     *   <li>Visibility timeout starts immediately upon dequeue</li>
     *   <li>Performance: O(M log N) where M = batchSize</li>
     * </ul>
     */
    @Override
    public List<Envelope> dequeue(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, but was: " + batchSize);
        }

        List<Envelope> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (int i = 0; i < batchSize; i++) {
            DelayedEnvelope delayed = queue.poll();
            if (delayed == null) {
                break;
            }

            Envelope envelope = delayed.envelope;
            inFlight.put(envelope.opId(), new EnvelopeWrapper(envelope, now + visibilityTimeoutMs));
            result.add(envelope);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Removes message from in-flight tracking</li>
     *   <li>Message is permanently removed from queue</li>
     *   <li>Idempotent: safe to call multiple times</li>
     *   <li>Performance: O(1) HashMap removal</li>
     * </ul>
     */
    @Override
    public void ack(Envelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }

        inFlight.remove(envelope.opId());
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Removes message from in-flight tracking</li>
     *   <li>Re-publishes message to queue with zero delay (immediate availability)</li>
     *   <li>Enables immediate retry by same or different consumer</li>
     *   <li>Performance: O(log N) for re-publish</li>
     * </ul>
     */
    @Override
    public void nack(Envelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }

        inFlight.remove(envelope.opId());
        publish(envelope, 0); // Re-publish immediately
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Removes message from in-flight tracking</li>
     *   <li>Appends message to Dead Letter Queue with failure metadata</li>
     *   <li>DLQ entries include: envelope, failure details, timestamp</li>
     *   <li>Performance: O(1) for CopyOnWriteArrayList append</li>
     * </ul>
     */
    @Override
    public void publishToDLQ(Envelope envelope, Fail fail) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }
        if (fail == null) {
            throw new IllegalArgumentException("fail cannot be null");
        }

        inFlight.remove(envelope.opId());
        dlq.add(new DLQEntry(envelope, fail, System.currentTimeMillis()));
    }

    /**
     * Simulates visibility timeout expiration for in-flight messages.
     *
     * <p>Messages that have exceeded their visibility timeout are returned to the queue.</p>
     * <p>This method is called periodically or manually in tests to simulate timeout behavior.</p>
     *
     * @return number of messages returned to queue
     */
    public int processVisibilityTimeouts() {
        long now = System.currentTimeMillis();
        int count = 0;

        List<OpId> expiredOpIds = new ArrayList<>();
        for (var entry : inFlight.entrySet()) {
            if (entry.getValue().visibilityTimeout <= now) {
                expiredOpIds.add(entry.getKey());
            }
        }

        for (OpId opId : expiredOpIds) {
            EnvelopeWrapper wrapper = inFlight.remove(opId);
            if (wrapper != null) {
                publish(wrapper.envelope, 0); // Re-publish immediately
                count++;
            }
        }

        return count;
    }

    /**
     * Manually triggers visibility timeout for a specific envelope. Used for testing.
     *
     * @param envelope the envelope to expire
     * @return true if the envelope was in-flight and expired, false otherwise
     * @throws IllegalArgumentException if envelope is null
     */
    public boolean expireVisibilityTimeout(Envelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }

        EnvelopeWrapper wrapper = inFlight.remove(envelope.opId());
        if (wrapper != null) {
            publish(wrapper.envelope, 0);
            return true;
        }
        return false;
    }

    /**
     * Clears all messages from queue, in-flight, and DLQ. Used for test cleanup.
     */
    public void clear() {
        queue.clear();
        inFlight.clear();
        dlq.clear();
    }

    /**
     * Returns the number of messages in the queue (not in-flight). Used for test assertions.
     *
     * @return queue size
     */
    public int queueSize() {
        return queue.size();
    }

    /**
     * Returns the number of in-flight messages. Used for test assertions.
     *
     * @return in-flight count
     */
    public int inFlightSize() {
        return inFlight.size();
    }

    /**
     * Returns the number of messages in DLQ. Used for test assertions.
     *
     * @return DLQ size
     */
    public int dlqSize() {
        return dlq.size();
    }

    /**
     * Returns all DLQ entries. Used for test assertions.
     *
     * @return list of DLQ entries
     */
    public List<DLQEntry> getDLQEntries() {
        return new ArrayList<>(dlq);
    }

    /**
     * Internal class representing a delayed envelope in the queue.
     *
     * <p><strong>Delay Mechanism:</strong></p>
     * <ul>
     *   <li>availableAt = current time + delayMs</li>
     *   <li>DelayQueue uses getDelay() to determine availability</li>
     *   <li>Messages with delay <= 0 are immediately available</li>
     * </ul>
     */
    private static class DelayedEnvelope implements Delayed {
        private final Envelope envelope;
        private final long availableAt;

        DelayedEnvelope(Envelope envelope, long delayMs) {
            this.envelope = envelope;
            this.availableAt = System.currentTimeMillis() + delayMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = availableAt - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }
    }

    /**
     * Internal class representing an in-flight envelope with visibility timeout.
     *
     * <p><strong>Visibility Timeout:</strong></p>
     * <ul>
     *   <li>visibilityTimeout = dequeue time + visibility timeout duration</li>
     *   <li>Message invisible to other consumers until timeout or ack/nack</li>
     * </ul>
     */
    private static class EnvelopeWrapper {
        private final Envelope envelope;
        private final long visibilityTimeout;

        EnvelopeWrapper(Envelope envelope, long visibilityTimeout) {
            this.envelope = envelope;
            this.visibilityTimeout = visibilityTimeout;
        }
    }

    /**
     * Dead Letter Queue entry with failure metadata.
     *
     * <p><strong>DLQ Entry Contents:</strong></p>
     * <ul>
     *   <li>envelope: original message that failed</li>
     *   <li>fail: failure details (error code, message, cause)</li>
     *   <li>timestamp: when message was moved to DLQ</li>
     * </ul>
     */
    public static class DLQEntry {
        private final Envelope envelope;
        private final Fail fail;
        private final long timestamp;

        DLQEntry(Envelope envelope, Fail fail, long timestamp) {
            this.envelope = envelope;
            this.fail = fail;
            this.timestamp = timestamp;
        }

        public Envelope getEnvelope() {
            return envelope;
        }

        public Fail getFail() {
            return fail;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
