package com.ryuqq.orchestrator.testkit.contract;

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
 * In-memory implementation of Bus for testing purposes.
 *
 * <p>This implementation provides thread-safe message queue operations
 * using DelayQueue for delayed message delivery and visibility timeout simulation.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Delayed message delivery (publish with delayMs)</li>
 *   <li>Visibility timeout simulation (30 seconds default)</li>
 *   <li>Dead Letter Queue (DLQ) for permanently failed messages</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class InMemoryBus implements Bus {

    private static final long DEFAULT_VISIBILITY_TIMEOUT_MS = 30_000L; // 30 seconds

    private final DelayQueue<DelayedEnvelope> queue;
    private final ConcurrentHashMap<OpId, EnvelopeWrapper> inFlight;
    private final List<DLQEntry> dlq;
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
     */
    public InMemoryBus(long visibilityTimeoutMs) {
        this.queue = new DelayQueue<>();
        this.inFlight = new ConcurrentHashMap<>();
        this.dlq = new CopyOnWriteArrayList<>();
        this.visibilityTimeoutMs = visibilityTimeoutMs;
    }

    /**
     * {@inheritDoc}
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
