package com.ryuqq.orchestrator.adapter.runner;

/**
 * QueueWorkerRunner 설정 (불변 record).
 *
 * <p>이 record는 QueueWorkerRunner의 동작을 제어하는 설정값을 담고 있습니다.</p>
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
 * @param pollingIntervalMs 큐 폴링 간격 (밀리초, 양수여야 함)
 * @param batchSize 배치 크기 (1 이상이어야 함)
 * @param concurrency 동시 처리 스레드 수 (1 이상이어야 함)
 * @param maxProcessingTimeMs 최대 처리 시간 (밀리초, 양수여야 함)
 * @param maxRetries 최대 재시도 횟수 (1 이상이어야 함)
 * @param dlqEnabled DLQ 전송 활성화 여부
 */
public record QueueWorkerConfig(
    long pollingIntervalMs,
    int batchSize,
    int concurrency,
    long maxProcessingTimeMs,
    int maxRetries,
    boolean dlqEnabled
) {

    /**
     * 기본 설정 생성자.
     *
     * <p>기본값: pollingIntervalMs=100ms, batchSize=10, concurrency=5,
     * maxProcessingTimeMs=30000ms, maxRetries=3, dlqEnabled=true</p>
     */
    public QueueWorkerConfig() {
        this(100, 10, 5, 30000, 3, true);
    }

    /**
     * Compact constructor (유효성 검증).
     *
     * @throws IllegalArgumentException 파라미터 검증 실패 시
     */
    public QueueWorkerConfig {
        if (pollingIntervalMs <= 0) {
            throw new IllegalArgumentException(
                "pollingIntervalMs must be positive (current: " + pollingIntervalMs + ")"
            );
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive (current: " + batchSize + ")"
            );
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException(
                "concurrency must be positive (current: " + concurrency + ")"
            );
        }
        if (maxProcessingTimeMs <= 0) {
            throw new IllegalArgumentException(
                "maxProcessingTimeMs must be positive (current: " + maxProcessingTimeMs + ")"
            );
        }
        if (maxRetries <= 0) {
            throw new IllegalArgumentException(
                "maxRetries must be positive (current: " + maxRetries + ")"
            );
        }
    }

    /**
     * pollingIntervalMs만 변경한 새 인스턴스 생성.
     */
    public QueueWorkerConfig withPollingIntervalMs(long pollingIntervalMs) {
        return new QueueWorkerConfig(pollingIntervalMs, batchSize, concurrency, maxProcessingTimeMs, maxRetries, dlqEnabled);
    }

    /**
     * batchSize만 변경한 새 인스턴스 생성.
     */
    public QueueWorkerConfig withBatchSize(int batchSize) {
        return new QueueWorkerConfig(pollingIntervalMs, batchSize, concurrency, maxProcessingTimeMs, maxRetries, dlqEnabled);
    }

    /**
     * concurrency만 변경한 새 인스턴스 생성.
     */
    public QueueWorkerConfig withConcurrency(int concurrency) {
        return new QueueWorkerConfig(pollingIntervalMs, batchSize, concurrency, maxProcessingTimeMs, maxRetries, dlqEnabled);
    }

    /**
     * maxProcessingTimeMs만 변경한 새 인스턴스 생성.
     */
    public QueueWorkerConfig withMaxProcessingTimeMs(long maxProcessingTimeMs) {
        return new QueueWorkerConfig(pollingIntervalMs, batchSize, concurrency, maxProcessingTimeMs, maxRetries, dlqEnabled);
    }

    /**
     * maxRetries만 변경한 새 인스턴스 생성.
     */
    public QueueWorkerConfig withMaxRetries(int maxRetries) {
        return new QueueWorkerConfig(pollingIntervalMs, batchSize, concurrency, maxProcessingTimeMs, maxRetries, dlqEnabled);
    }

    /**
     * dlqEnabled만 변경한 새 인스턴스 생성.
     */
    public QueueWorkerConfig withDlqEnabled(boolean dlqEnabled) {
        return new QueueWorkerConfig(pollingIntervalMs, batchSize, concurrency, maxProcessingTimeMs, maxRetries, dlqEnabled);
    }
}
