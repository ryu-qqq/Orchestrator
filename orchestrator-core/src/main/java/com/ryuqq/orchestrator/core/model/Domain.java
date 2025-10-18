package com.ryuqq.orchestrator.core.model;

import java.util.regex.Pattern;

/**
 * 업무 도메인 구분자.
 *
 * <p>Domain은 업무 영역을 구분하여 멱등성 키의 일부로 사용되며,
 * 도메인별 Operation 분리 및 추적을 가능하게 합니다.</p>
 *
 * <p><strong>예시:</strong></p>
 * <ul>
 *   <li>Domain.of("ORDER") - 주문 도메인</li>
 *   <li>Domain.of("PAYMENT") - 결제 도메인</li>
 *   <li>Domain.of("FILE_UPLOAD") - 파일 업로드 도메인</li>
 * </ul>
 *
 * <p><strong>불변성:</strong> 생성 후 값 변경 불가</p>
 * <p><strong>유효성 검증:</strong></p>
 * <ul>
 *   <li>null 또는 빈 문자열 불가</li>
 *   <li>길이: 1~50자</li>
 *   <li>패턴: 대문자와 언더스코어만 허용 (예: ORDER, FILE_UPLOAD)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class Domain {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Z_]+$");

    private final String value;

    private Domain(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Domain cannot be null or blank");
        }
        if (value.length() > 50) {
            throw new IllegalArgumentException("Domain length cannot exceed 50 characters");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Domain must contain only uppercase letters and underscores");
        }
        this.value = value;
    }

    /**
     * Domain 생성.
     *
     * @param value Domain 값 (예: ORDER, PAYMENT)
     * @return Domain 인스턴스
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static Domain of(String value) {
        return new Domain(value);
    }

    /**
     * Domain 값 조회.
     *
     * @return Domain 값
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Domain domain = (Domain) o;
        return value.equals(domain.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "Domain{" + value + '}';
    }
}
