package com.ryuqq.orchestrator.core.model;

/**
 * 멱등성 키 (Idempotency Key).
 *
 * <p>IdemKey는 동일 요청의 중복 실행을 방지하기 위해 클라이언트가 제공하는 고유 키입니다.</p>
 *
 * <p><strong>사용 시나리오:</strong></p>
 * <ul>
 *   <li>클라이언트가 요청별로 고유 키 생성 (UUID 권장)</li>
 *   <li>네트워크 타임아웃 후 재시도 시 동일 IdemKey 사용</li>
 *   <li>서버는 (Domain, EventType, BizKey, IdemKey) 조합으로 중복 요청 감지</li>
 * </ul>
 *
 * <p><strong>불변성:</strong> 생성 후 값 변경 불가</p>
 * <p><strong>유효성 검증:</strong></p>
 * <ul>
 *   <li>null 또는 빈 문자열 불가</li>
 *   <li>길이: 1~255자</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class IdemKey {

    private final String value;

    private IdemKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IdemKey cannot be null or blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("IdemKey length cannot exceed 255 characters");
        }
        this.value = value;
    }

    /**
     * IdemKey 생성.
     *
     * @param value IdemKey 값
     * @return IdemKey 인스턴스
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static IdemKey of(String value) {
        return new IdemKey(value);
    }

    /**
     * IdemKey 값 조회.
     *
     * @return IdemKey 값
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdemKey idemKey = (IdemKey) o;
        return value.equals(idemKey.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "IdemKey{" + value + '}';
    }
}
