package com.ryuqq.orchestrator.core.protection;

import com.ryuqq.orchestrator.core.model.OpId;

/**
 * Bulkhead SPI.
 *
 * <p>동시 실행 수를 제한하여 특정 작업이 전체 시스템 리소스를 독점하지 못하도록 격리합니다.</p>
 *
 * <p><strong>Bulkhead 패턴:</strong></p>
 * <ul>
 *   <li>Semaphore-based: Semaphore로 동시 실행 수 제한</li>
 *   <li>Thread Pool-based: 전용 스레드 풀로 격리 (향후 확장)</li>
 * </ul>
 *
 * <p><strong>사용 예시:</strong></p>
 * <pre>{@code
 * Bulkhead bulkhead = ...;
 * OpId opId = OpId.of("external-api-call");
 *
 * if (!bulkhead.tryAcquire(opId)) {
 *     // Bulkhead 진입 실패
 *     return new Fail("BULKHEAD-FULL", "Bulkhead is full");
 * }
 *
 * try {
 *     // 작업 실행
 *     Result result = externalApi.call();
 *     return result;
 * } finally {
 *     bulkhead.release(opId);
 * }
 * }</pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public interface Bulkhead {

    /**
     * Bulkhead 진입 허용 여부 확인 (비블로킹).
     *
     * <p>현재 동시 실행 수가 제한 이하인지 확인합니다.
     * 허용되지 않으면 false를 반환하며, 대기하지 않습니다.</p>
     *
     * @param opId Operation ID (통계 및 로깅용)
     * @return true: 진입 허용, false: 동시 실행 제한 초과
     */
    boolean tryAcquire(OpId opId);

    /**
     * Bulkhead 진입 허용 여부 확인 (타임아웃 대기).
     *
     * <p>지정된 시간 동안 대기하여 Bulkhead 진입을 시도합니다.
     * 대기 시간 내에 허용되면 true, 타임아웃 시 false를 반환합니다.</p>
     *
     * @param opId Operation ID
     * @param timeoutMs 최대 대기 시간 (밀리초)
     * @return true: 진입 허용, false: 타임아웃 또는 인터럽트
     * @throws InterruptedException 대기 중 인터럽트 발생
     */
    boolean tryAcquire(OpId opId, long timeoutMs) throws InterruptedException;

    /**
     * Bulkhead 진입 해제.
     *
     * <p>작업 완료 후 Bulkhead를 해제하여 다른 작업이 진입할 수 있도록 합니다.
     * 반드시 try-finally 블록에서 호출되어야 합니다.</p>
     *
     * @param opId Operation ID
     */
    void release(OpId opId);

    /**
     * 현재 동시 실행 수 조회.
     *
     * @return 현재 진입 중인 작업 수
     */
    int getCurrentConcurrency();

    /**
     * Bulkhead 설정 정보 조회.
     *
     * @return Bulkhead 설정 (최대 동시 실행 수 등)
     */
    BulkheadConfig getConfig();
}
