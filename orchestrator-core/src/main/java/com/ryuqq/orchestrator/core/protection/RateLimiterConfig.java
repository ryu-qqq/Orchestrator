package com.ryuqq.orchestrator.core.protection;

/**
 * Rate Limiter 설정.
 *
 * <p>Rate Limiter의 동작을 제어하는 설정 정보입니다.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public class RateLimiterConfig {

    private final double permitsPerSecond;
    private final int maxBurstSize;

    /**
     * Rate Limiter 설정 생성.
     *
     * @param permitsPerSecond 초당 허용 요청 수 (예: 100.0)
     * @param maxBurstSize 버스트 허용량 (Token Bucket의 버킷 크기)
     */
    public RateLimiterConfig(double permitsPerSecond, int maxBurstSize) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive");
        }
        if (maxBurstSize <= 0) {
            throw new IllegalArgumentException("maxBurstSize must be positive");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurstSize = maxBurstSize;
    }

    /**
     * 초당 허용 요청 수 조회.
     *
     * @return 초당 허용 요청 수
     */
    public double getPermitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * 버스트 허용량 조회.
     *
     * @return 버스트 허용량
     */
    public int getMaxBurstSize() {
        return maxBurstSize;
    }
}
