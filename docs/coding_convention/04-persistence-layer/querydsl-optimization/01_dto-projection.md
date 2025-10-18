# DTO Projection - QueryDSL DTO 조회 최적화

**목적**: N+1 없이 DTO 직접 조회

**관련 문서**:
- [Dynamic Query](./02_dynamic-query.md)
- [Query UseCase](../../03-application-layer/usecase-design/02_query-usecase.md)

**필수 버전**: Java 21+, Spring Boot 3.0+, QueryDSL 5.0+

---

## 📌 핵심 원칙

### DTO Projection이란?

1. **직접 조회**: Entity 대신 DTO로 직접 조회
2. **N+1 방지**: Join으로 한 번에 조회
3. **성능 최적화**: 필요한 컬럼만 SELECT

---

## ❌ N+1 문제

```java
// ❌ Before - Entity 조회 후 변환
public List<OrderSummaryResponse> getOrders() {
    List<Order> orders = orderRepository.findAll();

    return orders.stream()
        .map(order -> {
            Customer customer = customerRepository.findById(order.getCustomerId()).get();  // N+1!
            return OrderSummaryResponse.from(order, customer);
        })
        .toList();
}
```

---

## ✅ DTO Projection 패턴

```java
package com.company.adapter.out.persistence.mysql.order.querydsl;

import com.querydsl.core.types.Projections;

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

    private final JPAQueryFactory queryFactory;

    /**
     * ✅ DTO Projection - N+1 없이 한 번에 조회
     */
    public List<OrderSummaryResponse> findOrderSummaries() {
        return queryFactory
            .select(Projections.constructor(OrderSummaryResponse.class,
                order.id,
                customer.id,
                customer.name,
                order.totalAmount,
                order.status,
                order.createdAt
            ))
            .from(order)
            .join(customer).on(order.customerId.eq(customer.id))  // ✅ Join
            .fetch();
    }

    /**
     * ✅ Record Projection (Java 21)
     */
    public List<OrderProjection> findOrderProjections() {
        return queryFactory
            .select(Projections.constructor(OrderProjection.class,
                order.id.as("orderId"),
                customer.name.as("customerName"),
                order.totalAmount.as("amount")
            ))
            .from(order)
            .join(customer).on(order.customerId.eq(customer.id))
            .fetch();
    }
}

/**
 * Projection Record
 */
public record OrderProjection(
    Long orderId,
    String customerName,
    Money amount
) {}
```

---

## 📋 DTO Projection 체크리스트

- [ ] DTO로 직접 조회하는가?
- [ ] Join으로 N+1 방지하는가?
- [ ] 필요한 컬럼만 SELECT하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
