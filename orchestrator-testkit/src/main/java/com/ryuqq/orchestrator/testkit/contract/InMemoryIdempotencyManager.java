package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.model.IdempotencyKey;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.spi.IdempotencyManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of IdempotencyManager for testing purposes.
 *
 * <p>This implementation provides thread-safe idempotency key management
 * using ConcurrentHashMap for concurrent access scenarios.</p>
 *
 * <p><strong>Concurrency Guarantee:</strong></p>
 * <ul>
 *   <li>Multiple threads calling getOrCreate with the same key will get the same OpId</li>
 *   <li>Uses ConcurrentHashMap.computeIfAbsent for atomic get-or-create</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class InMemoryIdempotencyManager implements IdempotencyManager {

    private final ConcurrentHashMap<IdempotencyKey, OpId> mappings;

    /**
     * Creates a new InMemoryIdempotencyManager with empty mappings.
     */
    public InMemoryIdempotencyManager() {
        this.mappings = new ConcurrentHashMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates new OpId using UUID if not exists.</p>
     */
    @Override
    public OpId getOrCreate(IdempotencyKey key) {
        if (key == null) {
            throw new IllegalArgumentException("IdempotencyKey cannot be null");
        }

        return mappings.computeIfAbsent(key, k -> OpId.of(UUID.randomUUID().toString()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OpId find(IdempotencyKey key) {
        if (key == null) {
            throw new IllegalArgumentException("IdempotencyKey cannot be null");
        }

        return mappings.get(key);
    }

    /**
     * Clears all mappings. Used for test cleanup.
     */
    public void clear() {
        mappings.clear();
    }

    /**
     * Returns the number of stored mappings. Used for test assertions.
     *
     * @return number of idempotency key mappings
     */
    public int size() {
        return mappings.size();
    }
}
