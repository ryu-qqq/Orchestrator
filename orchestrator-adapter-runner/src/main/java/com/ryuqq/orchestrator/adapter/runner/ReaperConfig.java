package com.ryuqq.orchestrator.adapter.runner;

/**
 * Reaper 설정.
 *
 * <p>이 클래스는 Reaper의 동작을 제어하는 설정값을 담고 있습니다.</p>
 *
 * <p><strong>설정 항목:</strong></p>
 * <ul>
 *   <li>scanIntervalMs: 스캔 주기 (기본 300000ms = 5분)</li>
 *   <li>timeoutThresholdMs: 타임아웃 임계값 (기본 600000ms = 10분)</li>
 *   <li>batchSize: 한 번에 처리할 항목 수 (기본 50)</li>
 *   <li>defaultStrategy: 기본 리컨실 전략 (기본 FAIL)</li>
 * </ul>
 *
 * <p><strong>타임아웃 임계값 설정 가이드:</strong></p>
 * <ul>
 *   <li>짧은 작업 (1-5분): timeoutThresholdMs = 300000ms (5분)</li>
 *   <li>중간 작업 (5-30분): timeoutThresholdMs = 1800000ms (30분)</li>
 *   <li>긴 작업 (30분-1시간): timeoutThresholdMs = 3600000ms (1시간)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class ReaperConfig {

    private long scanIntervalMs = 300000;        // 5분
    private long timeoutThresholdMs = 600000;    // 10분
    private int batchSize = 50;
    private ReconcileStrategy defaultStrategy = ReconcileStrategy.FAIL;

    public ReaperConfig() {
    }

    /**
     * 스캔 주기 조회.
     *
     * @return 스캔 주기 (밀리초)
     */
    public long getScanIntervalMs() {
        return scanIntervalMs;
    }

    /**
     * 스캔 주기 설정.
     *
     * @param scanIntervalMs 스캔 주기 (밀리초, 양수여야 함)
     * @throws IllegalArgumentException scanIntervalMs가 양수가 아닌 경우
     */
    public void setScanIntervalMs(long scanIntervalMs) {
        if (scanIntervalMs <= 0) {
            throw new IllegalArgumentException(
                "scanIntervalMs must be positive (current: " + scanIntervalMs + ")"
            );
        }
        this.scanIntervalMs = scanIntervalMs;
    }

    /**
     * 타임아웃 임계값 조회.
     *
     * @return 타임아웃 임계값 (밀리초)
     */
    public long getTimeoutThresholdMs() {
        return timeoutThresholdMs;
    }

    /**
     * 타임아웃 임계값 설정.
     *
     * @param timeoutThresholdMs 타임아웃 임계값 (밀리초, 양수여야 함)
     * @throws IllegalArgumentException timeoutThresholdMs가 양수가 아닌 경우
     */
    public void setTimeoutThresholdMs(long timeoutThresholdMs) {
        if (timeoutThresholdMs <= 0) {
            throw new IllegalArgumentException(
                "timeoutThresholdMs must be positive (current: " + timeoutThresholdMs + ")"
            );
        }
        this.timeoutThresholdMs = timeoutThresholdMs;
    }

    /**
     * 배치 크기 조회.
     *
     * @return 배치 크기 (한 번에 처리할 항목 수)
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
     * 기본 리컨실 전략 조회.
     *
     * @return 기본 리컨실 전략
     */
    public ReconcileStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    /**
     * 기본 리컨실 전략 설정.
     *
     * @param defaultStrategy 기본 리컨실 전략
     * @throws IllegalArgumentException defaultStrategy가 null인 경우
     */
    public void setDefaultStrategy(ReconcileStrategy defaultStrategy) {
        if (defaultStrategy == null) {
            throw new IllegalArgumentException("defaultStrategy cannot be null");
        }
        this.defaultStrategy = defaultStrategy;
    }
}
