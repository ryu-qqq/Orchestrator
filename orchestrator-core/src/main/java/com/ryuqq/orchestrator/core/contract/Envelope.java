package com.ryuqq.orchestrator.core.contract;

import com.ryuqq.orchestrator.core.model.OpId;

/**
 * Command 실행을 위한 봉투 (Envelope).
 *
 * <p>Envelope은 Command에 OpId와 실행 시각 등의 메타데이터를 추가한
 * 완전한 실행 컨텍스트입니다.</p>
 *
 * <p><strong>필드 구성:</strong></p>
 * <ul>
 *   <li><strong>opId:</strong> Operation 고유 식별자</li>
 *   <li><strong>command:</strong> 실행할 명령</li>
 *   <li><strong>acceptedAt:</strong> 요청 수락 시각 (epoch milliseconds)</li>
 * </ul>
 *
 * <p><strong>예시:</strong></p>
 * <pre>
 * // 현재 시각으로 생성
 * Envelope envelope = Envelope.now(opId, command);
 *
 * // 특정 시각 지정
 * Envelope envelope = new Envelope(opId, command, System.currentTimeMillis());
 * </pre>
 *
 * @param opId Operation 고유 식별자
 * @param command 실행할 명령
 * @param acceptedAt 요청 수락 시각 (epoch millis)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Envelope(
    OpId opId,
    Command command,
    long acceptedAt
) {

    /**
     * Compact Constructor.
     *
     * @throws IllegalArgumentException 필수 필드가 null이거나 acceptedAt이 음수인 경우
     */
    public Envelope {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        if (acceptedAt < 0) {
            throw new IllegalArgumentException("acceptedAt must be non-negative (current: " + acceptedAt + ")");
        }
    }

    /**
     * Envelope 생성 (명시적 시각 지정).
     *
     * @param opId Operation ID
     * @param command Command
     * @param acceptedAt 요청 수락 시각 (epoch milliseconds)
     * @return 생성된 Envelope
     * @throws IllegalArgumentException opId 또는 command가 null이거나 acceptedAt이 음수인 경우
     */
    public static Envelope of(OpId opId, Command command, long acceptedAt) {
        return new Envelope(opId, command, acceptedAt);
    }

    /**
     * 현재 시각으로 Envelope 생성.
     *
     * @param opId Operation ID
     * @param command Command
     * @return 생성된 Envelope
     * @throws IllegalArgumentException opId 또는 command가 null인 경우
     */
    public static Envelope now(OpId opId, Command command) {
        return new Envelope(opId, command, System.currentTimeMillis());
    }
}
