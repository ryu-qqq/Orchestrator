package com.ryuqq.orchestrator.adapter.inmemory.store;

import com.ryuqq.orchestrator.core.model.IdempotencyKey;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.spi.IdempotencyManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link IdempotencyManager} for testing and reference purposes.
 *
 * <p>This implementation provides thread-safe idempotency key management
 * using {@link ConcurrentHashMap#computeIfAbsent} for atomic get-or-create operations.</p>
 *
 * <p><strong>Idempotency Guarantee:</strong></p>
 * <ul>
 *   <li>Same {@link IdempotencyKey} always maps to the same {@link OpId}</li>
 *   <li>Concurrent requests with the same key produce only one {@link OpId}</li>
 *   <li>Duplicate requests return existing {@link OpId}</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <ul>
 *   <li>{@link ConcurrentHashMap#computeIfAbsent} ensures atomic operations</li>
 *   <li>No manual locking required</li>
 *   <li>Safe for concurrent access from multiple threads</li>
 * </ul>
 *
 * <p><strong>OpId Generation Strategy:</strong></p>
 * <ul>
 *   <li>Uses {@link UUID#randomUUID()} for unique OpId generation</li>
 *   <li>Generated OpIds are globally unique</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * IdempotencyManager manager = new InMemoryIdempotencyManager();
 *
 * IdempotencyKey key = new IdempotencyKey(
 *     Domain.of("ORDER"),
 *     EventType.of("CREATE"),
 *     BizKey.of("123"),
 *     IdemKey.of("idem-abc")
 * );
 *
 * // First call: creates new OpId
 * OpId opId1 = manager.getOrCreate(key);
 *
 * // Second call with same key: returns existing OpId
 * OpId opId2 = manager.getOrCreate(key);
 *
 * assert opId1.equals(opId2); // true
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class InMemoryIdempotencyManager implements IdempotencyManager {

    /**
     * IdempotencyKey → OpId mapping storage.
     * Key: IdempotencyKey, Value: OpId
     */
    private final ConcurrentHashMap<IdempotencyKey, OpId> store;

    /**
     * Creates a new InMemoryIdempotencyManager with empty storage.
     */
    public InMemoryIdempotencyManager() {
        this.store = new ConcurrentHashMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Uses {@link ConcurrentHashMap#computeIfAbsent} for atomic get-or-create</li>
     *   <li>Generates new OpId using {@link UUID#randomUUID()}</li>
     *   <li>Thread-safe: multiple concurrent calls with same key produce one OpId</li>
     * </ul>
     *
     * @param key the idempotency key
     * @return OpId (existing or newly created)
     * @throws IllegalArgumentException if key is null
     */
    @Override
    public OpId getOrCreate(IdempotencyKey key) {
        if (key == null) {
            throw new IllegalArgumentException("IdempotencyKey cannot be null");
        }

        return store.computeIfAbsent(key, k -> OpId.of(UUID.randomUUID().toString()));
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *   <li>Simple read-only operation</li>
     *   <li>Returns null if key not found (does not create new OpId)</li>
     * </ul>
     *
     * @param key the idempotency key
     * @return OpId if exists, null otherwise
     * @throws IllegalArgumentException if key is null
     */
    @Override
    public OpId find(IdempotencyKey key) {
        if (key == null) {
            throw new IllegalArgumentException("IdempotencyKey cannot be null");
        }

        return store.get(key);
    }

    /**
     * Clears all stored IdempotencyKey → OpId mappings.
     *
     * <p>This method is used for test cleanup.</p>
     */
    public void clear() {
        store.clear();
    }

    /**
     * Returns the number of stored IdempotencyKey → OpId mappings.
     *
     * <p>This method is useful for testing and debugging.</p>
     *
     * @return the number of mappings
     */
    public int size() {
        return store.size();
    }
}
