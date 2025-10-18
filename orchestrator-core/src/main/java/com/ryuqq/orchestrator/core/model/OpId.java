package com.ryuqq.orchestrator.core.model;

/**
 * Operation의 전역 고유 식별자.
 *
 * <p>OpId는 시스템 내 모든 Operation을 추적하는 데 사용되며,
 * 외부 API 호출 시 멱등성 키로 활용될 수 있습니다.</p>
 *
 * <p><strong>불변성:</strong> 생성 후 값 변경 불가</p>
 * <p><strong>유효성 검증:</strong></p>
 * <ul>
 *   <li>null 또는 빈 문자열 불가</li>
 *   <li>길이: 1~255자</li>
 *   <li>패턴: 영숫자, 하이픈(-), 언더스코어(_)만 허용</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class OpId {

    private final String value;

    private OpId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OpId cannot be null or blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("OpId length cannot exceed 255 characters");
        }
        if (!value.matches("^[a-zA-Z0-9\\-_]+$")) {
            throw new IllegalArgumentException("OpId contains invalid characters. Only alphanumeric, hyphen, and underscore are allowed");
        }
        this.value = value;
    }

    /**
     * OpId 생성.
     *
     * @param value OpId 값
     * @return OpId 인스턴스
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static OpId of(String value) {
        return new OpId(value);
    }

    /**
     * OpId 값 조회.
     *
     * @return OpId 값
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpId opId = (OpId) o;
        return value.equals(opId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "OpId{" + value + '}';
    }
}
