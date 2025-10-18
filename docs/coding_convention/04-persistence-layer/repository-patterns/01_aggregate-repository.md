# Aggregate Repository - Aggregate Root 저장소

**목적**: Aggregate 단위 저장/조회 패턴

**관련 문서**:
- [Aggregate Root Design](../../02-domain-layer/aggregate-design/02_aggregate-root-design.md)
- [Custom Repository](./02_custom-repository.md)

**필수 버전**: Java 21+, Spring Data JPA 3.0+

---

## 📌 핵심 원칙

### Aggregate Repository란?

1. **Aggregate Root만**: Root만 Repository 가짐
2. **도메인 인터페이스**: Port 패턴
3. **ID로 조회**: findById() 기본

---

## ❌ Entity별 Repository

```java
// ❌ Before - 모든 Entity에 Repository
public interface OrderRepository extends JpaRepository<Order, Long> {}
public interface OrderLineItemRepository extends JpaRepository<OrderLineItem, Long> {}  // ❌
public interface PaymentRepository extends JpaRepository<Payment, Long> {}  // ❌
```

---

## ✅ Aggregate Repository 패턴

```java
package com.company.domain.order.port.out;

/**
 * Order Repository (Port)
 *
 * @author development-team
 * @since 1.0.0
 */
public interface OrderRepository {

    /**
     * ✅ Aggregate Root 저장
     */
    Order save(Order order);

    /**
     * ✅ Aggregate Root 조회
     */
    Optional<Order> findById(OrderId orderId);

    /**
     * ✅ Aggregate Root 삭제
     */
    void delete(Order order);

    /**
     * ✅ 도메인 조건으로 조회
     */
    List<Order> findByCustomerId(CustomerId customerId);
}

/**
 * JPA Repository (Adapter)
 */
@Repository
public interface OrderJpaRepository extends JpaRepository<Order, Long>, OrderRepository {
    // ✅ Spring Data JPA가 구현체 자동 생성
}
```

---

## 📋 Aggregate Repository 체크리스트

- [ ] Aggregate Root만 Repository 가지는가?
- [ ] 도메인 인터페이스로 정의되어 있는가?
- [ ] ID로 조회하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
