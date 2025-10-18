package com.ryuqq.orchestrator.adapter.runner;

/**
 * Reaper 리컨실 전략.
 *
 * <p>장기 IN_PROGRESS 상태의 작업을 어떻게 처리할지 결정하는 전략입니다.</p>
 *
 * <p><strong>전략별 사용 시나리오:</strong></p>
 * <ul>
 *   <li>RETRY: 네트워크 일시 장애, 타임아웃으로 인한 응답 미수신 등 재실행 가능한 경우</li>
 *   <li>FAIL: 영구 실패로 판단되거나 재실행이 위험한 경우 (예: 중복 처리 위험)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public enum ReconcileStrategy {

    /**
     * 재실행 전략.
     *
     * <p>Envelope을 큐에 재게시하여 다시 처리합니다.</p>
     *
     * <p><strong>장점:</strong></p>
     * <ul>
     *   <li>일시적 장애 복구 가능</li>
     *   <li>데이터 유실 방지</li>
     * </ul>
     *
     * <p><strong>위험:</strong></p>
     * <ul>
     *   <li>멱등성이 보장되지 않으면 중복 처리 발생</li>
     *   <li>영구 실패 케이스에서 무한 재시도 가능</li>
     * </ul>
     */
    RETRY,

    /**
     * 실패 처리 전략.
     *
     * <p>작업을 실패로 종결하고 DLQ로 전송합니다.</p>
     *
     * <p><strong>장점:</strong></p>
     * <ul>
     *   <li>중복 처리 위험 없음</li>
     *   <li>무한 재시도 방지</li>
     *   <li>수동 조사를 위한 DLQ 보관</li>
     * </ul>
     *
     * <p><strong>주의:</strong></p>
     * <ul>
     *   <li>복구 가능한 케이스도 실패 처리될 수 있음</li>
     *   <li>수동 개입 필요</li>
     * </ul>
     */
    FAIL
}
