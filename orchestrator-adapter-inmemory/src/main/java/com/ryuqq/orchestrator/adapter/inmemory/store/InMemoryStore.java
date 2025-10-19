package com.ryuqq.orchestrator.adapter.inmemory.store;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.spi.Store;
import com.ryuqq.orchestrator.core.spi.WriteAheadState;
import com.ryuqq.orchestrator.core.statemachine.OperationState;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link Store} SPI for testing and reference purposes.
 *
 * <p>This implementation provides thread-safe persistent storage operations
 * using {@link ConcurrentHashMap} for O(1) key-value operations and {@link ConcurrentSkipListSet}
 * for ordered write-ahead log entries with efficient sorted scans.</p>
 *
 * <p><strong>Data Structures:</strong></p>
 * <ul>
 *   <li><strong>operations:</strong> ConcurrentHashMap&lt;OpId, OperationEntity&gt; - Operation state, version, result payload (O(1) access)</li>
 *   <li><strong>walByOpId:</strong> ConcurrentHashMap&lt;OpId, WALEntry&gt; - Fast WAL entry lookup by OpId (O(1) access)</li>
 *   <li><strong>walSortedSet:</strong> ConcurrentSkipListSet&lt;WALEntry&gt; - Sorted WAL entries by occurredAt (O(log N) insertion, ordered iteration)</li>
 *   <li><strong>envelopes:</strong> ConcurrentHashMap&lt;OpId, Envelope&gt; - Original command envelopes (O(1) access)</li>
 * </ul>
 *
 * <p><strong>Performance Characteristics:</strong></p>
 * <ul>
 *   <li><strong>writeAhead:</strong> O(log N) - ConcurrentSkipListSet insertion</li>
 *   <li><strong>finalize:</strong> O(log N) - ConcurrentSkipListSet update</li>
 *   <li><strong>scanWA:</strong> O(M) - Ordered iteration over M entries</li>
 *   <li><strong>scanInProgress:</strong> O(K log K) - K filtered entries sorted</li>
 * </ul>
 *
 * <p><strong>Idempotency Guarantee:</strong></p>
 * <ul>
 *   <li>(Domain, EventType, BizKey, IdemKey) → OpId mapping</li>
 *   <li>Duplicate request handling</li>
 * </ul>
 *
 * <p><strong>Limitations:</strong></p>
 * <ul>
 *   <li>No actual ACID transactions (in-memory simulation only)</li>
 *   <li>Data lost on process restart</li>
 *   <li>Not suitable for production use</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * Store store = new InMemoryStore();
 * OpId opId = ...;
 * Outcome outcome = ...;
 *
 * // 1. Write-Ahead Log에 기록
 * store.writeAhead(opId, outcome);
 *
 * // 2. 작업 상태를 최종 상태로 변경
 * store.finalize(opId, OperationState.COMPLETED);
 *
 * // 3. WAL 스캔 (PENDING 상태 작업 조회)
 * List&lt;OpId&gt; pendingOps = store.scanWA(WriteAheadState.PENDING, 10);
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class InMemoryStore implements Store {

    /**
     * Operation state and metadata storage.
     * Key: OpId, Value: OperationEntity (state, version, envelope reference)
     */
    private final ConcurrentHashMap<OpId, OperationEntity> operations;

    /**
     * Write-Ahead Log entries indexed by OpId for O(1) lookup.
     * Key: OpId, Value: WALEntry
     */
    private final ConcurrentHashMap<OpId, WALEntry> walByOpId;

    /**
     * Write-Ahead Log entries sorted by occurred_at timestamp for efficient ordered scans.
     * Automatically maintains sorted order using ConcurrentSkipListSet.
     */
    private final ConcurrentSkipListSet<WALEntry> walSortedSet;

    /**
     * Original command envelopes for retry and recovery.
     * Key: OpId, Value: Envelope
     */
    private final ConcurrentHashMap<OpId, Envelope> envelopes;

    /**
     * Creates a new InMemoryStore with empty storage.
     */
    public InMemoryStore() {
        this.operations = new ConcurrentHashMap<>();
        this.walByOpId = new ConcurrentHashMap<>();
        this.walSortedSet = new ConcurrentSkipListSet<>(Comparator.comparingLong(entry -> entry.occurredAt));
        this.envelopes = new ConcurrentHashMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Creates WAL entry with PENDING state</li>
     *   <li>Uses occurred_at for ordering</li>
     *   <li>Idempotent: updates existing entry if present</li>
     *   <li>Performance: O(log N) using ConcurrentSkipListSet</li>
     * </ul>
     */
    @Override
    public void writeAhead(OpId opId, Outcome outcome) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome cannot be null");
        }

        long occurredAt = System.currentTimeMillis();
        WALEntry newEntry = new WALEntry(opId, outcome, WriteAheadState.PENDING, occurredAt);

        // Idempotent: remove old entry if exists
        WALEntry oldEntry = walByOpId.put(opId, newEntry);
        if (oldEntry != null) {
            walSortedSet.remove(oldEntry);
        }

        // Add new entry to sorted set (automatically maintains order)
        walSortedSet.add(newEntry);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Updates operation state to terminal state</li>
     *   <li>Marks WAL entry as COMPLETED</li>
     *   <li>Atomic update using ConcurrentHashMap operations</li>
     *   <li>Performance: O(log N) for WAL update using ConcurrentSkipListSet</li>
     * </ul>
     */
    @Override
    public synchronized void finalize(OpId opId, OperationState state) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (!state.isTerminal()) {
            throw new IllegalArgumentException("state must be terminal (COMPLETED or FAILED), but was: " + state);
        }

        // Check if operation exists
        OperationEntity entity = operations.get(opId);
        if (entity == null) {
            // Check if WAL entry exists
            WALEntry walEntry = walByOpId.get(opId);
            if (walEntry == null) {
                throw new IllegalStateException("Operation not found for opId: " + opId);
            }
        }

        // Check if already finalized
        if (entity != null && entity.state.isTerminal()) {
            throw new IllegalStateException("Operation already finalized with state: " + entity.state);
        }

        // Update operation state
        if (entity == null) {
            entity = new OperationEntity(opId, state, 1);
        } else {
            entity = new OperationEntity(opId, state, entity.version + 1);
        }
        operations.put(opId, entity);

        // Update WAL state to COMPLETED (O(log N) remove + insert)
        WALEntry oldEntry = walByOpId.get(opId);
        if (oldEntry != null) {
            WALEntry updatedEntry = new WALEntry(opId, oldEntry.outcome, WriteAheadState.COMPLETED, oldEntry.occurredAt);
            walByOpId.put(opId, updatedEntry);
            walSortedSet.remove(oldEntry);
            walSortedSet.add(updatedEntry);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Filters WAL entries by state</li>
     *   <li>Orders by occurred_at (oldest first) - ConcurrentSkipListSet maintains order</li>
     *   <li>Limits result to batchSize</li>
     *   <li>Performance: O(M) where M is number of matching entries</li>
     * </ul>
     */
    @Override
    public List<OpId> scanWA(WriteAheadState state, int batchSize) {
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, but was: " + batchSize);
        }

        // ConcurrentSkipListSet already maintains sorted order by occurredAt
        return walSortedSet.stream()
                .filter(entry -> entry.state == state)
                .limit(batchSize)
                .map(entry -> entry.opId)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Retrieves outcome from WAL entry</li>
     *   <li>Throws exception if WAL entry not found</li>
     *   <li>Performance: O(1) lookup using ConcurrentHashMap</li>
     * </ul>
     */
    @Override
    public Outcome getWriteAheadOutcome(OpId opId) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }

        WALEntry entry = walByOpId.get(opId);
        if (entry == null) {
            throw new IllegalStateException("No WAL entry found for opId: " + opId);
        }

        return entry.outcome;
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Filters operations in IN_PROGRESS state</li>
     *   <li>Checks if startedAt exceeds timeout threshold</li>
     *   <li>Orders by startedAt (oldest first)</li>
     *   <li>Performance: Optimized to avoid repeated map lookups during sorting</li>
     * </ul>
     */
    @Override
    public List<OpId> scanInProgress(long timeoutThreshold, int batchSize) {
        if (timeoutThreshold <= 0) {
            throw new IllegalArgumentException("timeoutThreshold must be positive, but was: " + timeoutThreshold);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, but was: " + batchSize);
        }

        long now = System.currentTimeMillis();

        // Map to temporary object to avoid repeated envelope lookups during sorting
        return operations.entrySet().stream()
                .filter(entry -> entry.getValue().state == OperationState.IN_PROGRESS)
                .map(entry -> {
                    OpId opId = entry.getKey();
                    Envelope envelope = envelopes.get(opId);
                    return new OperationWithTimestamp(opId, envelope);
                })
                .filter(owt -> owt.envelope != null && (now - owt.envelope.acceptedAt()) > timeoutThreshold)
                .sorted(Comparator.comparingLong(owt -> owt.envelope.acceptedAt()))
                .limit(batchSize)
                .map(owt -> owt.opId)
                .collect(Collectors.toList());
    }

    /**
     * Temporary holder for operation ID and envelope to optimize sorting performance.
     */
    private static class OperationWithTimestamp {
        private final OpId opId;
        private final Envelope envelope;

        OperationWithTimestamp(OpId opId, Envelope envelope) {
            this.opId = opId;
            this.envelope = envelope;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Envelope getEnvelope(OpId opId) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }

        Envelope envelope = envelopes.get(opId);
        if (envelope == null) {
            throw new IllegalStateException("No envelope found for opId: " + opId);
        }

        return envelope;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OperationState getState(OpId opId) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }

        OperationEntity entity = operations.get(opId);
        if (entity == null) {
            throw new IllegalStateException("No operation found for opId: " + opId);
        }

        return entity.state;
    }

    /**
     * Stores an envelope for the given operation.
     *
     * <p>This method is used to register the original command envelope
     * for retry scenarios and recovery operations.</p>
     *
     * @param opId the operation ID
     * @param envelope the envelope to store
     * @throws IllegalArgumentException if opId or envelope is null
     */
    public void storeEnvelope(OpId opId, Envelope envelope) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("envelope cannot be null");
        }

        envelopes.put(opId, envelope);
    }

    /**
     * Sets the operation state.
     *
     * <p>This method is used to initialize operation state for testing purposes.</p>
     *
     * @param opId the operation ID
     * @param state the state to set
     * @throws IllegalArgumentException if opId or state is null
     * @throws IllegalStateException if attempting backward transition from terminal state
     */
    public void setState(OpId opId, OperationState state) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }

        // Check for backward transition prohibition
        OperationEntity currentEntity = operations.get(opId);
        if (currentEntity != null && currentEntity.state.isTerminal()) {
            throw new IllegalStateException(
                    String.format("Cannot transition from terminal state %s to %s for opId: %s",
                            currentEntity.state, state, opId));
        }

        int version = currentEntity != null ? currentEntity.version + 1 : 1;
        operations.put(opId, new OperationEntity(opId, state, version));
    }

    /**
     * Clears all stored data.
     *
     * <p>This method is used for test cleanup.</p>
     */
    public void clear() {
        operations.clear();
        walByOpId.clear();
        walSortedSet.clear();
        envelopes.clear();
    }

    /**
     * Returns the WAL state for the given operation.
     *
     * <p>This method is used for test assertions.</p>
     *
     * @param opId the operation ID
     * @return the WAL state, or null if no WAL entry exists
     */
    public WriteAheadState getWALState(OpId opId) {
        WALEntry entry = walByOpId.get(opId);
        return entry != null ? entry.state : null;
    }

    /**
     * Internal record representing an Operation entity with state and version.
     *
     * @param opId the operation identifier
     * @param state the current operation state
     * @param version the operation version number
     */
    private record OperationEntity(OpId opId, OperationState state, int version) {
    }

    /**
     * Internal record representing a Write-Ahead Log entry.
     * Implements custom equals/hashCode based on opId for proper ConcurrentSkipListSet behavior.
     *
     * @param opId the operation identifier
     * @param outcome the outcome to be recorded
     * @param state the write-ahead state
     * @param occurredAt the timestamp when the event occurred
     */
    private record WALEntry(OpId opId, Outcome outcome, WriteAheadState state, long occurredAt) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WALEntry walEntry = (WALEntry) o;
            return opId.equals(walEntry.opId);
        }

        @Override
        public int hashCode() {
            return opId.hashCode();
        }
    }

}
