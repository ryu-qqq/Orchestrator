package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.spi.Store;
import com.ryuqq.orchestrator.core.spi.WriteAheadState;
import com.ryuqq.orchestrator.core.statemachine.OperationState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of Store for testing purposes.
 *
 * <p>This implementation provides thread-safe persistent storage operations
 * using ConcurrentHashMap for concurrent access scenarios.</p>
 *
 * <p><strong>Limitations:</strong></p>
 * <ul>
 *   <li>No actual ACID transaction support (in-memory only)</li>
 *   <li>State changes are not rolled back automatically on exceptions</li>
 *   <li>Suitable for Contract Tests but not production use</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class InMemoryStore implements Store {

    private final ConcurrentHashMap<OpId, OperationState> operations;
    private final ConcurrentHashMap<OpId, WALEntry> walEntries;
    private final ConcurrentHashMap<OpId, Envelope> envelopes;
    private final ConcurrentHashMap<OpId, Long> startTimes;

    /**
     * Creates a new InMemoryStore with empty storage.
     */
    public InMemoryStore() {
        this.operations = new ConcurrentHashMap<>();
        this.walEntries = new ConcurrentHashMap<>();
        this.envelopes = new ConcurrentHashMap<>();
        this.startTimes = new ConcurrentHashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeAhead(OpId opId, Outcome outcome) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome cannot be null");
        }

        walEntries.put(opId, new WALEntry(opId, outcome, WriteAheadState.PENDING, System.currentTimeMillis()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finalize(OpId opId, OperationState state) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (state != OperationState.COMPLETED && state != OperationState.FAILED) {
            throw new IllegalArgumentException("state must be COMPLETED or FAILED, but was: " + state);
        }

        // Check if operation exists
        if (!operations.containsKey(opId) && !walEntries.containsKey(opId)) {
            throw new IllegalStateException("Operation not found for opId: " + opId);
        }

        // Check if already finalized
        OperationState currentState = operations.get(opId);
        if (currentState == OperationState.COMPLETED || currentState == OperationState.FAILED) {
            throw new IllegalStateException("Operation already finalized with state: " + currentState);
        }

        // Update operation state
        operations.put(opId, state);

        // Update WAL state to COMPLETED (atomic update)
        walEntries.computeIfPresent(opId, (id, entry) ->
                new WALEntry(opId, entry.outcome, WriteAheadState.COMPLETED, entry.createdAt));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OpId> scanWA(WriteAheadState state, int batchSize) {
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, but was: " + batchSize);
        }

        return walEntries.values().stream()
                .filter(entry -> entry.state == state)
                .sorted((e1, e2) -> Long.compare(e1.createdAt, e2.createdAt))
                .limit(batchSize)
                .map(entry -> entry.opId)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Outcome getWriteAheadOutcome(OpId opId) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }

        WALEntry entry = walEntries.get(opId);
        if (entry == null) {
            throw new IllegalStateException("No WAL entry found for opId: " + opId);
        }

        return entry.outcome;
    }

    /**
     * {@inheritDoc}
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
                .filter(entry -> entry.getValue() == OperationState.IN_PROGRESS)
                .filter(entry -> {
                    Long startTime = startTimes.get(entry.getKey());
                    return startTime != null && (now - startTime) > timeoutThreshold;
                })
                .sorted(java.util.Comparator.comparing(entry -> startTimes.get(entry.getKey())))
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

        OperationState state = operations.get(opId);
        if (state == null) {
            throw new IllegalStateException("No operation found for opId: " + opId);
        }

        return state;
    }

    /**
     * Stores an envelope for the given operation. Used for test setup.
     *
     * @param opId the operation ID
     * @param envelope the envelope to store
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
     * Sets the operation state. Used for test setup.
     *
     * @param opId the operation ID
     * @param state the state to set
     */
    public void setState(OpId opId, OperationState state) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }

        operations.put(opId, state);

        // Track start time for IN_PROGRESS state
        if (state == OperationState.IN_PROGRESS) {
            startTimes.putIfAbsent(opId, System.currentTimeMillis());
        }
    }

    /**
     * Clears all stored data. Used for test cleanup.
     */
    public void clear() {
        operations.clear();
        walEntries.clear();
        envelopes.clear();
        startTimes.clear();
    }

    /**
     * Returns the WAL state for the given operation. Used for test assertions.
     *
     * @param opId the operation ID
     * @return the WAL state, or null if no WAL entry exists
     */
    public WriteAheadState getWALState(OpId opId) {
        WALEntry entry = walEntries.get(opId);
        return entry != null ? entry.state : null;
    }

    /**
     * Internal class representing a Write-Ahead Log entry.
     */
    private static class WALEntry {
        private final OpId opId;
        private final Outcome outcome;
        private final WriteAheadState state;
        private final long createdAt;

        WALEntry(OpId opId, Outcome outcome, WriteAheadState state, long createdAt) {
            this.opId = opId;
            this.outcome = outcome;
            this.state = state;
            this.createdAt = createdAt;
        }
    }
}
