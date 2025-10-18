# Domain Aggregate 생성 커맨드

당신은 DDD(Domain-Driven Design) Aggregate를 생성하는 전문가입니다.

## 🎯 컨텍스트 주입 (자동)

---

## 🎯 DOMAIN 레이어 규칙 (자동 주입됨)

### ❌ 금지 규칙 (Zero-Tolerance)

- **Lombok 절대 금지**: `@Data`, `@Builder`, `@Getter`, `@Setter` 등 모든 Lombok 어노테이션
- **Getter 체이닝 금지**: `order.getCustomer().getAddress().getZip()` 형태 금지
- **ORM 어노테이션 금지**: Domain 레이어에 `@Entity`, `@Table`, `@Column` 사용 금지
- **Spring 어노테이션 금지**: `@Service`, `@Repository`, `@Transactional` 금지
- **Law of Demeter 위반**: Tell, Don't Ask 패턴 준수

### ✅ 필수 규칙

- **Pure Java**: Domain은 순수 Java만 사용 (프레임워크 의존성 없음)
- **Javadoc 필수**: 모든 public 클래스/메서드에 `@author`, `@since` 포함
- **불변 필드**: Aggregate 식별자와 중요 속성은 `final` 선언
- **캡슐화**: 내부 컬렉션은 `Collections.unmodifiableList()` 반환
- **Tell, Don't Ask**: 상태를 묻지 말고 명령을 내리는 메서드 제공

### 📋 상세 문서

- [Aggregate Boundaries](docs/coding_convention/02-domain-layer/aggregate-design/01_aggregate-boundaries.md)
- [Law of Demeter - Getter Chaining Prohibition](docs/coding_convention/02-domain-layer/law-of-demeter/01_getter-chaining-prohibition.md)
- [Tell, Don't Ask Pattern](docs/coding_convention/02-domain-layer/law-of-demeter/02_tell-dont-ask-pattern.md)

**이 규칙들은 실시간으로 검증됩니다.**

---

## 📋 작업 지시

### 1. 입력 분석

- **Aggregate 이름**: 첫 번째 인자로 전달된 엔티티명 (예: `Order`, `Payment`, `Shipment`)
- **PRD 파일** (선택): 두 번째 인자로 PRD 문서 경로 (있을 경우 분석하여 비즈니스 로직 구현)

### 2. 생성할 파일

다음 파일을 `domain/src/main/java/com/company/template/domain/model/` 경로에 생성:

```
domain/src/main/java/com/company/template/domain/model/
├── {AggregateName}.java          # Aggregate Root
├── {AggregateName}Id.java        # Typed ID (record)
├── {AggregateName}Status.java    # Status Enum
└── {Entity}.java                 # 내부 Entity (필요시)
```

### 3. 필수 준수 규칙

#### Aggregate Root 패턴

```java
/**
 * {AggregateName} Aggregate Root
 *
 * <p>{간단한 설명}</p>
 *
 * <p><strong>규칙 준수:</strong></p>
 * <ul>
 *   <li>❌ Lombok 사용 안함 - Pure Java</li>
 *   <li>✅ Law of Demeter - Getter 체이닝 방지</li>
 *   <li>✅ Tell, Don't Ask 패턴 적용</li>
 *   <li>✅ Aggregate 경계 내에서 일관성 보장</li>
 * </ul>
 *
 * @author Claude
 * @since {현재 날짜}
 */
public class {AggregateName} {

    // 1. 불변 필드 (final)
    private final {AggregateName}Id id;
    private final String customerId;  // 다른 Aggregate 참조는 ID만

    // 2. 가변 필드
    private {AggregateName}Status status;
    private BigDecimal totalAmount;

    // 3. 내부 컬렉션 (private)
    private final List<OrderLineItem> lineItems;

    // 4. 생성자 (모든 불변 필드 초기화 + 유효성 검증)
    /**
     * {AggregateName}를 생성합니다.
     *
     * @param id {AggregateName} ID
     * @param customerId 고객 ID
     * @author Claude
     * @since {현재 날짜}
     */
    public {AggregateName}({AggregateName}Id id, String customerId) {
        if (id == null) {
            throw new IllegalArgumentException("{AggregateName} ID는 필수입니다");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID는 필수입니다");
        }

        this.id = id;
        this.customerId = customerId;
        this.lineItems = new ArrayList<>();
        this.status = {AggregateName}Status.PENDING;
        this.totalAmount = BigDecimal.ZERO;
    }

    // 5. 비즈니스 메서드 (Tell, Don't Ask 패턴)
    /**
     * {비즈니스 액션 설명}
     *
     * <p>Law of Demeter 준수: 내부 로직을 캡슐화</p>
     *
     * @param {파라미터} {설명}
     * @author Claude
     * @since {현재 날짜}
     */
    public void doBusinessAction(String param) {
        // 상태 검증
        if (this.status != {AggregateName}Status.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 가능합니다");
        }

        // 비즈니스 로직
        this.status = {AggregateName}Status.CONFIRMED;
    }

    // 6. 질의 메서드 (Law of Demeter 준수 - boolean 질문 형태)
    /**
     * Law of Demeter 준수: 상태를 묻는 메서드
     *
     * <p>❌ Bad: aggregate.getStatus().equals(CONFIRMED)</p>
     * <p>✅ Good: aggregate.isConfirmed()</p>
     *
     * @return 확정 여부
     * @author Claude
     * @since {현재 날짜}
     */
    public boolean isConfirmed() {
        return this.status == {AggregateName}Status.CONFIRMED;
    }

    // 7. Getters (최소한만 노출)
    /**
     * {AggregateName} ID를 반환합니다.
     *
     * @return {AggregateName} ID
     * @author Claude
     * @since {현재 날짜}
     */
    public {AggregateName}Id getId() {
        return id;
    }

    /**
     * 내부 컬렉션을 반환합니다 (불변).
     *
     * <p>Law of Demeter: 내부 컬렉션의 직접 수정 방지</p>
     *
     * @return 읽기 전용 컬렉션
     * @author Claude
     * @since {현재 날짜}
     */
    public List<OrderLineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }
}
```

#### Typed ID 패턴 (Java Record)

```java
/**
 * {AggregateName} 식별자
 *
 * @param value ID 값
 * @author Claude
 * @since {현재 날짜}
 */
public record {AggregateName}Id(String value) {
    public {AggregateName}Id {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("{AggregateName} ID는 필수입니다");
        }
    }
}
```

#### Status Enum 패턴

```java
/**
 * {AggregateName} 상태
 *
 * @author Claude
 * @since {현재 날짜}
 */
public enum {AggregateName}Status {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
```

### 4. 생성 체크리스트

생성 후 다음 사항을 확인하세요:

- [ ] **Lombok 미사용**: 모든 생성자/getter를 수동 작성
- [ ] **Javadoc 완전성**: 모든 public 클래스/메서드에 `@author`, `@since` 포함
- [ ] **Law of Demeter**: Getter 체이닝 없음, Tell Don't Ask 준수
- [ ] **Pure Java**: Spring/JPA 어노테이션 없음
- [ ] **불변성**: 중요 필드는 `final` 선언
- [ ] **캡슐화**: 컬렉션은 `unmodifiableList()` 반환
- [ ] **유효성 검증**: 생성자에서 모든 필수 값 검증
- [ ] **비즈니스 로직**: 상태 변경은 명령 메서드로 캡슐화

## 🚀 실행

PRD가 제공된 경우 PRD를 읽고 비즈니스 요구사항을 분석한 후, 위 규칙을 따라 Aggregate를 생성하세요.

PRD가 없는 경우 기본 템플릿 구조로 Aggregate를 생성하세요.
