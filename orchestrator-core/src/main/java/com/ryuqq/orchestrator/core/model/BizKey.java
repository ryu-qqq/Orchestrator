package com.ryuqq.orchestrator.core.model;

/**
 * 업무적으로 의미 있는 엔티티 식별자.
 *
 * <p>BizKey는 업무 도메인에서 사용하는 엔티티의 고유 식별자입니다.</p>
 *
 * <p><strong>예시:</strong></p>
 * <ul>
 *   <li>주문 도메인: 주문번호 (ORDER-20251018-001)</li>
 *   <li>결제 도메인: 거래ID (TXN-123456)</li>
 *   <li>파일 도메인: 파일명 또는 파일ID</li>
 * </ul>
 *
 * <p><strong>불변성:</strong> 생성 후 값 변경 불가</p>
 * <p><strong>유효성 검증:</strong></p>
 * <ul>
 *   <li>null 또는 빈 문자열 불가</li>
 *   <li>길이: 1~100자</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class BizKey {

    private final String value;

    private BizKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BizKey cannot be null or blank");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException("BizKey length cannot exceed 100 characters");
        }
        this.value = value;
    }

    /**
     * BizKey 생성.
     *
     * @param value BizKey 값
     * @return BizKey 인스턴스
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static BizKey of(String value) {
        return new BizKey(value);
    }

    /**
     * BizKey 값 조회.
     *
     * @return BizKey 값
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BizKey bizKey = (BizKey) o;
        return value.equals(bizKey.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "BizKey{" + value + '}';
    }
}
