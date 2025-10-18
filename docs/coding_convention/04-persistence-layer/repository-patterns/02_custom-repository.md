# Custom Repository - 사용자 정의 Repository

**목적**: 복잡한 쿼리 분리 패턴

**관련 문서**:
- [Aggregate Repository](./01_aggregate-repository.md)
- [QueryDSL DTO Projection](../querydsl-optimization/01_dto-projection.md)

**필수 버전**: Java 21+, Spring Data JPA 3.0+

---

## 📌 핵심 원칙

### Custom Repository란?

1. **복잡한 쿼리**: QueryDSL 사용
2. **Interface + Impl**: 명명 규칙
3. **자동 통합**: Spring Data JPA가 자동 감지

---

## ❌ 모든 쿼리를 JPA로

```java
// ❌ Before - 복잡한 쿼리를 @Query로
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.status = :status")
    List<Order> findByStatusWithCustomer(OrderStatus status);  // ❌ 유지보수 어려움
}
```

---

## ✅ Custom Repository 패턴

```java
/**
 * Custom Interface
 */
public interface OrderRepositoryCustom {

    /**
     * ✅ 복잡한 쿼리 메서드
     */
    List<OrderProjection> findOrderProjections(SearchOrdersQuery query);

    Page<Order> search(SearchOrdersQuery query);
}

/**
 * Custom Impl (QueryDSL)
 *
 * @author development-team
 * @since 1.0.0
 */
@Repository
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OrderProjection> findOrderProjections(SearchOrdersQuery query) {
        return queryFactory
            .select(Projections.constructor(OrderProjection.class,
                order.id,
                customer.name,
                order.totalAmount
            ))
            .from(order)
            .join(customer).on(order.customerId.eq(customer.id))
            .fetch();
    }
}

/**
 * JPA Repository - Custom 통합
 */
public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {
    // ✅ Spring Data JPA + Custom 자동 통합
}
```

---

## 📋 Custom Repository 체크리스트

- [ ] Interface + Impl 명명 규칙 준수하는가?
- [ ] QueryDSL 사용하는가?
- [ ] JPA Repository와 통합되어 있는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
