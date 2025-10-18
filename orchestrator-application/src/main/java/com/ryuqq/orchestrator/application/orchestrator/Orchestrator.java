package com.ryuqq.orchestrator.application.orchestrator;

import com.ryuqq.orchestrator.core.contract.Command;

/**
 * Operation 실행 조정자.
 *
 * <p>클라이언트 요청을 수락하고, timeBudget 기반 동기/비동기 분기를 수행합니다.</p>
 *
 * <p><strong>사용 예시:</strong></p>
 * <pre>
 * Command command = new Command(domain, eventType, bizKey, idemKey, payload);
 * OperationHandle handle = orchestrator.submit(command, 200);
 *
 * if (handle.isCompletedFast()) {
 *     // 동기 완료: 200 OK + outcome
 *     Outcome outcome = handle.getResponseBodyOrNull();
 * } else {
 *     // 비동기 전환: 202 Accepted + statusUrl
 *     String statusUrl = handle.getStatusUrlOrNull();
 * }
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface Orchestrator {

    /**
     * Command를 제출하고 Fast-Path 대기.
     *
     * <p>설정된 timeBudget 내에 작업이 완료되면 즉시 결과를 반환하고,
     * 시간 내 완료되지 않으면 비동기 전환하여 상태 조회 URL을 반환합니다.</p>
     *
     * <p><strong>동작 방식:</strong></p>
     * <ol>
     *   <li>OpId 생성 (UUID 기반)</li>
     *   <li>Envelope 생성 (OpId + Command + acceptedAt)</li>
     *   <li>Executor에게 실행 위임 (비동기 시작)</li>
     *   <li>timeBudget 동안 소프트 폴링 (10ms 간격)</li>
     *   <li>완료 시 → OperationHandle(completedFast=true, outcome)</li>
     *   <li>타임아웃 시 → OperationHandle(completedFast=false, statusUrl)</li>
     * </ol>
     *
     * @param command 실행할 명령
     * @param timeBudgetMs Fast-Path 대기 시간 (밀리초)
     * @return OperationHandle (완료 여부 및 결과)
     * @throws IllegalArgumentException command가 null이거나 유효하지 않은 경우
     * @throws IllegalArgumentException timeBudgetMs가 허용 범위를 벗어난 경우 (50ms ~ 5000ms)
     */
    OperationHandle submit(Command command, long timeBudgetMs);
}
