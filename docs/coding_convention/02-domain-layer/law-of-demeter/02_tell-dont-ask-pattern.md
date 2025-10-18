# Tell, Don’t Ask — 묻지 말고 시켜라 (CQRS 친화적 적용)

> **목표**: 커맨드 경로에서 **리치 도메인 모델**을 통해 불변식을 보장하고, **절차적 서비스**로의 비대화를 막는다.

본 문서는 **쓰기(커맨드)** 경로에 강제되는 규칙입니다. 조회(Read) 경로는 **데이터를 묻는 것(Ask)** 이 정상입니다.  
공통 원칙은 [_shared.business-logic-placement.md]을 참고하세요.

---

## CQRS에 따른 분리
| 구분 | 목적 | 모델 | 규칙 |
|---|---|---|---|
| **Command(쓰기)** | 상태 변경 | **리치 모델** | **Tell**: 불변식, 전이, 이벤트 |
| **Query(조회)** | 데이터 조회 | **얇은 모델/DTO** | **Ask**: 조인/정렬/페이징 |

---

## 도메인 메서드의 설계 원칙
- **동사형** + 유비쿼터스 언어: `confirm`, `cancel`, `reserveStock`, `charge`, `applyCoupon`, `changeOwner`
- **원자적 불변식 보장**: 메서드 내부에서 전후 일관성을 스스로 보장
- **도메인 이벤트 방출**: 의미 있는 전이 후 `OrderConfirmed` 등 이벤트 생성

### 예시 (Java 21)
```java
public final class Order {
    private OrderStatus status;
    private final Money total;
    private final ShippingAddress shipping;

    public void confirm(PaymentResult result) {
        if (status != OrderStatus.PAYMENT_AUTHORIZED) {
            throw new DomainException("INVALID_STATE", "결제 승인 상태가 아닙니다.");
        }
        if (!result.success()) {
            throw new DomainException("PAYMENT_FAILED", "결제에 실패했습니다.");
        }
        this.status = OrderStatus.CONFIRMED;
        DomainEvents.raise(new OrderConfirmed(this.id));
    }
}
```

### 애플리케이션 서비스 (오케스트레이션)
```java
@UseCase
@Transactional
public class ConfirmOrderService implements ConfirmOrderUseCase {
    private final LoadOrderPort loadOrder;
    private final SaveOrderPort saveOrder;
    private final ChargePaymentPort charge;
    private final OutboxPort outbox;

    @Override
    public void confirm(ConfirmOrderCommand cmd) {
        Order order = loadOrder.byId(cmd.orderId());
        PaymentResult pr = charge.charge(cmd.paymentMethod(), order.total());

        order.confirm(pr);               // ← Tell (불변식/전이/이벤트)
        saveOrder.save(order);           // 영속
        outbox.append(DomainEvents.pull()); // 이벤트를 Outbox에 기록
    }
}
```

---

## 트랜잭션/동시성/멱등성
- **트랜잭션 경계는 서비스**, 불변식은 **도메인 메서드**에서 보장
- **낙관적 락**(버전 필드)로 동시 수정 감지, 실패 시 재시도 정책
- **멱등성 키**(요청 ID)를 커맨드와 함께 전달해 **중복 처리 방지**

---

## 예외 정책
- `DomainException(code, message)` 베이스 클래스를 두고, API 계층에서 코드/메시지로 매핑
- 재시도 가능한 케이스(예: 일시적 재고 잠금 실패)는 **리트라이/백오프 정책** 문서화

---

## 금지 사항
- 애플리케이션 서비스에서 **도메인 객체의 setter 호출** 금지
- 도메인 외부에서 불변식을 파편화하여 검증하는 행위 금지

> 서비스는 **“순서대로 시키는 역할”**, 도메인은 **“규칙을 지키며 실행하는 역할”** 입니다.

---

## 체크리스트
- [ ] 커맨드 경로의 비즈니스 결정을 도메인 메서드로 모았는가?
- [ ] 도메인 이벤트를 방출하고 Outbox에 기록하는가?
- [ ] 동시성/멱등성 전략을 커맨드별로 명시했는가?
- [ ] 조회 경로에서 과도한 도메인 로직을 수행하지 않는가?

> 공통 원칙: [_shared.business-logic-placement.md] 참조