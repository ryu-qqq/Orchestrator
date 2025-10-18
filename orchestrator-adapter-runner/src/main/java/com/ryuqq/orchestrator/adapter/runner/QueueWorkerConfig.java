package com.ryuqq.orchestrator.adapter.runner;

/**
 * QueueWorkerRunner 설정.
 *
 * <p>이 클래스는 QueueWorkerRunner의 동작을 제어하는 설정값을 담고 있습니다.</p>
 *
 * <p><strong>설정 항목:</strong></p>
 * <ul>
 *   <li>pollingIntervalMs: 큐 폴링 간격 (기본 100ms)</li>
 *   <li>batchSize: 한 번에 dequeue할 메시지 수 (기본 10)</li>
 *   <li>concurrency: 동시 처리 스레드 수 (기본 5)</li>
 *   <li>maxProcessingTimeMs: 최대 처리 시간 (기본 30000ms = 30초)</li>
 *   <li>maxRetries: 최대 재시도 횟수 (기본 3)</li>
 *   <li>dlqEnabled: DLQ 전송 활성화 여부 (기본 true)</li>
 * </ul>
 *
 * <p><strong>성능 튜닝 가이드:</strong></p>
 * <ul>
 *   <li>높은 처리량: batchSize 증가 (10 → 50), concurrency 증가 (5 → 20)</li>
 *   <li>낮은 지연: pollingIntervalMs 감소 (100 → 10), batchSize 감소 (10 → 1)</li>
 *   <li>자원 절약: concurrency 감소, pollingIntervalMs 증가</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class QueueWorkerConfig {

    private long pollingIntervalMs = 100;
    private int batchSize = 10;
    private int concurrency = 5;
    private long maxProcessingTimeMs = 30000;
    private int maxRetries = 3;
    private boolean dlqEnabled = true;

    public QueueWorkerConfig() {
    }

    /**
     * 큐 폴링 간격 조회.
     *
     * @return 폴링 간격 (밀리초)
     */
    public long getPollingIntervalMs() {
        return pollingIntervalMs;
    }

    /**
     * 큐 폴링 간격 설정.
     *
     * @param pollingIntervalMs 폴링 간격 (밀리초, 양수여야 함)
     * @throws IllegalArgumentException pollingIntervalMs가 양수가 아닌 경우
     */
    public void setPollingIntervalMs(long pollingIntervalMs) {
        if (pollingIntervalMs <= 0) {
            throw new IllegalArgumentException(
                "pollingIntervalMs must be positive (current: " + pollingIntervalMs + ")"
            );
        }
        this.pollingIntervalMs = pollingIntervalMs;
    }

    /**
     * 배치 크기 조회.
     *
     * @return 배치 크기 (한 번에 dequeue할 메시지 수)
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 배치 크기 설정.
     *
     * @param batchSize 배치 크기 (1 이상이어야 함)
     * @throws IllegalArgumentException batchSize가 양수가 아닌 경우
     */
    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive (current: " + batchSize + ")"
            );
        }
        this.batchSize = batchSize;
    }

    /**
     * 동시 처리 스레드 수 조회.
     *
     * @return 동시 처리 스레드 수
     */
    public int getConcurrency() {
        return concurrency;
    }

    /**
     * 동시 처리 스레드 수 설정.
     *
     * @param concurrency 동시 처리 스레드 수 (1 이상이어야 함)
     * @throws IllegalArgumentException concurrency가 양수가 아닌 경우
     */
    public void setConcurrency(int concurrency) {
        if (concurrency <= 0) {
            throw new IllegalArgumentException(
                "concurrency must be positive (current: " + concurrency + ")"
            );
        }
        this.concurrency = concurrency;
    }

    /**
     * 최대 처리 시간 조회.
     *
     * @return 최대 처리 시간 (밀리초)
     */
    public long getMaxProcessingTimeMs() {
        return maxProcessingTimeMs;
    }

    /**
     * 최대 처리 시간 설정.
     *
     * @param maxProcessingTimeMs 최대 처리 시간 (밀리초, 양수여야 함)
     * @throws IllegalArgumentException maxProcessingTimeMs가 양수가 아닌 경우
     */
    public void setMaxProcessingTimeMs(long maxProcessingTimeMs) {
        if (maxProcessingTimeMs <= 0) {
            throw new IllegalArgumentException(
                "maxProcessingTimeMs must be positive (current: " + maxProcessingTimeMs + ")"
            );
        }
        this.maxProcessingTimeMs = maxProcessingTimeMs;
    }

    /**
     * 최대 재시도 횟수 조회.
     *
     * @return 최대 재시도 횟수
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 최대 재시도 횟수 설정.
     *
     * @param maxRetries 최대 재시도 횟수 (1 이상이어야 함)
     * @throws IllegalArgumentException maxRetries가 양수가 아닌 경우
     */
    public void setMaxRetries(int maxRetries) {
        if (maxRetries <= 0) {
            throw new IllegalArgumentException(
                "maxRetries must be positive (current: " + maxRetries + ")"
            );
        }
        this.maxRetries = maxRetries;
    }

    /**
     * DLQ 전송 활성화 여부 조회.
     *
     * @return DLQ 전송 활성화 여부
     */
    public boolean isDlqEnabled() {
        return dlqEnabled;
    }

    /**
     * DLQ 전송 활성화 여부 설정.
     *
     * @param dlqEnabled DLQ 전송 활성화 여부
     */
    public void setDlqEnabled(boolean dlqEnabled) {
        this.dlqEnabled = dlqEnabled;
    }
}
