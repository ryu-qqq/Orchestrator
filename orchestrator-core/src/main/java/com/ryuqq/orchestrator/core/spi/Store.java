package com.ryuqq.orchestrator.core.spi;

import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.statemachine.OperationState;

import java.util.List;

/**
 * Persistent Storage SPI for operation state and write-ahead logging.
 *
 * <p>This interface provides abstractions for persistent storage operations
 * required by the Orchestrator components for state management and recovery.</p>
 *
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Write-ahead logging for outcome persistence before finalization</li>
 *   <li>Operation state finalization (COMPLETED/FAILED)</li>
 *   <li>Recovery support through WAL scanning (Finalizer)</li>
 *   <li>Long-running operation detection (Reaper)</li>
 *   <li>Operation state and envelope retrieval</li>
 * </ul>
 *
 * <p><strong>Write-Ahead Log (WAL) Pattern:</strong></p>
 * <pre>
 * 1. writeAhead(opId, outcome) → WAL entry created (PENDING)
 * 2. finalize(opId, state)     → State machine updated (COMPLETED/FAILED)
 * 3. WAL entry marked COMPLETED
 * </pre>
 *
 * <p><strong>Recovery Mechanisms:</strong></p>
 * <ul>
 *   <li>Finalizer: Completes interrupted writeAhead → finalize sequences</li>
 *   <li>Reaper: Reconciles long-running IN_PROGRESS operations</li>
 * </ul>
 *
 * <p><strong>Implementation Requirements:</strong></p>
 * <ul>
 *   <li>ACID Transactions: writeAhead and finalize should be transactional</li>
 *   <li>Thread-safe: All methods must be safely callable from multiple threads</li>
 *   <li>Concurrent-safe: Support concurrent access to different OpIds</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface Store {

    /**
     * Writes outcome to the write-ahead log (WAL) before state finalization.
     *
     * <p>This method durably persists the outcome with WAL state PENDING,
     * ensuring that the outcome can be recovered even if finalize() fails.</p>
     *
     * <p><strong>Transaction Boundary:</strong></p>
     * <pre>
     * BEGIN TRANSACTION;
     *   INSERT INTO write_ahead_log (op_id, outcome, state) VALUES (?, ?, 'PENDING');
     * COMMIT;
     * </pre>
     *
     * <p><strong>Idempotency:</strong> Multiple calls with the same opId should be idempotent.
     * If a WAL entry already exists, it should be updated or left unchanged based on implementation.</p>
     *
     * @param opId the operation ID
     * @param outcome the outcome to persist (Ok, Retry, Fail)
     * @throws IllegalArgumentException if opId or outcome is null
     */
    void writeAhead(OpId opId, Outcome outcome);

    /**
     * Finalizes the operation state in the state machine.
     *
     * <p>This method updates the operation state to its terminal state (COMPLETED or FAILED)
     * and marks the corresponding WAL entry as COMPLETED.</p>
     *
     * <p><strong>Transaction Boundary:</strong></p>
     * <pre>
     * BEGIN TRANSACTION;
     *   UPDATE operations SET state = ? WHERE op_id = ?;
     *   UPDATE write_ahead_log SET wal_state = 'COMPLETED' WHERE op_id = ?;
     * COMMIT;
     * </pre>
     *
     * <p><strong>State Validation:</strong></p>
     * <ul>
     *   <li>state must be OperationState.COMPLETED or OperationState.FAILED</li>
     *   <li>Attempting to finalize with non-terminal state should fail</li>
     * </ul>
     *
     * @param opId the operation ID
     * @param state the terminal state (COMPLETED or FAILED)
     * @throws IllegalArgumentException if opId is null or state is not terminal
     * @throws IllegalStateException if opId does not exist or is already finalized
     */
    void finalize(OpId opId, OperationState state);

    /**
     * Scans write-ahead log for entries in the specified state.
     *
     * <p>This method is used by the Finalizer to discover operations that
     * have completed outcome processing but have not been finalized yet.</p>
     *
     * <p><strong>Query Example (PENDING):</strong></p>
     * <pre>
     * SELECT op_id FROM write_ahead_log
     * WHERE wal_state = 'PENDING'
     * ORDER BY created_at ASC
     * LIMIT ?;
     * </pre>
     *
     * <p><strong>Batch Processing:</strong></p>
     * <ul>
     *   <li>Results ordered by creation time (oldest first)</li>
     *   <li>Finalizer processes each opId by calling finalize()</li>
     *   <li>Typical batchSize: 10-100</li>
     * </ul>
     *
     * @param state the write-ahead log state to scan for (PENDING or COMPLETED)
     * @param batchSize maximum number of entries to return
     * @return list of operation IDs in the specified state (may be empty)
     * @throws IllegalArgumentException if state is null or batchSize is not positive
     */
    List<OpId> scanWA(WriteAheadState state, int batchSize);

    /**
     * Retrieves the outcome from the write-ahead log.
     *
     * <p>This method fetches the persisted outcome for an operation
     * from the write-ahead log, regardless of WAL state.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Finalizer: Retrieve outcome to determine COMPLETED vs FAILED state</li>
     *   <li>Recovery: Inspect outcome for debugging or auditing</li>
     * </ul>
     *
     * @param opId the operation ID
     * @return the persisted outcome (Ok, Retry, Fail)
     * @throws IllegalArgumentException if opId is null
     * @throws IllegalStateException if no WAL entry exists for opId
     */
    Outcome getWriteAheadOutcome(OpId opId);

    /**
     * Scans for operations that have been IN_PROGRESS for longer than the timeout threshold.
     *
     * <p>This method is used by the Reaper to identify potentially stuck operations
     * that may require manual intervention or automatic recovery.</p>
     *
     * <p><strong>Query Example:</strong></p>
     * <pre>
     * SELECT op_id FROM operations
     * WHERE state = 'IN_PROGRESS'
     *   AND (current_timestamp - started_at) > ?
     * ORDER BY started_at ASC
     * LIMIT ?;
     * </pre>
     *
     * <p><strong>Timeout Calculation:</strong></p>
     * <ul>
     *   <li>timeoutThreshold: typically 5-60 minutes in milliseconds</li>
     *   <li>started_at: timestamp when operation entered IN_PROGRESS state</li>
     *   <li>current_timestamp - started_at > timeoutThreshold</li>
     * </ul>
     *
     * @param timeoutThreshold maximum allowed IN_PROGRESS duration in milliseconds
     * @param batchSize maximum number of entries to return
     * @return list of operation IDs that have exceeded timeout (may be empty)
     * @throws IllegalArgumentException if timeoutThreshold is not positive or batchSize is not positive
     */
    List<OpId> scanInProgress(long timeoutThreshold, int batchSize);

    /**
     * Retrieves the envelope for a given operation ID.
     *
     * <p>This method fetches the original envelope that initiated the operation,
     * which is required for retry scenarios and recovery operations.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Reaper: Re-publish envelope for stuck IN_PROGRESS operations</li>
     *   <li>Retry: Retrieve envelope for re-execution after backoff delay</li>
     * </ul>
     *
     * @param opId the operation ID
     * @return the envelope associated with this operation
     * @throws IllegalArgumentException if opId is null
     * @throws IllegalStateException if no envelope exists for opId
     */
    Envelope getEnvelope(OpId opId);

    /**
     * Retrieves the current operation state.
     *
     * <p>This method returns the current state of the operation in the state machine.</p>
     *
     * <p><strong>Possible States:</strong></p>
     * <ul>
     *   <li>PENDING: Operation accepted but not started</li>
     *   <li>IN_PROGRESS: Operation currently executing</li>
     *   <li>COMPLETED: Operation succeeded</li>
     *   <li>FAILED: Operation permanently failed</li>
     * </ul>
     *
     * @param opId the operation ID
     * @return the current operation state
     * @throws IllegalArgumentException if opId is null
     * @throws IllegalStateException if opId does not exist
     */
    OperationState getState(OpId opId);
}
