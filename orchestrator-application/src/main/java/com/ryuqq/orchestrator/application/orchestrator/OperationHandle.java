package com.ryuqq.orchestrator.application.orchestrator;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Outcome;

/**
 * Operation 실행 핸들.
 *
 * <p>Fast-Path 대기 결과를 표현하며, 동기/비동기 응답 전략을 결정합니다.</p>
 *
 * <p><strong>두 가지 가능한 상태:</strong></p>
 * <ul>
 *   <li><strong>Fast-Path 완료 (completedFast = true):</strong>
 *       <ul>
 *         <li>HTTP 200 OK 응답</li>
 *         <li>responseBodyOrNull: 실제 Outcome 결과 (Ok, Retry, Fail)</li>
 *         <li>statusUrlOrNull: null</li>
 *       </ul>
 *   </li>
 *   <li><strong>비동기 전환 (completedFast = false):</strong>
 *       <ul>
 *         <li>HTTP 202 Accepted 응답</li>
 *         <li>responseBodyOrNull: null</li>
 *         <li>statusUrlOrNull: 상태 조회 URL (예: /api/operations/{opId}/status)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p><strong>불변성:</strong> 생성 후 상태 변경 불가</p>
 *
 * <p><strong>사용 예시:</strong></p>
 * <pre>
 * // Fast-Path 완료
 * OperationHandle handle = OperationHandle.completed(opId, outcome);
 * if (handle.isCompletedFast()) {
 *     Outcome result = handle.getResponseBodyOrNull();
 *     // HTTP 200 + result
 * }
 *
 * // 비동기 전환
 * OperationHandle handle = OperationHandle.async(opId, "/api/operations/123/status");
 * if (!handle.isCompletedFast()) {
 *     String url = handle.getStatusUrlOrNull();
 *     // HTTP 202 + url
 * }
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class OperationHandle {

    private final OpId opId;
    private final boolean completedFast;
    private final Outcome responseBodyOrNull;
    private final String statusUrlOrNull;

    /**
     * Private constructor - 정적 팩토리 메서드 사용 권장.
     *
     * @param opId Operation ID
     * @param completedFast Fast-Path 완료 여부
     * @param responseBodyOrNull Fast-Path 완료 시 결과 (completedFast=true일 때만 non-null)
     * @param statusUrlOrNull 비동기 전환 시 상태 조회 URL (completedFast=false일 때만 non-null)
     * @throws IllegalArgumentException opId가 null인 경우
     */
    private OperationHandle(OpId opId, boolean completedFast,
                            Outcome responseBodyOrNull, String statusUrlOrNull) {
        if (opId == null) {
            throw new IllegalArgumentException("opId cannot be null");
        }
        this.opId = opId;
        this.completedFast = completedFast;
        this.responseBodyOrNull = responseBodyOrNull;
        this.statusUrlOrNull = statusUrlOrNull;
    }

    /**
     * Fast-Path 완료 핸들 생성.
     *
     * <p>timeBudget 내에 작업이 완료된 경우 사용합니다.
     * HTTP 200 OK 응답과 함께 실제 결과를 즉시 반환합니다.</p>
     *
     * @param opId Operation ID
     * @param outcome 실행 결과 (Ok, Retry, Fail)
     * @return OperationHandle (completedFast=true)
     * @throws IllegalArgumentException opId 또는 outcome이 null인 경우
     */
    public static OperationHandle completed(OpId opId, Outcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome cannot be null for completed handle");
        }
        return new OperationHandle(opId, true, outcome, null);
    }

    /**
     * 비동기 전환 핸들 생성.
     *
     * <p>timeBudget을 초과하여 작업이 아직 진행 중인 경우 사용합니다.
     * HTTP 202 Accepted 응답과 함께 상태 조회 URL을 반환합니다.</p>
     *
     * @param opId Operation ID
     * @param statusUrl 상태 조회 URL (예: /api/operations/{opId}/status)
     * @return OperationHandle (completedFast=false)
     * @throws IllegalArgumentException opId가 null이거나 statusUrl이 null/blank인 경우
     */
    public static OperationHandle async(OpId opId, String statusUrl) {
        if (statusUrl == null || statusUrl.isBlank()) {
            throw new IllegalArgumentException("statusUrl cannot be null or blank for async handle");
        }
        return new OperationHandle(opId, false, null, statusUrl);
    }

    /**
     * Operation ID 조회.
     *
     * @return Operation ID (non-null)
     */
    public OpId getOpId() {
        return opId;
    }

    /**
     * Fast-Path 완료 여부 확인.
     *
     * @return timeBudget 내 완료된 경우 true, 초과한 경우 false
     */
    public boolean isCompletedFast() {
        return completedFast;
    }

    /**
     * 응답 결과 조회 (Fast-Path 완료 시).
     *
     * <p><strong>주의:</strong> completedFast=true인 경우에만 non-null 반환</p>
     *
     * @return 실행 결과 (Ok, Retry, Fail) 또는 null (비동기 전환 시)
     */
    public Outcome getResponseBodyOrNull() {
        return responseBodyOrNull;
    }

    /**
     * 상태 조회 URL 조회 (비동기 전환 시).
     *
     * <p><strong>주의:</strong> completedFast=false인 경우에만 non-null 반환</p>
     *
     * @return 상태 조회 URL 또는 null (Fast-Path 완료 시)
     */
    public String getStatusUrlOrNull() {
        return statusUrlOrNull;
    }

    @Override
    public String toString() {
        if (completedFast) {
            return "OperationHandle{opId=" + opId + ", completed=true, outcome=" + responseBodyOrNull + "}";
        } else {
            return "OperationHandle{opId=" + opId + ", completed=false, statusUrl=" + statusUrlOrNull + "}";
        }
    }
}
