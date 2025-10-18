package com.ryuqq.orchestrator.core.model;

import com.ryuqq.orchestrator.core.contract.Command;

/**
 * 멱등성 복합 키.
 *
 * <p>IdempotencyKey는 (Domain, EventType, BizKey, IdemKey) 조합으로 구성되며,
 * 동일한 복합 키는 항상 동일한 OpId와 매핑됩니다.</p>
 *
 * <p><strong>비즈니스 의미:</strong></p>
 * <ul>
 *   <li><strong>Domain:</strong> 업무 영역 분리 (ORDER vs PAYMENT)</li>
 *   <li><strong>EventType:</strong> 작업 유형 분리 (CREATE vs UPDATE)</li>
 *   <li><strong>BizKey:</strong> 엔티티 식별 (주문번호 123)</li>
 *   <li><strong>IdemKey:</strong> 클라이언트 요청 구분 (UUID)</li>
 * </ul>
 *
 * <p><strong>예시:</strong></p>
 * <pre>
 * IdempotencyKey key = new IdempotencyKey(
 *     Domain.of("ORDER"),
 *     EventType.of("CREATE"),
 *     BizKey.of("123"),
 *     IdemKey.of("550e8400-e29b-41d4-a716-446655440000")
 * );
 * </pre>
 *
 * @param domain 도메인
 * @param eventType 이벤트 타입
 * @param bizKey 비즈니스 키
 * @param idemKey 멱등성 키
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record IdempotencyKey(
    Domain domain,
    EventType eventType,
    BizKey bizKey,
    IdemKey idemKey
) {

    /**
     * Compact Constructor.
     *
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public IdempotencyKey {
        if (domain == null || eventType == null || bizKey == null || idemKey == null) {
            throw new IllegalArgumentException("All fields are required for IdempotencyKey");
        }
    }

    /**
     * Command에서 IdempotencyKey 추출.
     *
     * @param command Command
     * @return IdempotencyKey
     * @throws IllegalArgumentException command가 null인 경우
     */
    public static IdempotencyKey from(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        return new IdempotencyKey(
            command.domain(),
            command.eventType(),
            command.bizKey(),
            command.idemKey()
        );
    }
}
