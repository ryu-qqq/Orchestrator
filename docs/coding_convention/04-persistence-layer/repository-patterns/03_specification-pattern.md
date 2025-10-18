# Specification Pattern - 조건 조합 패턴

**목적**: 재사용 가능한 쿼리 조건 패턴

**관련 문서**:
- [Custom Repository](./02_custom-repository.md)
- [Dynamic Query](../querydsl-optimization/02_dynamic-query.md)

**필수 버전**: Java 21+, Spring Data JPA 3.0+

---

## 📌 핵심 원칙

### Specification이란?

1. **조건 조합**: AND, OR로 조건 조합
2. **재사용 가능**: 쿼리 조건 재사용
3. **타입 안전**: JPA Criteria API 기반

---

## ❌ 중복된 쿼리 조건

```java
// ❌ Before - 조건이 중복됨
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerIdAndStatus(CustomerId customerId, OrderStatus status);

    List<Order> findByCustomerIdAndCreatedAtAfter(CustomerId customerId, LocalDateTime createdAt);

    // ⚠️ customerId 조건 중복
}
```

---

## ✅ Specification 패턴

```java
package com.company.adapter.out.persistence.mysql.order.spec;

import org.springframework.data.jpa.domain.Specification;

/**
 * Order Specifications
 *
 * 📁 Package: adapter.out.persistence-mysql.order.spec
 *
 * @author development-team
 * @since 1.0.0
 */
public class OrderSpecifications {

    /**
     * ✅ 재사용 가능한 조건
     */
    public static Specification<Order> hasCustomerId(CustomerId customerId) {
        return (root, query, cb) ->
            cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdAfter(LocalDateTime dateTime) {
        return (root, query, cb) ->
            cb.greaterThanOrEqualTo(root.get("createdAt"), dateTime);
    }
}

/**
 * Repository - Specification 사용
 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
}

/**
 * Service - 조건 조합
 */
@Service
public class OrderQueryService {

    public List<Order> search(CustomerId customerId, OrderStatus status, LocalDateTime since) {
        Specification<Order> spec = Specification.where(hasCustomerId(customerId))
            .and(hasStatus(status))
            .and(createdAfter(since));

        return orderRepository.findAll(spec);  // ✅ 조건 조합
    }
}
```

---

## 📋 Specification 체크리스트

- [ ] 재사용 가능한 조건으로 분리되어 있는가?
- [ ] AND/OR로 조합 가능한가?
- [ ] JpaSpecificationExecutor 상속하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
