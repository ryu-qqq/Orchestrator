package com.ryuqq.orchestrator.application.runtime;

/**
 * Asynchronous Operation Runtime.
 *
 * <p>This interface defines the core runtime behavior for asynchronous
 * operation processing via message queue polling.</p>
 *
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Continuous polling of message queue for pending operations</li>
 *   <li>Orchestration of operation execution flow</li>
 *   <li>Outcome handling and state finalization</li>
 *   <li>Concurrency control and error recovery</li>
 * </ul>
 *
 * <p><strong>Runtime Operation Flow:</strong></p>
 * <pre>
 * pump() starts
 *   ↓
 * while (running):
 *   1. Dequeue messages from Bus (batch)
 *   2. For each Envelope:
 *      a. Execute via Executor
 *      b. Await Outcome
 *      c. Handle Outcome:
 *         - Ok → writeAhead → finalize(COMPLETED)
 *         - Retry → check RetryBudget → re-publish or finalize(FAILED)
 *         - Fail → finalize(FAILED)
 *      d. Acknowledge message to Bus
 *   3. Sleep (pollingIntervalMs)
 * </pre>
 *
 * <p><strong>Execution Context:</strong></p>
 * <ul>
 *   <li>pump() is typically invoked as a background task (@Scheduled or ExecutorService)</li>
 *   <li>Implementations should be thread-safe for multi-instance deployment</li>
 *   <li>Graceful shutdown support (interrupt pump() loop cleanly)</li>
 * </ul>
 *
 * <p><strong>Configuration:</strong></p>
 * <ul>
 *   <li>pollingIntervalMs: Time between dequeue attempts (default: 100ms)</li>
 *   <li>batchSize: Number of messages to dequeue per iteration (default: 10)</li>
 *   <li>concurrency: Number of concurrent processing threads (default: 5)</li>
 *   <li>maxProcessingTimeMs: Maximum time per operation (default: 30000ms)</li>
 * </ul>
 *
 * <p><strong>Integration with Other Components:</strong></p>
 * <ul>
 *   <li>Bus: Dequeue messages, acknowledge processing</li>
 *   <li>Executor: Execute operations and retrieve outcomes</li>
 *   <li>Store: Write-ahead logging and state finalization</li>
 *   <li>Finalizer: Recovery for interrupted finalize() operations</li>
 *   <li>Reaper: Recovery for stuck IN_PROGRESS operations</li>
 * </ul>
 *
 * <p><strong>Error Handling Strategy:</strong></p>
 * <ul>
 *   <li>Transient errors → Retry with exponential backoff</li>
 *   <li>Permanent errors → Finalize as FAILED, publish to DLQ</li>
 *   <li>Unhandled exceptions → Log, nack message, continue processing</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // Typically invoked by Spring Scheduler
 * {@literal @Scheduled}(fixedDelay = 100)
 * public void scheduledPump() {
 *     runtime.pump();
 * }
 *
 * // Or via ExecutorService for more control
 * ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
 * executor.submit(() -> {
 *     while (running) {
 *         runtime.pump();
 *     }
 * });
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface Runtime {

    /**
     * Executes a single pump cycle: dequeue, process, acknowledge.
     *
     * <p>This method performs one iteration of the message queue processing loop:</p>
     * <ol>
     *   <li>Dequeue a batch of messages from the message queue</li>
     *   <li>Process each message concurrently (within concurrency limits)</li>
     *   <li>Handle outcomes according to their type (Ok, Retry, Fail)</li>
     *   <li>Acknowledge successfully processed messages</li>
     *   <li>Negative acknowledge messages requiring retry</li>
     * </ol>
     *
     * <p><strong>Processing Guarantees:</strong></p>
     * <ul>
     *   <li>At-least-once processing: Messages may be processed multiple times</li>
     *   <li>Idempotency: Operations should be idempotent to handle duplicates</li>
     *   <li>Durability: Results persisted via write-ahead log before acknowledgment</li>
     *   <li>Eventual consistency: Finalizer ensures all outcomes eventually finalize</li>
     * </ul>
     *
     * <p><strong>Concurrency Behavior:</strong></p>
     * <ul>
     *   <li>Batch dequeue is synchronized to prevent duplicate processing</li>
     *   <li>Individual message processing can occur concurrently</li>
     *   <li>Duplicate OpId detection prevents concurrent processing of same operation</li>
     *   <li>Visibility timeout on dequeued messages prevents other workers from processing</li>
     * </ul>
     *
     * <p><strong>Blocking Behavior:</strong></p>
     * <ul>
     *   <li>This method is typically <strong>non-blocking</strong> per cycle</li>
     *   <li>Returns after processing one batch or if queue is empty</li>
     *   <li>Caller is responsible for continuous invocation (loop or scheduler)</li>
     * </ul>
     *
     * <p><strong>Exception Handling:</strong></p>
     * <ul>
     *   <li>Thrown exceptions indicate critical failures (e.g., Bus unavailable)</li>
     *   <li>Per-message errors are caught internally and handled via Outcome</li>
     *   <li>Uncaught exceptions should trigger alerting and may require manual intervention</li>
     * </ul>
     *
     * <p><strong>Performance Considerations:</strong></p>
     * <ul>
     *   <li>Virtual threads (Java 21) recommended for high concurrency</li>
     *   <li>Batch size impacts latency vs throughput trade-off</li>
     *   <li>Polling interval affects CPU usage vs message processing delay</li>
     *   <li>Typical throughput: 100-1000 messages/second per instance</li>
     * </ul>
     *
     * @throws IllegalStateException if runtime is not properly initialized
     * @throws RuntimeException if critical infrastructure failure occurs (Bus, Store unavailable)
     */
    void pump();
}
