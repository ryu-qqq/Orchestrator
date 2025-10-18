package com.ryuqq.orchestrator.core.statemachine;

/**
 * 상태 전이 검증 및 실행.
 *
 * <p>이 클래스는 Operation의 상태 전이가 허용된 규칙을 따르는지
 * 검증하고, 불변식을 보장합니다.</p>
 *
 * <p><strong>허용되는 전이:</strong></p>
 * <ul>
 *   <li>PENDING → IN_PROGRESS</li>
 *   <li>IN_PROGRESS → COMPLETED</li>
 *   <li>IN_PROGRESS → FAILED</li>
 * </ul>
 *
 * <p><strong>불변식:</strong></p>
 * <ul>
 *   <li>종료 상태(COMPLETED, FAILED)에서는 어떤 상태로도 전이 불가</li>
 *   <li>역방향 전이 불가 (예: COMPLETED → IN_PROGRESS)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class StateTransition {

    // Utility class - prevent instantiation
    private StateTransition() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 상태 전이가 유효한지 검증.
     *
     * <p>허용되지 않은 전이를 시도하면 {@link IllegalStateException}을 발생시킵니다.</p>
     *
     * @param from 현재 상태
     * @param to 전이할 상태
     * @throws IllegalArgumentException from 또는 to가 null인 경우
     * @throws IllegalStateException 유효하지 않은 전이인 경우
     */
    public static void validate(OperationState from, OperationState to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("States cannot be null (from: " + from + ", to: " + to + ")");
        }

        // 종료 상태에서는 어디로도 전이 불가
        if (from.isTerminal()) {
            throw new IllegalStateException(
                String.format("Cannot transition from terminal state: %s → %s", from, to)
            );
        }

        // 허용된 전이만 통과
        boolean valid = switch (from) {
            case PENDING -> to == OperationState.IN_PROGRESS;
            case IN_PROGRESS -> to == OperationState.COMPLETED || to == OperationState.FAILED;
            case COMPLETED, FAILED -> false; // 종료 상태 (위에서 이미 체크했지만 명시적 표현)
        };

        if (!valid) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s → %s", from, to)
            );
        }
    }

    /**
     * 상태 전이 실행 (검증 후).
     *
     * <p>검증을 통과한 경우에만 새로운 상태를 반환합니다.</p>
     *
     * @param current 현재 상태
     * @param next 다음 상태
     * @return 전이된 상태 (next)
     * @throws IllegalArgumentException current 또는 next가 null인 경우
     * @throws IllegalStateException 유효하지 않은 전이인 경우
     */
    public static OperationState transition(OperationState current, OperationState next) {
        validate(current, next);
        return next;
    }
}
