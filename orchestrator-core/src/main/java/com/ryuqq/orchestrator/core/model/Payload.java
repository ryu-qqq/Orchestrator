package com.ryuqq.orchestrator.core.model;

/**
 * Command에 포함될 업무 데이터의 직렬화된 형태.
 *
 * <p>Payload는 실제 업무 로직에 필요한 데이터를 전달하며,
 * 직렬화 형식(JSON, XML, Protobuf 등)은 사용자가 선택할 수 있습니다.</p>
 *
 * <p><strong>예시:</strong></p>
 * <ul>
 *   <li>JSON: Payload.of("{\"orderId\":123,\"amount\":50000}")</li>
 *   <li>XML: Payload.of("&lt;order&gt;&lt;id&gt;123&lt;/id&gt;&lt;/order&gt;")</li>
 *   <li>빈 Payload: Payload.of("")</li>
 * </ul>
 *
 * <p><strong>불변성:</strong> 생성 후 값 변경 불가</p>
 * <p><strong>유효성 검증:</strong></p>
 * <ul>
 *   <li>null 허용 (빈 Payload 가능)</li>
 *   <li>길이 제한 없음 (단, 실무에서는 1MB 권장)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class Payload {

    private final String value;

    private Payload(String value) {
        // null 허용, 빈 문자열도 허용
        this.value = value;
    }

    /**
     * Payload 생성.
     *
     * @param value Payload 값 (null 허용)
     * @return Payload 인스턴스
     */
    public static Payload of(String value) {
        return new Payload(value);
    }

    /**
     * 빈 Payload 생성.
     *
     * @return 빈 Payload 인스턴스
     */
    public static Payload empty() {
        return new Payload("");
    }

    /**
     * Payload 값 조회.
     *
     * @return Payload 값 (null 가능)
     */
    public String getValue() {
        return value;
    }

    /**
     * Payload가 비어있는지 확인.
     *
     * @return 비어있으면 true
     */
    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payload payload = (Payload) o;
        if (value == null) return payload.value == null;
        return value.equals(payload.value);
    }

    @Override
    public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }

    @Override
    public String toString() {
        return "Payload{" + (value == null ? "null" : value.length() + " chars") + '}';
    }
}
