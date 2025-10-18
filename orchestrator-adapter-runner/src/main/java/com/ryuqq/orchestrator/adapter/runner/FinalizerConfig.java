package com.ryuqq.orchestrator.adapter.runner;

/**
 * Finalizer 설정 (불변 record).
 *
 * <p>이 record는 Finalizer의 동작을 제어하는 설정값을 담고 있습니다.</p>
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
 * @param scanIntervalMs 스캔 주기 (밀리초, 양수여야 함)
 * @param batchSize 배치 크기 (1 이상이어야 함)
 */
public record FinalizerConfig(long scanIntervalMs, int batchSize) {

    /**
     * 기본 설정 생성자.
     *
     * <p>기본값: scanIntervalMs=60000ms (1분), batchSize=100</p>
     */
    public FinalizerConfig() {
        this(60000, 100);
    }

    /**
     * Compact constructor (유효성 검증).
     *
     * @throws IllegalArgumentException 파라미터 검증 실패 시
     */
    public FinalizerConfig {
        if (scanIntervalMs <= 0) {
            throw new IllegalArgumentException(
                "scanIntervalMs must be positive (current: " + scanIntervalMs + ")"
            );
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive (current: " + batchSize + ")"
            );
        }
    }

    /**
     * scanIntervalMs만 변경한 새 인스턴스 생성.
     *
     * @param scanIntervalMs 새로운 스캔 주기 (밀리초)
     * @return 새 FinalizerConfig 인스턴스
     */
    public FinalizerConfig withScanIntervalMs(long scanIntervalMs) {
        return new FinalizerConfig(scanIntervalMs, this.batchSize);
    }

    /**
     * batchSize만 변경한 새 인스턴스 생성.
     *
     * @param batchSize 새로운 배치 크기
     * @return 새 FinalizerConfig 인스턴스
     */
    public FinalizerConfig withBatchSize(int batchSize) {
        return new FinalizerConfig(this.scanIntervalMs, batchSize);
    }
}
