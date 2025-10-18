# Dynamic Query - QueryDSL 동적 쿼리

**목적**: BooleanBuilder로 동적 조건 구성

**관련 문서**:
- [DTO Projection](./01_dto-projection.md)
- [Search UseCase](../../03-application-layer/usecase-design/02_query-usecase.md)

**필수 버전**: Java 21+, QueryDSL 5.0+

---

## 📌 핵심 원칙

### 동적 쿼리란?

1. **조건부 WHERE**: 파라미터에 따라 WHERE 절 변경
2. **BooleanBuilder**: 조건 조합
3. **Null-Safe**: Null 파라미터 무시

---

## ❌ JPQL 문자열 조합

```java
// ❌ Before - JPQL 문자열 조합
public List<Order> search(String status, LocalDate startDate) {
    String jpql = "SELECT o FROM Order o WHERE 1=1";

    if (status != null) {
        jpql += " AND o.status = :status";  // ❌ SQL Injection 위험
    }

    if (startDate != null) {
        jpql += " AND o.createdAt >= :startDate";
    }

    // ⚠️ 타입 안전하지 않음
    return entityManager.createQuery(jpql).getResultList();
}
```

---

## ✅ BooleanBuilder 패턴

```java
/**
 * Order QueryDSL Repository
 *
 * 📁 Package: adapter.out.persistence-mysql.order.querydsl
 *
 * @author development-team
 * @since 1.0.0
 */
@Repository
public class OrderQueryDslRepository {

    /**
     * ✅ BooleanBuilder - 동적 WHERE 조건
     */
    public List<Order> search(SearchOrdersQuery query) {
        BooleanBuilder builder = new BooleanBuilder();

        // ✅ Null-Safe 조건 추가
        if (query.customerId() != null) {
            builder.and(order.customerId.eq(query.customerId()));
        }

        if (query.status() != null) {
            builder.and(order.status.eq(query.status()));
        }

        if (query.startDate() != null) {
            builder.and(order.createdAt.goe(query.startDate().atStartOfDay()));
        }

        if (query.endDate() != null) {
            builder.and(order.createdAt.lt(query.endDate().plusDays(1).atStartOfDay()));
        }

        return queryFactory
            .selectFrom(order)
            .where(builder)  // ✅ 동적 WHERE
            .orderBy(order.createdAt.desc())
            .fetch();
    }
}
```

---

## 📋 Dynamic Query 체크리스트

- [ ] BooleanBuilder 사용하는가?
- [ ] Null 파라미터 처리하는가?
- [ ] 타입 안전한가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
