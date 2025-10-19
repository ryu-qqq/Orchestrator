/**
 * In-memory Store adapter implementation package.
 *
 * <p>This package provides reference implementations of the Store SPI
 * for testing and educational purposes.</p>
 *
 * <p><strong>Main Components:</strong></p>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.adapter.inmemory.store.InMemoryStore}:
 *       Thread-safe in-memory implementation of {@link com.ryuqq.orchestrator.core.spi.Store}</li>
 *   <li>{@link com.ryuqq.orchestrator.adapter.inmemory.store.InMemoryIdempotencyManager}:
 *       Thread-safe idempotency key management</li>
 * </ul>
 *
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li><strong>Concurrency:</strong> Uses {@link java.util.concurrent.ConcurrentHashMap}
 *       and {@link java.util.concurrent.CopyOnWriteArrayList} for thread-safe operations</li>
 *   <li><strong>Ordering:</strong> WAL entries maintain occurred_at based ordering</li>
 *   <li><strong>Idempotency:</strong> (Domain, EventType, BizKey, IdemKey) → OpId mapping</li>
 *   <li><strong>Transaction Simulation:</strong> ThreadLocal-based transaction context</li>
 * </ul>
 *
 * <p><strong>Limitations:</strong></p>
 * <ul>
 *   <li>No actual ACID transaction support</li>
 *   <li>Data lost on process restart</li>
 *   <li>Not suitable for production use</li>
 *   <li>Suitable for Contract Tests and reference implementation</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // Create SPI implementations
 * Store store = new InMemoryStore();
 * IdempotencyManager idempotencyManager = new InMemoryIdempotencyManager();
 *
 * // Use in Contract Tests
 * public class MyStoreContractTest extends AbstractContractTest {
 *     {@literal @}BeforeEach
 *     void setUp() {
 *         this.store = new InMemoryStore();
 *         this.idempotencyManager = new InMemoryIdempotencyManager();
 *     }
 * }
 * </pre>
 *
 * @see com.ryuqq.orchestrator.core.spi.Store
 * @see com.ryuqq.orchestrator.core.spi.IdempotencyManager
 * @author Orchestrator Team
 * @since 1.0.0
 */
package com.ryuqq.orchestrator.adapter.inmemory.store;
