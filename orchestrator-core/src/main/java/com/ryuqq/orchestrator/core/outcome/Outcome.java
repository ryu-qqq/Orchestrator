package com.ryuqq.orchestrator.core.outcome;

/**
 * Operation 실행 결과.
 *
 * <p>Outcome은 세 가지 가능한 결과를 나타냅니다:</p>
 * <ul>
 *   <li>{@link Ok}: 성공적으로 완료됨</li>
 *   <li>{@link Retry}: 일시적 실패, 재시도 가능</li>
 *   <li>{@link Fail}: 영구적 실패, 재시도 불가</li>
 * </ul>
 *
 * <p>Sealed interface로 정의되어 모든 케이스를 컴파일 타임에 검증합니다.</p>
 *
 * <p><strong>Pattern Matching 예시:</strong></p>
 * <pre>
 * String result = switch (outcome) {
 *     case Ok ok -> "Success: " + ok.message();
 *     case Retry retry -> "Retry after " + retry.nextRetryAfterMillis() + "ms";
 *     case Fail fail -> "Failed: " + fail.errorCode();
 *     // 컴파일러가 모든 케이스 처리 강제 (default 불필요)
 * };
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public sealed interface Outcome permits Ok, Retry, Fail {

    /**
     * 결과가 성공인지 확인.
     *
     * @return 성공 여부
     */
    default boolean isOk() {
        return this instanceof Ok;
    }

    /**
     * 결과가 재시도 가능한지 확인.
     *
     * @return 재시도 가능 여부
     */
    default boolean isRetry() {
        return this instanceof Retry;
    }

    /**
     * 결과가 영구 실패인지 확인.
     *
     * @return 영구 실패 여부
     */
    default boolean isFail() {
        return this instanceof Fail;
    }
}
