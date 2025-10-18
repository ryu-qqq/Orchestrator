# Domain Encapsulation — 도메인 캡슐화와 불변식

> **목표**: 도메인 모델의 **불변식**과 **상태 전이 규칙**을 객체 내부에 가두어, 변경 내성을 높인다.

본 문서는 **타입 설계/가시성/불변성**에 초점을 둡니다. 공통 원칙은 [_shared.business-logic-placement.md]을 참고하세요.

---

## 타입 설계 (Java 21 친화)

### 값 객체 (Value Object)
- **`record`** 사용 권장: 불변, `equals/hashCode/toString` 자동
- **캐노니컬 생성자**에서 **검증** 수행
- 컬렉션은 **불변 뷰**로 보관

```java
public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount == null || amount.signum() < 0) throw new DomainException("INVALID_AMOUNT", "금액은 0 이상이어야 합니다.");
        if (currency == null || currency.isBlank()) throw new DomainException("INVALID_CURRENCY", "통화 필수");
    }
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }
    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) throw new DomainException("CURRENCY_MISMATCH", "통화 불일치");
    }
}
```

### 상태 모델
- **`sealed interface`**로 상태 계층을 제한하여 **전이 제약을 타입 레벨**에서 표현

```java
public sealed interface OrderState permits Created, Authorized, Confirmed, Cancelled {}
final class Created implements OrderState {}
final class Authorized implements OrderState {}
final class Confirmed implements OrderState {}
final class Cancelled implements OrderState {}
```

> 전이를 **도메인 메서드**에서만 수행하게 하여, 상태 불일치를 방지합니다.

---

## 가시성 & 생성 정책
- 생성자는 **`package-private`** 또는 `private` 로 감추고, **정적 팩토리** 발표
- 컬렉션 필드는 **불변**으로 유지하고, 외부에는 **읽기 전용 뷰**만 노출
- **public setter 금지**, 상태 변경은 **의미 있는 도메인 메서드**로만

```java
public final class Order {
    private Long id;
    private OrderStatus status;
    private final List<OrderLine> lines;

    Order(Long id, List<OrderLine> lines) { // package-private
        this.id = id;
        this.lines = List.copyOf(lines); // 불변
        this.status = OrderStatus.CREATED;
    }

    public static Order create(List<OrderLine> lines) {
        return new Order(null, lines);
    }

    public List<OrderLine> lines() {
        return List.copyOf(lines);
    }
}
```

---

## JPA ↔ 도메인 분리 (Adapter-out)
- **엔티티는 `adapter-out-jpa`** 에 두고, 도메인 모델은 `/domain`에 둡니다.
- 매핑은 **Mapper(어댑터 책임)** 에 배치, 도메인은 JPA 어노테이션에 오염되지 않게 유지
- 명명 규칙:
  - JPA 엔티티: `OrderEntity`
  - 도메인 모델: `Order`
  - 매퍼: `OrderJpaMapper`

```java
// adapter-out-jpa
@Entity
class OrderEntity { /* JPA 어노테이션 & 필드 */ }

@Component
class OrderJpaMapper {
    Order toDomain(OrderEntity e) { /* ... */ }
    OrderEntity toEntity(Order d) { /* ... */ }
}
```

---

## 불변식 표 (예시)
| 항목 | 규칙 | 검증 위치 |
|---|---|---|
| 금액 | 0 이상 | `Money` record ctor |
| 상태 전이 | `AUTHORIZED → CONFIRMED` 만 허용 | `Order.confirm()` |
| 합계 | 라인 합계 = 주문 합계 | `Order.recalculateTotal()` |
| 배송지 | 국가/우편번호 형식 | `ShippingAddress` record ctor |

---

## ArchUnit 보조 규칙 (예시)
```java
ArchRule domain_not_expose_setters =
    methods().that().areDeclaredInClassesThat().resideInAPackage("..domain..")
        .should().notHaveNameMatching("set[A-Z].*");

ArchRule domain_not_depend_on_adapters =
    layeredArchitecture()
        .layer("Domain").definedBy("..domain..")
        .layer("Adapters").definedBy("..adapter..")
        .whereLayer("Domain").mayNotBeAccessedByAnyLayer()
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application");
```

---

## 체크리스트
- [ ] VO는 `record`로, 생성자에서 검증을 수행하는가?
- [ ] 상태 전이를 `sealed`/`enum` 등 타입으로 표현했는가?
- [ ] public setter 없이 의미 메서드로만 상태 변경하는가?
- [ ] JPA 어노테이션이 도메인 코드에 섞이지 않았는가?
- [ ] 불변식이 표와 테스트 케이스로 문서화되었는가?

> 공통 원칙: [_shared.business-logic-placement.md] 참조