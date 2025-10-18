package com.ryuqq.orchestrator.adapter.runner;

/**
 * Reaper 설정 (불변 record).
 *
 * <p>이 record는 Reaper의 동작을 제어하는 설정값을 담고 있습니다.</p>
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
 * @param scanIntervalMs 스캔 주기 (밀리초, 양수여야 함)
 * @param timeoutThresholdMs 타임아웃 임계값 (밀리초, 양수여야 함)
 * @param batchSize 배치 크기 (1 이상이어야 함)
 * @param defaultStrategy 기본 리컨실 전략 (null이 아니어야 함)
 */
public record ReaperConfig(
    long scanIntervalMs,
    long timeoutThresholdMs,
    int batchSize,
    ReconcileStrategy defaultStrategy
) {

    /**
     * 기본 설정 생성자.
     *
     * <p>기본값: scanIntervalMs=300000ms (5분), timeoutThresholdMs=600000ms (10분),
     * batchSize=50, defaultStrategy=FAIL</p>
     */
    public ReaperConfig() {
        this(300000, 600000, 50, ReconcileStrategy.FAIL);
    }

    /**
     * Compact constructor (유효성 검증).
     *
     * @throws IllegalArgumentException 파라미터 검증 실패 시
     */
    public ReaperConfig {
        if (scanIntervalMs <= 0) {
            throw new IllegalArgumentException(
                "scanIntervalMs must be positive (current: " + scanIntervalMs + ")"
            );
        }
        if (timeoutThresholdMs <= 0) {
            throw new IllegalArgumentException(
                "timeoutThresholdMs must be positive (current: " + timeoutThresholdMs + ")"
            );
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive (current: " + batchSize + ")"
            );
        }
        if (defaultStrategy == null) {
            throw new IllegalArgumentException("defaultStrategy cannot be null");
        }
    }

    /**
     * scanIntervalMs만 변경한 새 인스턴스 생성.
     */
    public ReaperConfig withScanIntervalMs(long scanIntervalMs) {
        return new ReaperConfig(scanIntervalMs, timeoutThresholdMs, batchSize, defaultStrategy);
    }

    /**
     * timeoutThresholdMs만 변경한 새 인스턴스 생성.
     */
    public ReaperConfig withTimeoutThresholdMs(long timeoutThresholdMs) {
        return new ReaperConfig(scanIntervalMs, timeoutThresholdMs, batchSize, defaultStrategy);
    }

    /**
     * batchSize만 변경한 새 인스턴스 생성.
     */
    public ReaperConfig withBatchSize(int batchSize) {
        return new ReaperConfig(scanIntervalMs, timeoutThresholdMs, batchSize, defaultStrategy);
    }

    /**
     * defaultStrategy만 변경한 새 인스턴스 생성.
     */
    public ReaperConfig withDefaultStrategy(ReconcileStrategy defaultStrategy) {
        return new ReaperConfig(scanIntervalMs, timeoutThresholdMs, batchSize, defaultStrategy);
    }
}
