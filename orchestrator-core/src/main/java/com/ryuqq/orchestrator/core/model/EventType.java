package com.ryuqq.orchestrator.core.model;

/**
 * 이벤트 유형 구분자.
 *
 * <p>EventType은 도메인 내 이벤트 유형을 구분하여 멱등성 키의 일부로 사용되며,
 * 동일 도메인 내 서로 다른 작업을 구분합니다.</p>
 *
 * <p><strong>예시:</strong></p>
 * <ul>
 *   <li>EventType.of("CREATE_ORDER") - 주문 생성</li>
 *   <li>EventType.of("CANCEL_ORDER") - 주문 취소</li>
 *   <li>EventType.of("PROCESS_PAYMENT") - 결제 처리</li>
 * </ul>
 *
 * <p><strong>불변성:</strong> 생성 후 값 변경 불가</p>
 * <p><strong>유효성 검증:</strong></p>
 * <ul>
 *   <li>null 또는 빈 문자열 불가</li>
 *   <li>길이: 1~50자</li>
 *   <li>패턴: 대문자와 언더스코어만 허용 (예: CREATE_ORDER)</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public final class EventType {

    private final String value;

    private EventType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("EventType cannot be null or blank");
        }
        if (value.length() > 50) {
            throw new IllegalArgumentException("EventType length cannot exceed 50 characters");
        }
        if (!value.matches("^[A-Z_]+$")) {
            throw new IllegalArgumentException("EventType must contain only uppercase letters and underscores");
        }
        this.value = value;
    }

    /**
     * EventType 생성.
     *
     * @param value EventType 값 (예: CREATE_ORDER, PROCESS_PAYMENT)
     * @return EventType 인스턴스
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static EventType of(String value) {
        return new EventType(value);
    }

    /**
     * EventType 값 조회.
     *
     * @return EventType 값
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventType eventType = (EventType) o;
        return value.equals(eventType.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "EventType{" + value + '}';
    }
}
