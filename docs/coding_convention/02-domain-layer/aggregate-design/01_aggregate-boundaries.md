# Aggregate Boundaries (No‑Association Edition) — 도메인 경계 = 불변식 경계

> **핵심 결정**: **ORM 연관관계는 사용하지 않는다.** 모든 교차 참조는 **ID 기반(typed id VO)** 으로만 수행한다.

## 1) 경계 정의 원칙
- **하나의 트랜잭션(=한 번의 상태 전이)에서 지켜야 하는 불변식의 최소 집합**을 Aggregate로 묶는다.
- 다른 Aggregate의 데이터를 **참조할 수는 있으나 변경하면 안 된다**. 필요 시 **도메인 이벤트 + 사가**로 분해한다.
- 조회 복잡도는 **CQRS Read 모델**로 해결하여 Aggregate 경계를 비대하게 키우지 않는다.

### 의사결정 체크리스트 (4문)
1. 서로의 데이터 없이 **자체 불변식**을 유지할 수 있는가? → 예(분리), 아니오(통합 검토)
2. 상태 변경 시 **항상 같은 트랜잭션**이 필요한가? → 예(통합), 아니오(분리)
3. 경합/잠금 비용이 큰가? → 크다(분리), 작다(통합 가능)
4. 조회 시 **동시 로딩**이 매우 빈번한가? → 빈번(분리 + Read 모델), 드묾(분리 유지)

## 2) No‑Association 정책 (도메인 규약)
- **Cross‑aggregate 관계는 전부 ID로만 참조**: `OrderId`, `CustomerId` 같은 **typed id record** 권장.
- 도메인 모델에 **ORM 어노테이션 금지**. (JPA 엔티티는 adapter‑out에 별도 정의)
- 컬렉션 노출 금지, **의미 있는 질의/명령 메서드**로만 내부 상태 접근을 허용.
- **Join/Fetch 전략 같은 영속성 용어는 본 문서에 포함하지 않는다.**

```java
public record OrderId(String value) {
  public OrderId {
    if (value == null || value.isBlank()) throw new DomainException("INVALID_ID", "OrderId 필수");
  }
}
public final class Order {
  private final OrderId id;
  private final CustomerId customerId; // ← No association, only typed id
  // ...
}
```

## 3) 읽기(조회) 전략
- 교차 집계 조회는 **Read 모델/프로젝션**으로 구현한다.
- 어댑터(예: JPA, MyBatis)에서 ID를 기반으로 조인/쿼리를 수행하고, API에는 **DTO/뷰 모델**만 반환한다.
- 도메인은 **조회 최적화 로직**을 가지지 않는다.

## 4) 이벤트 기반 협력
- Aggregate 간 협력은 **도메인 이벤트**로 연결한다. (예: `OrderConfirmed`, `InventoryReserved`)
- 이벤트는 **도메인에서 생성/보유**하지만, **영속/발행은 애플리케이션 레이어**에서 처리한다.  
  (자세한 트랜잭션/Outbox는 `03-application-layer/01_transaction-and-consistency.md` 문서 참조)

## 5) 체크리스트
- [ ] Cross‑aggregate **쓰기 금지**, 반드시 이벤트/사가로 분해
- [ ] 참조는 모두 **typed id**로 표현
- [ ] 조회 복잡도는 **Read 모델**로 분리
- [ ] 도메인 내 **ORM/트랜잭션 용어 금지**