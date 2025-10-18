package com.ryuqq.orchestrator.adapter.runner;

/**
 * Finalizer 설정.
 *
 * <p>이 클래스는 Finalizer의 동작을 제어하는 설정값을 담고 있습니다.</p>
 *
 * <p><strong>설정 항목:</strong></p>
 * <ul>
 *   <li>scanIntervalMs: 스캔 주기 (기본 60000ms = 1분)</li>
 *   <li>batchSize: 한 번에 처리할 항목 수 (기본 100)</li>
 * </ul>
 *
 * <p><strong>성능 튜닝 가이드:</strong></p>
 * <ul>
 *   <li>높은 복구 속도: scanIntervalMs 감소, batchSize 증가</li>
 *   <li>자원 절약: scanIntervalMs 증가, batchSize 감소</li>
 *   <li>권장 값: 1-5분 간격, 50-200 배치 크기</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class FinalizerConfig {

    private long scanIntervalMs = 60000;  // 1분
    private int batchSize = 100;

    public FinalizerConfig() {
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
}
