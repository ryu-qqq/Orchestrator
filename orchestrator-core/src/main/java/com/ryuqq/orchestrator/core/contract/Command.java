package com.ryuqq.orchestrator.core.contract;

import com.ryuqq.orchestrator.core.model.BizKey;
import com.ryuqq.orchestrator.core.model.Domain;
import com.ryuqq.orchestrator.core.model.EventType;
import com.ryuqq.orchestrator.core.model.IdemKey;
import com.ryuqq.orchestrator.core.model.Payload;

/**
 * Operation 실행 명령.
 *
 * <p>Command는 외부 API 호출을 포함한 업무 플로우를 실행하기 위한
 * 모든 필요 정보를 담고 있습니다.</p>
 *
 * <p><strong>필드 구성:</strong></p>
 * <ul>
 *   <li><strong>domain:</strong> 업무 도메인 (예: ORDER, PAYMENT)</li>
 *   <li><strong>eventType:</strong> 이벤트 유형 (예: CREATE, UPDATE)</li>
 *   <li><strong>bizKey:</strong> 업무 엔티티 식별자</li>
 *   <li><strong>idemKey:</strong> 멱등성 키 (클라이언트 제공)</li>
 *   <li><strong>payload:</strong> 업무 데이터 (직렬화된 형태, null 가능)</li>
 * </ul>
 *
 * <p><strong>예시:</strong></p>
 * <pre>
 * Command command = Command.of(
 *     Domain.of("ORDER"),
 *     EventType.of("CREATE"),
 *     BizKey.of("ORDER-123"),
 *     IdemKey.of("550e8400-e29b-41d4-a716-446655440000"),
 *     Payload.of("{\"amount\":50000}")
 * );
 * </pre>
 *
 * @param domain 업무 도메인
 * @param eventType 이벤트 유형
 * @param bizKey 업무 엔티티 식별자
 * @param idemKey 멱등성 키
 * @param payload 업무 데이터 (null 가능)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Command(
    Domain domain,
    EventType eventType,
    BizKey bizKey,
    IdemKey idemKey,
    Payload payload
) {

    /**
     * Compact Constructor.
     *
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public Command {
        if (domain == null) {
            throw new IllegalArgumentException("domain cannot be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType cannot be null");
        }
        if (bizKey == null) {
            throw new IllegalArgumentException("bizKey cannot be null");
        }
        if (idemKey == null) {
            throw new IllegalArgumentException("idemKey cannot be null");
        }
        // payload는 null 허용
    }

    /**
     * Command 인스턴스를 생성하는 static factory method.
     *
     * @param domain 업무 도메인
     * @param eventType 이벤트 유형
     * @param bizKey 업무 엔티티 식별자
     * @param idemKey 멱등성 키
     * @param payload 업무 데이터 (null 가능)
     * @return Command 인스턴스
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public static Command of(
        Domain domain,
        EventType eventType,
        BizKey bizKey,
        IdemKey idemKey,
        Payload payload
    ) {
        return new Command(domain, eventType, bizKey, idemKey, payload);
    }
}
