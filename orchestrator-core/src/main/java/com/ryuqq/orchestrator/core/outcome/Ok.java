package com.ryuqq.orchestrator.core.outcome;

import com.ryuqq.orchestrator.core.model.OpId;

/**
 * 성공 결과.
 *
 * <p>Operation이 성공적으로 완료되었음을 나타냅니다.</p>
 *
 * @param opId Operation ID
 * @param message 성공 메시지 (선택, null 가능)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Ok(
    OpId opId,
    String message
) implements Outcome {

    /**
     * Compact Constructor.
     *
     * @throws IllegalArgumentException opId가 null인 경우
     */
    public Ok {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        // message는 null 허용
    }

    /**
     * 메시지 없이 성공 결과 생성.
     *
     * @param opId Operation ID
     * @return Ok 인스턴스
     * @throws IllegalArgumentException opId가 null인 경우
     */
    public static Ok of(OpId opId) {
        return new Ok(opId, null);
    }
}
