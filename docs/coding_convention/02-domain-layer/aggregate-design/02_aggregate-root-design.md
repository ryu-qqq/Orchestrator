# Aggregate Root Design — 루트 중심 변경, 타입으로 전이 제약

> **핵심**: 루트를 통해서만 상태 변경을 수행하고, **유효성/불변식/이벤트**를 루트 메서드 안에서 보장한다.

## 1) 생성 & 전이
- 생성은 **정적 팩토리/팩토리 메서드**로 공개, 생성자(ctor)는 `package-private`/`private` 권장.
- 상태 전이는 **의미 있는 동사형 메서드**로만 허용(`confirm`, `cancel`, `applyCoupon` 등). **Setter 금지**.
- 메서드 내부에서 **사전조건 검사 → 상태 변경 → 도메인 이벤트 생성** 순으로 처리한다.

```java
public final class Order {
  private OrderStatus status;
  private final Money total;
  // ...

  public void confirm(PaymentResult result) {
    if (status != OrderStatus.AUTHORIZED) throw new DomainException("INVALID_STATE", "결제승인 전이 아님");
    if (!result.success()) throw new DomainException("PAYMENT_FAILED", "결제 실패");
    this.status = OrderStatus.CONFIRMED;
    DomainEvents.raise(new OrderConfirmed(id));
  }
}
```

## 2) Java 21 타입 활용
- VO는 **`record`**로, 상태 모델은 **`sealed interface`/`enum`**으로 전이 제약을 **타입 레벨**에서 표현.
- 컬렉션은 **불변 뷰**로 보관(`List.copyOf`). 외부에 **변경 가능 참조**를 노출하지 않는다.

## 3) Repository Port (Application 레벨)
> **중요**: Repository Port(인터페이스)는 **Application 모듈**에 위치합니다. Domain은 순수 모델만 포함합니다.

- **루트 단위로만** 로드/저장. 내부 엔티티/값 객체에 대한 별도 Repository 금지.
- 로딩은 주로 `byId`/`byBusinessKey` 등 **식별 기반**.
  (조인은 어댑터의 Read 모델에서 처리)

```java
// application/port/out/LoadOrderPort.java
public interface LoadOrderPort {
  Optional<Order> byId(OrderId id);
}

// application/port/out/SaveOrderPort.java
public interface SaveOrderPort {
  void save(Order order);
}
```

**의존성 방향:**
- Domain ← Application (Port) ← Adapter (구현체)
- Application Service가 Port를 통해 Domain과 Adapter를 연결

## 4) 테스트 지침
- 루트 public API 기준으로 **계약 기반 테스트** 작성.
- 불변식 표(합계 일치, 상태 전이 허용 그래프 등)를 테스트로 확인.

## 5) 체크리스트
- [ ] 루트 외 개별 엔티티에 대한 Repository 금지
- [ ] 상태 변경은 **동사형 의미 메서드**로만
- [ ] VO=record, 상태=sealed/enum 적용
- [ ] 도메인 이벤트는 루트 메서드에서 생성