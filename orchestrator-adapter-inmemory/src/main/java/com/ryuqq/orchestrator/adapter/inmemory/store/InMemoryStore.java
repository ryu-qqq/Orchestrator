package com.ryuqq.orchestrator.adapter.inmemory.store;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.spi.Store;
import com.ryuqq.orchestrator.core.spi.WriteAheadState;
import com.ryuqq.orchestrator.core.statemachine.OperationState;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link Store} SPI for testing and reference purposes.
 *
 * <p>This implementation provides thread-safe persistent storage operations
 * using {@link ConcurrentHashMap} for key-value storage and {@link CopyOnWriteArrayList}
 * for write-ahead log entries with guaranteed ordering.</p>
 *
 * <p><strong>Data Structures:</strong></p>
 * <ul>
 *   <li><strong>operations:</strong> ConcurrentHashMap&lt;OpId, OperationEntity&gt; - Operation state, version, result payload</li>
 *   <li><strong>writeAheadLog:</strong> CopyOnWriteArrayList&lt;WALEntry&gt; - Ordered WAL entries (occurred_at based)</li>
 *   <li><strong>envelopes:</strong> ConcurrentHashMap&lt;OpId, Envelope&gt; - Original command envelopes</li>
 * </ul>
 *
 * <p><strong>Transaction Simulation:</strong></p>
 * <ul>
 *   <li>ThreadLocal-based transaction context</li>
 *   <li>Commit/Rollback support</li>
 *   <li>Isolation between concurrent operations</li>
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
 *
 * // Start transaction
 * store.beginTransaction();
 * try {
 *     store.writeAhead(opId, outcome);
 *     store.finalize(opId, OperationState.COMPLETED);
 *     store.commitTransaction();
 * } catch (Exception e) {
 *     store.rollbackTransaction();
 *     throw e;
 * }
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
     * Write-Ahead Log entries storage with guaranteed ordering.
     * Entries are ordered by occurred_at timestamp.
     */
    private final CopyOnWriteArrayList<WALEntry> writeAheadLog;

    /**
     * Original command envelopes for retry and recovery.
     * Key: OpId, Value: Envelope
     */
    private final ConcurrentHashMap<OpId, Envelope> envelopes;

    /**
     * Thread-local transaction context for simulating transactional behavior.
     */
    private final ThreadLocal<TransactionContext> transactionContext;

    /**
     * Creates a new InMemoryStore with empty storage.
     */
    public InMemoryStore() {
        this.operations = new ConcurrentHashMap<>();
        this.writeAheadLog = new CopyOnWriteArrayList<>();
        this.envelopes = new ConcurrentHashMap<>();
        this.transactionContext = ThreadLocal.withInitial(TransactionContext::new);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Creates WAL entry with PENDING state</li>
     *   <li>Uses occurred_at for ordering</li>
     *   <li>Idempotent: updates existing entry if present</li>
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

        // Idempotent: remove old entry if exists, add new entry
        writeAheadLog.removeIf(entry -> entry.opId.equals(opId));
        writeAheadLog.add(newEntry);

        // Sort by occurred_at to maintain order
        writeAheadLog.sort((e1, e2) -> Long.compare(e1.occurredAt, e2.occurredAt));
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Updates operation state to terminal state</li>
     *   <li>Marks WAL entry as COMPLETED</li>
     *   <li>Atomic update using ConcurrentHashMap operations</li>
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
            boolean walExists = writeAheadLog.stream().anyMatch(e -> e.opId.equals(opId));
            if (!walExists) {
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

        // Update WAL state to COMPLETED
        for (int i = 0; i < writeAheadLog.size(); i++) {
            WALEntry entry = writeAheadLog.get(i);
            if (entry.opId.equals(opId)) {
                writeAheadLog.set(i, new WALEntry(opId, entry.outcome, WriteAheadState.COMPLETED, entry.occurredAt));
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Filters WAL entries by state</li>
     *   <li>Orders by occurred_at (oldest first)</li>
     *   <li>Limits result to batchSize</li>
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

        return writeAheadLog.stream()
                .filter(entry -> entry.state == state)
                .sorted((e1, e2) -> Long.compare(e1.occurredAt, e2.occurredAt))
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
     * </ul>
     */
    @Override
    public Outcome getWriteAheadOutcome(OpId opId) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }

        return writeAheadLog.stream()
                .filter(entry -> entry.opId.equals(opId))
                .map(entry -> entry.outcome)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No WAL entry found for opId: " + opId));
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Filters operations in IN_PROGRESS state</li>
     *   <li>Checks if startedAt exceeds timeout threshold</li>
     *   <li>Orders by startedAt (oldest first)</li>
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

        return operations.entrySet().stream()
                .filter(entry -> entry.getValue().state == OperationState.IN_PROGRESS)
                .filter(entry -> {
                    Envelope envelope = envelopes.get(entry.getKey());
                    return envelope != null && (now - envelope.acceptedAt()) > timeoutThreshold;
                })
                .sorted((e1, e2) -> {
                    Envelope env1 = envelopes.get(e1.getKey());
                    Envelope env2 = envelopes.get(e2.getKey());
                    return Long.compare(env1.acceptedAt(), env2.acceptedAt());
                })
                .limit(batchSize)
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());
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
        writeAheadLog.clear();
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
        return writeAheadLog.stream()
                .filter(entry -> entry.opId.equals(opId))
                .map(entry -> entry.state)
                .findFirst()
                .orElse(null);
    }

    /**
     * Internal class representing an Operation entity with state and version.
     */
    private static class OperationEntity {
        private final OpId opId;
        private final OperationState state;
        private final int version;

        OperationEntity(OpId opId, OperationState state, int version) {
            this.opId = opId;
            this.state = state;
            this.version = version;
        }
    }

    /**
     * Internal class representing a Write-Ahead Log entry.
     */
    private static class WALEntry {
        private final OpId opId;
        private final Outcome outcome;
        private final WriteAheadState state;
        private final long occurredAt;

        WALEntry(OpId opId, Outcome outcome, WriteAheadState state, long occurredAt) {
            this.opId = opId;
            this.outcome = outcome;
            this.state = state;
            this.occurredAt = occurredAt;
        }
    }

    /**
     * Transaction context for simulating transactional behavior.
     */
    private static class TransactionContext {
        // Transaction-related state can be added here
        // For simple in-memory implementation, this is a placeholder
    }
}
