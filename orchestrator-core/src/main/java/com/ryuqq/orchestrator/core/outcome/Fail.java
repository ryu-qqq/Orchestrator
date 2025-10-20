package com.ryuqq.orchestrator.core.outcome;

/**
 * 영구적 실패 (재시도 불가).
 *
 * <p>비즈니스 규칙 위반이나 복구 불가능한 오류로 인해 재시도해도 성공할 수 없는 경우를 나타냅니다.</p>
 *
 * <p><strong>예시:</strong></p>
 * <ul>
 *   <li>유효성 검증 실패 (잘못된 파라미터)</li>
 *   <li>권한 없음 (403 Forbidden)</li>
 *   <li>리소스 없음 (404 Not Found)</li>
 *   <li>비즈니스 규칙 위반 (잔액 부족, 재고 없음 등)</li>
 * </ul>
 *
 * @param errorCode 오류 코드 (예: PAY-001, FILE-404)
 * @param message 오류 메시지
 * @param cause 원인 (선택, null 가능)
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public record Fail(
    String errorCode,
    String message,
    String cause
) implements Outcome {

    /**
     * Compact Constructor.
     *
     * @throws IllegalArgumentException errorCode 또는 message가 null이거나 빈 문자열인 경우
     */
    public Fail {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode cannot be null or blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be null or blank");
        }
        // cause는 null 허용
    }

    /**
     * Fail 생성 (cause 포함).
     *
     * @param errorCode 오류 코드
     * @param message 오류 메시지
     * @param cause 원인
     * @return Fail 인스턴스
     * @throws IllegalArgumentException errorCode 또는 message가 null이거나 빈 문자열인 경우
     */
    public static Fail of(String errorCode, String message, String cause) {
        return new Fail(errorCode, message, cause);
    }

    /**
     * cause 없이 Fail 생성.
     *
     * @param errorCode 오류 코드
     * @param message 오류 메시지
     * @return Fail 인스턴스
     * @throws IllegalArgumentException errorCode 또는 message가 null이거나 빈 문자열인 경우
     */
    public static Fail of(String errorCode, String message) {
        return new Fail(errorCode, message, null);
    }
}
