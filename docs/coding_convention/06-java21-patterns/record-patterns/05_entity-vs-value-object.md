# Entity vs Value Object with Records - JPA Entity와 Domain Record 구분

**목적**: JPA Entity (Long FK)와 Domain Value Object (Record)의 명확한 구분 및 변환 패턴

**관련 문서**:
- [Long FK Strategy](../../../04-persistence-layer/jpa-entity-design/01_long-fk-strategy.md)
- [Aggregate Boundaries](../../../02-domain-layer/aggregate-design/01_aggregate-boundaries.md)
- [Value Objects with Records](./02_value-objects-with-records.md)
- [Repository Pattern](../../../04-persistence-layer/repository-pattern/01_repository-implementation.md)

**필수 버전**: Java 21+, Spring Boot 3.0+, Spring Data JPA 3.0+

---

## 📌 핵심 원칙

### Entity vs Value Object 구분

1. **Entity (JPA)**: 변경 가능, 식별자 중심, Long FK만 사용
2. **Value Object (Domain)**: 불변, 값 중심, Record 사용
3. **변환**: Repository Adapter에서 Entity ↔ Domain Record 변환
4. **격리**: Domain은 JPA 의존성 없음 (Framework 독립)

---

## 🏗️ 아키텍처 레이어별 책임

```
┌──────────────────────────────────────────────────┐
│  Domain Layer (Framework 독립)                    │
│  - Domain Record (Value Object, Aggregate)       │
│  - 불변 객체, 비즈니스 로직                         │
│  - JPA 의존성 없음                                 │
└──────────────────────────────────────────────────┘
                      ↕ (변환)
┌──────────────────────────────────────────────────┐
│  Persistence Layer (JPA Adapter)                 │
│  - JPA Entity (@Entity, Long FK만 사용)          │
│  - 변경 가능, 영속성 관리                           │
│  - Repository에서 Domain ↔ Entity 변환            │
└──────────────────────────────────────────────────┘
```

---

## ❌ 안티패턴 - ORM Association 사용

### 문제점: JPA Relationship Annotations

```java
// ❌ Before - JPA Association (절대 금지!)
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ❌ @ManyToOne - Law of Demeter 위반!
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    // ❌ @OneToMany - N+1 쿼리 문제!
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderLineItemEntity> items;

    // ❌ Getter 체이닝 가능
    public CustomerEntity getCustomer() {
        return customer;
    }

    // ❌ 사용 예시 - Law of Demeter 위반
    // order.getCustomer().getAddress().getZipCode()
}

// ❌ Before - Domain도 JPA Entity
@Entity
public class Order {  // ❌ Domain이 JPA에 의존
    @Id
    private Long id;

    @ManyToOne
    private Customer customer;  // ❌ Association
}
```

**문제점**:
- ❌ Law of Demeter 위반 (`order.getCustomer().getAddress()`)
- ❌ N+1 쿼리 문제
- ❌ Domain이 Framework에 의존
- ❌ 불변성 보장 어려움 (Entity는 변경 가능)

---

## ✅ 권장 패턴 - Long FK + Domain Record

### 패턴 1: JPA Entity (Long FK만 사용)

```java
package com.company.application.order.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order JPA Entity
 *
 * - Persistence Layer 전용 (Domain과 분리)
 * - Long FK만 사용 (Association 금지)
 * - 변경 가능 (Mutable)
 * - Repository Adapter에서만 사용
 *
 * @author development-team
 * @since 1.0.0
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Long FK만 사용 (Association 금지)
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ✅ JPA 기본 생성자 (protected)
    protected OrderEntity() {}

    // ✅ 정적 팩토리 메서드
    public static OrderEntity create(
        Long customerId,
        String status,
        Long totalAmount,
        String notes
    ) {
        OrderEntity entity = new OrderEntity();
        entity.customerId = customerId;
        entity.status = status;
        entity.totalAmount = totalAmount;
        entity.notes = notes;
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        return entity;
    }

    // ✅ Getter/Setter (JPA Entity는 변경 가능)
    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public Long getTotalAmount() { return totalAmount; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
        this.updatedAt = Instant.now();
    }
}

/**
 * OrderLineItem JPA Entity
 */
@Entity
@Table(name = "order_line_items")
public class OrderLineItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Long FK (orderId)
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    // ✅ Long FK (productId)
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "subtotal", nullable = false)
    private Long subtotal;

    protected OrderLineItemEntity() {}

    public static OrderLineItemEntity create(
        Long orderId,
        Long productId,
        Integer quantity,
        Long unitPrice
    ) {
        OrderLineItemEntity entity = new OrderLineItemEntity();
        entity.orderId = orderId;
        entity.productId = productId;
        entity.quantity = quantity;
        entity.unitPrice = unitPrice;
        entity.subtotal = quantity * unitPrice;
        return entity;
    }

    // Getters/Setters...
}
```

**핵심 포인트**:
- ✅ `customerId`, `productId`: Long 타입 (Association 금지)
- ✅ `@OneToMany`는 예외적으로 허용 (자식 Entity 라이프사이클 관리)
- ✅ Entity는 변경 가능 (Mutable)
- ✅ Domain과 완전히 분리

---

### 패턴 2: Domain Record (불변 Value Object)

```java
package com.company.domain.order;

import java.time.Instant;
import java.util.List;

/**
 * Order Domain Record (Aggregate Root)
 *
 * - Domain Layer 전용 (JPA 의존성 없음)
 * - 불변 객체 (Record)
 * - 비즈니스 로직 포함
 * - Framework 독립
 *
 * @author development-team
 * @since 1.0.0
 */
public record Order(
    Long id,
    Long customerId,
    List<OrderLineItem> items,
    OrderStatus status,
    Long totalAmount,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * ✅ Compact Constructor - 불변성 보장
     */
    public Order {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("Invalid customer ID");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }

        // ✅ 방어적 복사 (불변성)
        items = List.copyOf(items);
    }

    /**
     * ✅ 정적 팩토리 메서드
     */
    public static Order create(
        Long customerId,
        List<OrderLineItem> items,
        String notes
    ) {
        Long totalAmount = items.stream()
            .mapToLong(OrderLineItem::subtotal)
            .sum();

        return new Order(
            null,  // ID는 Repository에서 할당
            customerId,
            items,
            OrderStatus.PENDING,
            totalAmount,
            notes,
            Instant.now(),
            Instant.now()
        );
    }

    /**
     * ✅ 비즈니스 로직 - 주문 승인
     *
     * - Record는 불변이므로 새로운 인스턴스 반환
     */
    public Order approve() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be approved");
        }

        return new Order(
            this.id,
            this.customerId,
            this.items,
            OrderStatus.CONFIRMED,
            this.totalAmount,
            this.notes,
            this.createdAt,
            Instant.now()
        );
    }

    /**
     * ✅ 비즈니스 로직 - 주문 취소
     */
    public Order cancel(String reason) {
        if (this.status == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivered orders cannot be cancelled");
        }

        return new Order(
            this.id,
            this.customerId,
            this.items,
            OrderStatus.CANCELLED,
            this.totalAmount,
            reason,
            this.createdAt,
            Instant.now()
        );
    }

    /**
     * ✅ Law of Demeter - Getter 체이닝 방지
     *
     * - order.getCustomerZipCode() 제공
     * - order.getCustomer().getAddress().getZipCode() 방지
     */
    public Money getTotalAmountAsMoney() {
        return new Money(this.totalAmount);
    }
}

/**
 * OrderLineItem Domain Record
 */
public record OrderLineItem(
    Long id,
    Long productId,
    Integer quantity,
    Long unitPrice,
    Long subtotal
) {
    public OrderLineItem {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid quantity");
        }

        if (unitPrice == null || unitPrice < 0) {
            throw new IllegalArgumentException("Invalid unit price");
        }
    }

    public static OrderLineItem create(Long productId, Integer quantity, Long unitPrice) {
        return new OrderLineItem(
            null,  // ID는 Repository에서 할당
            productId,
            quantity,
            unitPrice,
            quantity * unitPrice
        );
    }
}

/**
 * OrderStatus Enum
 */
public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}
```

**핵심 포인트**:
- ✅ Record로 불변성 보장
- ✅ JPA 어노테이션 없음 (Framework 독립)
- ✅ 비즈니스 로직 포함 (`approve()`, `cancel()`)
- ✅ Law of Demeter 준수 (`getTotalAmountAsMoney()`)

---

### 패턴 3: Repository Adapter (Entity ↔ Domain 변환)

```java
package com.company.application.order.adapter.out.persistence;

import com.company.application.order.port.out.SaveOrderPort;
import com.company.application.order.port.out.LoadOrderPort;
import com.company.application.order.adapter.out.persistence.entity.OrderEntity;
import com.company.application.order.adapter.out.persistence.entity.OrderLineItemEntity;
import com.company.domain.order.Order;
import com.company.domain.order.OrderLineItem;
import com.company.domain.order.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Order Repository Adapter
 *
 * - Entity ↔ Domain Record 변환 책임
 * - JPA Repository 호출
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderRepositoryAdapter implements SaveOrderPort, LoadOrderPort {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderLineItemJpaRepository lineItemJpaRepository;

    public OrderRepositoryAdapter(
        OrderJpaRepository orderJpaRepository,
        OrderLineItemJpaRepository lineItemJpaRepository
    ) {
        this.orderJpaRepository = orderJpaRepository;
        this.lineItemJpaRepository = lineItemJpaRepository;
    }

    /**
     * ✅ Domain Record → JPA Entity → 저장 → Domain Record
     */
    @Override
    public Order save(Order order) {
        // 1. Domain Record → JPA Entity
        OrderEntity entity = toEntity(order);

        // 2. JPA 저장
        OrderEntity savedEntity = orderJpaRepository.save(entity);

        // 3. Line Items 저장
        order.items().forEach(item -> {
            OrderLineItemEntity itemEntity = OrderLineItemEntity.create(
                savedEntity.getId(),
                item.productId(),
                item.quantity(),
                item.unitPrice()
            );
            lineItemJpaRepository.save(itemEntity);
        });

        // 4. JPA Entity → Domain Record
        return toDomain(savedEntity, order.items());
    }

    /**
     * ✅ JPA Entity → Domain Record
     */
    @Override
    public Optional<Order> load(Long orderId) {
        return orderJpaRepository.findById(orderId)
            .map(entity -> {
                // Line Items 조회
                List<OrderLineItemEntity> itemEntities =
                    lineItemJpaRepository.findByOrderId(orderId);

                List<OrderLineItem> items = itemEntities.stream()
                    .map(this::toLineItemDomain)
                    .toList();

                return toDomain(entity, items);
            });
    }

    /**
     * ✅ Domain Record → JPA Entity
     */
    private OrderEntity toEntity(Order order) {
        if (order.id() == null) {
            // 신규 생성
            return OrderEntity.create(
                order.customerId(),
                order.status().name(),
                order.totalAmount(),
                order.notes()
            );
        } else {
            // 기존 엔티티 업데이트
            OrderEntity entity = orderJpaRepository.findById(order.id())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

            entity.setStatus(order.status().name());
            entity.setTotalAmount(order.totalAmount());

            return entity;
        }
    }

    /**
     * ✅ JPA Entity → Domain Record
     */
    private Order toDomain(OrderEntity entity, List<OrderLineItem> items) {
        return new Order(
            entity.getId(),
            entity.getCustomerId(),
            items,
            OrderStatus.valueOf(entity.getStatus()),
            entity.getTotalAmount(),
            entity.getNotes(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * ✅ OrderLineItemEntity → OrderLineItem Domain
     */
    private OrderLineItem toLineItemDomain(OrderLineItemEntity entity) {
        return new OrderLineItem(
            entity.getId(),
            entity.getProductId(),
            entity.getQuantity(),
            entity.getUnitPrice(),
            entity.getSubtotal()
        );
    }
}
```

**핵심 포인트**:
- ✅ `toEntity()`: Domain Record → JPA Entity
- ✅ `toDomain()`: JPA Entity → Domain Record
- ✅ Repository Adapter에서만 변환 책임
- ✅ Domain은 JPA 의존성 없음

---

## 🎯 비교표: Entity vs Value Object

| 항목 | JPA Entity | Domain Record |
|------|-----------|---------------|
| **목적** | 영속성 관리 | 비즈니스 로직 |
| **레이어** | Persistence Layer | Domain Layer |
| **타입** | Class | Record |
| **변경 가능** | Mutable (O) | Immutable (X) |
| **어노테이션** | `@Entity`, `@Table` | 없음 (Framework 독립) |
| **관계 표현** | Long FK만 | Long 타입 또는 Typed ID Record |
| **비즈니스 로직** | 없음 (Anemic) | 있음 (Rich Domain) |
| **변환 책임** | Repository Adapter | - |
| **예시** | `OrderEntity` | `Order` |

---

## 🔧 고급 패턴

### 패턴 1: Typed ID Record (ID 타입 안전성)

```java
/**
 * ✅ Typed ID Record
 *
 * - Long 대신 타입 안전한 ID 사용
 * - 컴파일 타임 타입 검증
 */
public record OrderId(Long value) {
    public OrderId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Invalid OrderId");
        }
    }

    public static OrderId of(Long value) {
        return new OrderId(value);
    }
}

public record CustomerId(Long value) {
    public CustomerId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Invalid CustomerId");
        }
    }

    public static CustomerId of(Long value) {
        return new CustomerId(value);
    }
}

/**
 * ✅ Domain Record with Typed ID
 */
public record Order(
    OrderId id,
    CustomerId customerId,  // ✅ Typed ID
    List<OrderLineItem> items,
    OrderStatus status,
    Money totalAmount,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
    // ✅ 컴파일 타임 타입 검증
    // order.customerId().value() → Long
}

/**
 * ✅ JPA Entity는 여전히 Long 사용
 */
@Entity
public class OrderEntity {
    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;  // ✅ Long FK
}

/**
 * ✅ Repository Adapter에서 변환
 */
@Component
public class OrderRepositoryAdapter {

    private Order toDomain(OrderEntity entity, List<OrderLineItem> items) {
        return new Order(
            OrderId.of(entity.getId()),  // ✅ Long → OrderId
            CustomerId.of(entity.getCustomerId()),  // ✅ Long → CustomerId
            items,
            OrderStatus.valueOf(entity.getStatus()),
            new Money(entity.getTotalAmount()),
            entity.getNotes(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private OrderEntity toEntity(Order order) {
        return OrderEntity.create(
            order.customerId().value(),  // ✅ CustomerId → Long
            order.status().name(),
            order.totalAmount().value(),
            order.notes()
        );
    }
}
```

---

### 패턴 2: Projection (조회 전용 Record)

```java
/**
 * ✅ Projection Record (조회 전용)
 *
 * - 복잡한 조회는 Projection 사용
 * - Domain Record와 별도
 */
public record OrderSummary(
    Long orderId,
    String customerName,
    String customerEmail,
    Integer itemCount,
    Long totalAmount,
    String status,
    Instant createdAt
) {}

/**
 * ✅ Repository Adapter에서 Projection 조회
 */
@Component
public class OrderQueryAdapter {

    private final JdbcTemplate jdbcTemplate;

    public List<OrderSummary> findOrderSummaries(Long customerId) {
        String sql = """
            SELECT o.id, c.name, c.email,
                   COUNT(li.id) as item_count,
                   o.total_amount, o.status, o.created_at
            FROM orders o
            JOIN customers c ON o.customer_id = c.id
            LEFT JOIN order_line_items li ON o.id = li.order_id
            WHERE o.customer_id = ?
            GROUP BY o.id, c.name, c.email, o.total_amount, o.status, o.created_at
            """;

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> new OrderSummary(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getInt("item_count"),
                rs.getLong("total_amount"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            ),
            customerId
        );
    }
}
```

---

## 📋 Entity vs Record 체크리스트

### Entity (JPA)
- [ ] **Long FK만 사용**: `@ManyToOne`, `@OneToMany` 등 Association 금지
- [ ] **Persistence Layer**: `adapter/out/persistence/entity/` 패키지
- [ ] **변경 가능**: Getter/Setter 제공
- [ ] **JPA 어노테이션**: `@Entity`, `@Table`, `@Column`

### Value Object (Domain Record)
- [ ] **불변 객체**: Record 사용
- [ ] **Domain Layer**: `domain/` 패키지
- [ ] **Framework 독립**: JPA 어노테이션 없음
- [ ] **비즈니스 로직**: Rich Domain Model

### Repository Adapter
- [ ] **변환 책임**: `toEntity()`, `toDomain()` 메서드 제공
- [ ] **Long FK ↔ Typed ID**: 필요 시 Typed ID Record 변환
- [ ] **Projection**: 복잡한 조회는 별도 Projection Record 사용

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
