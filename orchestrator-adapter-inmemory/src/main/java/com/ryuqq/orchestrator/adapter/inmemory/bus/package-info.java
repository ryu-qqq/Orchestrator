/**
 * In-memory Bus adapter providing thread-safe message queue operations for testing and reference.
 *
 * <p>This package contains the reference implementation of the {@link com.ryuqq.orchestrator.core.spi.Bus}
 * SPI interface using in-memory data structures. It demonstrates best practices for implementing
 * message queue semantics required by the Orchestrator framework.</p>
 *
 * <h2>Architecture</h2>
 *
 * <p>The In-memory Bus uses three core data structures:</p>
 * <ul>
 *   <li><strong>Main Queue:</strong> {@link java.util.concurrent.DelayQueue} for delayed message delivery</li>
 *   <li><strong>In-Flight Tracking:</strong> {@link java.util.concurrent.ConcurrentHashMap} for visibility timeout management</li>
 *   <li><strong>Dead Letter Queue:</strong> {@link java.util.concurrent.CopyOnWriteArrayList} for permanently failed messages</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 *
 * <ul>
 *   <li><strong>At-least-once Delivery:</strong> Messages may be delivered multiple times via visibility timeout</li>
 *   <li><strong>Delayed Delivery:</strong> Support for delayed message publishing (retry scenarios)</li>
 *   <li><strong>Visibility Timeout:</strong> Messages invisible to other consumers during processing</li>
 *   <li><strong>Dead Letter Queue:</strong> Permanent failure handling with metadata tracking</li>
 *   <li><strong>Thread Safety:</strong> All operations are thread-safe using concurrent collections</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create bus with default 30-second visibility timeout
 * Bus bus = new InMemoryBus();
 *
 * // Publish message with 5-second delay (retry scenario)
 * bus.publish(envelope, 5000);
 *
 * // Dequeue and process messages
 * List<Envelope> batch = bus.dequeue(10);
 * for (Envelope envelope : batch) {
 *     try {
 *         processEnvelope(envelope);
 *         bus.ack(envelope);  // Success: remove from queue
 *     } catch (TransientException e) {
 *         bus.nack(envelope);  // Temporary failure: retry
 *     } catch (PermanentException e) {
 *         bus.publishToDLQ(envelope, fail);  // Permanent failure: DLQ
 *     }
 * }
 * }</pre>
 *
 * <h2>Performance Characteristics</h2>
 *
 * <table border="1">
 *   <caption>Operation Complexity</caption>
 *   <tr>
 *     <th>Operation</th>
 *     <th>Time Complexity</th>
 *     <th>Notes</th>
 *   </tr>
 *   <tr>
 *     <td>publish()</td>
 *     <td>O(log N)</td>
 *     <td>DelayQueue insertion with ordering</td>
 *   </tr>
 *   <tr>
 *     <td>dequeue()</td>
 *     <td>O(M log N)</td>
 *     <td>M = batchSize, DelayQueue polling</td>
 *   </tr>
 *   <tr>
 *     <td>ack() / nack()</td>
 *     <td>O(1)</td>
 *     <td>ConcurrentHashMap operations</td>
 *   </tr>
 *   <tr>
 *     <td>publishToDLQ()</td>
 *     <td>O(1)</td>
 *     <td>CopyOnWriteArrayList append</td>
 *   </tr>
 * </table>
 *
 * <h2>Concurrency Model</h2>
 *
 * <p>All operations are thread-safe:</p>
 * <ul>
 *   <li><strong>DelayQueue:</strong> Thread-safe priority queue with blocking operations</li>
 *   <li><strong>ConcurrentHashMap:</strong> Lock-free concurrent access for in-flight tracking</li>
 *   <li><strong>CopyOnWriteArrayList:</strong> Snapshot isolation for DLQ iteration</li>
 * </ul>
 *
 * <h2>Message Lifecycle</h2>
 *
 * <pre>
 * ┌─────────────┐
 * │   publish   │ (with optional delay)
 * └──────┬──────┘
 *        │
 *        ▼
 * ┌─────────────┐
 * │ DelayQueue  │ (wait for delay expiration)
 * └──────┬──────┘
 *        │
 *        ▼
 * ┌─────────────┐
 * │   dequeue   │ → Mark as in-flight (visibility timeout starts)
 * └──────┬──────┘
 *        │
 *        ├──► ack() ────────────────────────────────► [Permanently Removed]
 *        │
 *        ├──► nack() ───────────────────────────────► [Re-queued for Retry]
 *        │
 *        ├──► publishToDLQ() ────────────────────────► [Dead Letter Queue]
 *        │
 *        └──► Visibility Timeout Expired ───────────► [Re-queued Automatically]
 * </pre>
 *
 * <h2>Testing Support</h2>
 *
 * <p>The implementation provides additional methods for testing:</p>
 * <ul>
 *   <li>{@code processVisibilityTimeouts()}: Manually trigger visibility timeout processing</li>
 *   <li>{@code expireVisibilityTimeout(Envelope)}: Force specific message timeout</li>
 *   <li>{@code clear()}: Reset all queues for test cleanup</li>
 *   <li>{@code queueSize()}, {@code inFlightSize()}, {@code dlqSize()}: Inspection methods</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 *
 * <ul>
 *   <li><strong>In-Memory Only:</strong> Data lost on process restart</li>
 *   <li><strong>Single JVM:</strong> No distributed message queue support</li>
 *   <li><strong>No Persistence:</strong> Not suitable for production use</li>
 *   <li><strong>Manual Timeout Processing:</strong> Requires explicit {@code processVisibilityTimeouts()} calls</li>
 * </ul>
 *
 * <h2>Integration with Orchestrator</h2>
 *
 * <p>This Bus implementation is used by:</p>
 * <ul>
 *   <li><strong>QueueWorkerRunner:</strong> Dequeues and processes pending operations</li>
 *   <li><strong>Finalizer:</strong> Publishes final outcomes to queue</li>
 *   <li><strong>Reaper:</strong> Handles long-running operation timeouts and retries</li>
 * </ul>
 *
 * @see com.ryuqq.orchestrator.core.spi.Bus
 * @see com.ryuqq.orchestrator.adapter.inmemory.bus.InMemoryBus
 * @author Orchestrator Team
 * @since 1.0.0
 */
package com.ryuqq.orchestrator.adapter.inmemory.bus;
