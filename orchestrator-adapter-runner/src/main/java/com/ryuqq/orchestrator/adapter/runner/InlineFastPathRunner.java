package com.ryuqq.orchestrator.adapter.runner;

import com.ryuqq.orchestrator.application.orchestrator.Orchestrator;
import com.ryuqq.orchestrator.application.orchestrator.OperationHandle;
import com.ryuqq.orchestrator.core.contract.Command;
import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.executor.Executor;
import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.statemachine.OperationState;

/**
 * Inline Fast-Path Runner 구현체.
 *
 * <p>timeBudget 기반 동기/비동기 분기를 수행합니다.</p>
 *
 * <p><strong>동작 방식:</strong></p>
 * <ol>
 *   <li>OpId 생성 (UUID 기반)</li>
 *   <li>Envelope 생성 (OpId + Command + acceptedAt)</li>
 *   <li>Executor에게 실행 시작 요청 (비블로킹)</li>
 *   <li>timeBudget 동안 소프트 폴링 (10ms 간격)</li>
 *   <li>완료 시: OperationHandle(completedFast=true, outcome)</li>
 *   <li>타임아웃 시: OperationHandle(completedFast=false, statusUrl)</li>
 * </ol>
 *
 * <p><strong>성능 특성:</strong></p>
 * <ul>
 *   <li>Stateless 설계: 인스턴스 간 상태 공유 없음 (thread-safe)</li>
 *   <li>폴링 오버헤드: 최대 timeBudget / pollingIntervalMs 회</li>
 *   <li>메모리 효율: OperationHandle만 생성, 추가 할당 없음</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class InlineFastPathRunner implements Orchestrator {

    private static final long MIN_TIME_BUDGET_MS = 50;
    private static final long MAX_TIME_BUDGET_MS = 5000;
    private static final long DEFAULT_POLLING_INTERVAL_MS = 10;

    private final Executor executor;
    private final long pollingIntervalMs;

    /**
     * 생성자 (기본 폴링 간격 10ms).
     *
     * @param executor 작업 실행자
     * @throws IllegalArgumentException executor가 null인 경우
     */
    public InlineFastPathRunner(Executor executor) {
        this(executor, DEFAULT_POLLING_INTERVAL_MS);
    }

    /**
     * 생성자 (폴링 간격 커스터마이징).
     *
     * @param executor 작업 실행자
     * @param pollingIntervalMs 폴링 간격 (밀리초)
     * @throws IllegalArgumentException executor가 null이거나 pollingIntervalMs가 양수가 아닌 경우
     */
    public InlineFastPathRunner(Executor executor, long pollingIntervalMs) {
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        if (pollingIntervalMs <= 0) {
            throw new IllegalArgumentException("pollingIntervalMs must be positive (current: " + pollingIntervalMs + ")");
        }
        this.executor = executor;
        this.pollingIntervalMs = pollingIntervalMs;
    }

    @Override
    public OperationHandle submit(Command command, long timeBudgetMs) {
        validateInput(command, timeBudgetMs);

        // 1. OpId 생성 (UUID 기반)
        OpId opId = OpId.of(generateOpIdValue());

        // 2. Envelope 생성 (현재 시각)
        Envelope envelope = Envelope.now(opId, command);

        // 3. Executor에게 실행 시작 요청 (비블로킹)
        executor.execute(envelope);

        // 4. Fast-Path 폴링 (timeBudget 동안 대기)
        return pollForCompletion(opId, timeBudgetMs);
    }

    /**
     * 입력 유효성 검증.
     *
     * @param command Command
     * @param timeBudgetMs timeBudget (밀리초)
     * @throws IllegalArgumentException 유효하지 않은 입력인 경우
     */
    private void validateInput(Command command, long timeBudgetMs) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        if (timeBudgetMs < MIN_TIME_BUDGET_MS || timeBudgetMs > MAX_TIME_BUDGET_MS) {
            throw new IllegalArgumentException(
                String.format("timeBudgetMs must be between %d and %d ms (current: %d)",
                    MIN_TIME_BUDGET_MS, MAX_TIME_BUDGET_MS, timeBudgetMs));
        }
    }

    /**
     * Fast-Path 폴링 (소프트 폴링 메커니즘).
     *
     * <p>timeBudget 동안 주기적으로 Operation 상태를 체크하여
     * 완료 시 즉시 결과를 반환합니다.</p>
     *
     * <p><strong>알고리즘:</strong></p>
     * <ol>
     *   <li>시작 시각 기록</li>
     *   <li>while (경과 시간 < timeBudget):</li>
     *   <li>  - getState() 호출</li>
     *   <li>  - isTerminal() 체크</li>
     *   <li>  - 완료 시: getOutcome() → OperationHandle.completed()</li>
     *   <li>  - 미완료 시: sleep(pollingIntervalMs)</li>
     *   <li>timeBudget 초과 시: OperationHandle.async()</li>
     * </ol>
     *
     * @param opId Operation ID
     * @param timeBudgetMs timeBudget (밀리초)
     * @return OperationHandle (완료 또는 비동기 전환)
     */
    private OperationHandle pollForCompletion(OpId opId, long timeBudgetMs) {
        long startTimeNanos = System.nanoTime();
        long timeBudgetNanos = timeBudgetMs * 1_000_000L; // ms를 ns로 변환

        while (System.nanoTime() - startTimeNanos < timeBudgetNanos) {
            OperationState state = executor.getState(opId);

            if (state.isTerminal()) {
                // Fast-Path 완료: 200 OK + outcome
                Outcome outcome = executor.getOutcome(opId);
                return OperationHandle.completed(opId, outcome);
            }

            // 아직 진행 중: sleep 후 계속 폴링
            sleep(pollingIntervalMs);
        }

        // timeBudget 초과: 202 Accepted + statusUrl
        String statusUrl = buildStatusUrl(opId);
        return OperationHandle.async(opId, statusUrl);
    }

    /**
     * OpId 값 생성 (UUID 기반).
     *
     * @return UUID 문자열
     */
    private String generateOpIdValue() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * 상태 조회 URL 생성.
     *
     * @param opId Operation ID
     * @return 상태 조회 URL (예: /api/operations/{opId}/status)
     */
    private String buildStatusUrl(OpId opId) {
        return "/api/operations/" + opId.getValue() + "/status";
    }

    /**
     * Sleep (폴링 간격 대기).
     *
     * <p>InterruptedException 발생 시 현재 스레드의 인터럽트 플래그를 복원하고
     * RuntimeException으로 래핑하여 던집니다.</p>
     *
     * <p><strong>성능 고려사항:</strong></p>
     * <ul>
     *   <li>현재 구현은 플랫폼 스레드를 블로킹하므로, 고부하 환경에서 스레드 리소스 고갈 가능</li>
     *   <li>향후 개선 방향: Java 21 Virtual Thread 활용 또는 이벤트 기반 대기 메커니즘</li>
     *   <li>폴링 간격(기본 10ms)은 CPU 사용량과 반응성 간의 트레이드오프</li>
     * </ul>
     *
     * @param millis 대기 시간 (밀리초)
     * @throws RuntimeException sleep 중 인터럽트 발생 시
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Polling interrupted", e);
        }
    }
}
