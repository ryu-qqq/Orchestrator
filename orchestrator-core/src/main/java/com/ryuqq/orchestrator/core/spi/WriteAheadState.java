package com.ryuqq.orchestrator.core.spi;

/**
 * Write-Ahead Log (WAL) entry state for Finalizer recovery.
 *
 * <p>This enum represents the state of a write-ahead log entry,
 * which is used to track operations that have completed outcome processing
 * but have not yet been finalized in the state machine.</p>
 *
 * <p><strong>State Transition Flow:</strong></p>
 * <pre>
 * PENDING → COMPLETED (after finalize() succeeds)
 * </pre>
 *
 * <p><strong>Recovery Scenario:</strong></p>
 * <pre>
 * 1. QueueWorker writes outcome to WAL (state: PENDING)
 * 2. Crash occurs before finalize() call
 * 3. Finalizer scans for PENDING entries
 * 4. Finalizer completes finalize() and marks COMPLETED
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public enum WriteAheadState {

    /**
     * Write-ahead log entry exists, but finalize() has not been called yet.
     *
     * <p><strong>Trigger for Finalizer:</strong></p>
     * <ul>
     *   <li>Finalizer scans for PENDING entries periodically</li>
     *   <li>Completes the interrupted finalize() operation</li>
     *   <li>Ensures eventual consistency between outcome and state</li>
     * </ul>
     */
    PENDING,

    /**
     * Write-ahead log entry has been successfully finalized.
     *
     * <p><strong>Terminal State:</strong></p>
     * <ul>
     *   <li>finalize() has been successfully called</li>
     *   <li>Operation state is now COMPLETED or FAILED</li>
     *   <li>WAL entry can be purged after retention period</li>
     * </ul>
     */
    COMPLETED;

    /**
     * Checks if this state indicates that finalization is still required.
     *
     * @return true if this state is PENDING, false otherwise
     */
    public boolean requiresFinalization() {
        return this == PENDING;
    }

    /**
     * Checks if this state indicates that finalization has been completed.
     *
     * @return true if this state is COMPLETED, false otherwise
     */
    public boolean isFinalized() {
        return this == COMPLETED;
    }
}
