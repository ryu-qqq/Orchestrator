package com.ryuqq.orchestrator.core.spi;

import com.ryuqq.orchestrator.core.model.IdempotencyKey;
import com.ryuqq.orchestrator.core.model.OpId;

/**
 * 멱등성 관리 SPI (Service Provider Interface).
 *
 * <p>동일한 {@link IdempotencyKey}는 항상 동일한 {@link OpId}로 매핑되어야 합니다.</p>
 *
 * <p><strong>구현 책임:</strong></p>
 * <ul>
 *   <li>IdempotencyKey → OpId 매핑 저장소 관리</li>
 *   <li>동시성 제어 (동일 키로 동시 요청 시 하나의 OpId만 생성)</li>
 *   <li>OpId 생성 전략 (UUID, Snowflake ID 등)</li>
 * </ul>
 *
 * <p><strong>동시성 제어 권장 방안:</strong></p>
 * <ul>
 *   <li>Database Unique Constraint: IdempotencyKey를 Unique Key로 설정</li>
 *   <li>Optimistic Locking: Version 필드로 동시성 제어</li>
 *   <li>Distributed Lock: Redis, Zookeeper 등으로 분산 락 구현</li>
 * </ul>
 *
 * <p><strong>구현 예시:</strong></p>
 * <pre>
 * // InMemory 구현 (테스트용)
 * public class InMemoryIdempotencyManager implements IdempotencyManager {
 *     private final ConcurrentHashMap&lt;IdempotencyKey, OpId&gt; store = new ConcurrentHashMap&lt;&gt;();
 *
 *     {@literal @}Override
 *     public OpId getOrCreate(IdempotencyKey key) {
 *         return store.computeIfAbsent(key, k -&gt; OpId.of(UUID.randomUUID().toString()));
 *     }
 *
 *     {@literal @}Override
 *     public OpId find(IdempotencyKey key) {
 *         return store.get(key);
 *     }
 * }
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface IdempotencyManager {

    /**
     * IdempotencyKey에 대응하는 OpId를 조회하거나 생성.
     *
     * <p>이미 존재하는 경우 기존 OpId를 반환하고,
     * 없는 경우 새로운 OpId를 생성하여 저장 후 반환합니다.</p>
     *
     * <p><strong>동시성 보장:</strong></p>
     * <p>동일한 IdempotencyKey로 동시에 여러 요청이 들어와도
     * 하나의 OpId만 생성되어야 합니다.</p>
     *
     * @param key 멱등성 키
     * @return OpId (기존 또는 신규)
     * @throws IllegalArgumentException key가 null인 경우
     */
    OpId getOrCreate(IdempotencyKey key);

    /**
     * IdempotencyKey에 대응하는 OpId 조회 (조회만).
     *
     * <p>존재하지 않는 경우 null을 반환하며, 새로 생성하지 않습니다.</p>
     *
     * @param key 멱등성 키
     * @return OpId (존재하는 경우), null (없는 경우)
     * @throws IllegalArgumentException key가 null인 경우
     */
    OpId find(IdempotencyKey key);
}
