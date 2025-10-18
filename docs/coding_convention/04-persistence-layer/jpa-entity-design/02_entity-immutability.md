# Entity Immutability (Entity 불변성)

**Priority**: 🔴 CRITICAL
**Validation**: ArchUnit `PersistenceLayerTest.java`

---

## ⚠️ 중요: Entity vs Domain Model 구분

**헥사고날 아키텍처**에서는 **JPA Entity**와 **Domain Model**을 명확히 분리합니다:

| 구분 | JPA Entity | Domain Model |
|------|-----------|--------------|
| **역할** | DB 매핑만 담당 | 비즈니스 로직 포함 |
| **위치** | `adapter/out/persistence-mysql/{context}/entity/` | `domain/{context}/` |
| **특징** | JPA 어노테이션, Long FK, Getter만 | Framework 독립, 비즈니스 메서드 |
| **변경** | Mapper를 통해서만 | 비즈니스 메서드로 |

**변환 흐름**:
```
Domain (Order) ↔ Mapper ↔ Entity (OrderJpaEntity)
```

---

## 📋 핵심 원칙

JPA Entity는 **DB 매핑만 담당**하며 **가능한 한 불변(Immutable)**으로 설계합니다.

1. **모든 필드는 `private final`** (JPA 필수 필드 제외)
2. **Setter 메서드 절대 금지**
3. **비즈니스 메서드 금지** - 비즈니스 로직은 Domain Model에
4. **Protected 기본 생성자** (JPA Proxy 생성용)
5. **Static Factory Method** - `create()`, `reconstitute()`

---

## 🚨 왜 Entity에 Setter를 금지하는가?

### 문제점 매트릭스

| 문제점 | 설명 | 영향 |
|--------|------|------|
| **캡슐화 파괴** | 외부에서 무분별한 상태 변경 | 비즈니스 규칙 우회 가능 |
| **불변식(Invariant) 위반** | 검증 없이 상태 변경 | 데이터 무결성 파괴 |
| **추적 불가능** | 어디서 상태가 바뀌는지 불명확 | 디버깅 어려움 |
| **동시성 문제** | Thread-safe하지 않음 | 경쟁 조건 발생 |
| **테스트 복잡도** | 상태 조합 폭발 | 테스트 케이스 급증 |

---

## ✅ Entity 불변성 설계 패턴

### 패턴 1: 필드 선언 (private final)

#### ❌ Bad - Setter 제공

```java
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;  // ❌ Setter로 변경 가능

    @Column(nullable = false)
    private BigDecimal totalAmount;

    // ❌ 문제: Setter로 무분별한 상태 변경
    public void setStatus(OrderStatus status) {
        this.status = status;  // 비즈니스 규칙 우회!
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;  // 검증 없이 변경!
    }
}

// ❌ 사용 코드 - 비즈니스 규칙 우회
OrderEntity order = orderRepository.findById(orderId);
order.setStatus(OrderStatus.SHIPPED);  // 결제 확인 없이 배송 상태 변경!
order.setTotalAmount(BigDecimal.ZERO);  // 금액 무단 변경!
```

**문제점**:
- 비즈니스 규칙 없이 상태 변경 가능
- 결제 미완료 상태에서 배송 시작 가능
- 주문 금액 조작 가능

---

#### ✅ Good - JPA Entity (DB 매핑만)

```java
/**
 * Order JPA Entity (Persistence Layer)
 *
 * ✅ DB 매핑만 담당
 * ❌ 비즈니스 로직 없음!
 */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ final 필드 - 생성 후 변경 불가
    @Column(nullable = false)
    private final Long userId;

    // ❌ JPA는 final 필드 업데이트 불가
    //    → 상태 변경 필요 필드는 final 제외
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private final BigDecimal totalAmount;

    @Column(nullable = false)
    private final LocalDateTime createdAt;

    // ✅ Protected 기본 생성자 (JPA Proxy 생성용)
    protected OrderJpaEntity() {
        this.userId = null;
        this.totalAmount = null;
        this.createdAt = null;
    }

    // ✅ Private 전체 생성자 (Factory Method에서만 사용)
    private OrderJpaEntity(
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
    ) {
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    // ✅ Static Factory Method - 새 Entity 생성
    public static OrderJpaEntity create(Long userId, BigDecimal totalAmount) {
        // ⚠️ 최소 검증만 (비즈니스 검증은 Domain Layer에서)
        if (userId == null || totalAmount == null) {
            throw new IllegalArgumentException("Required fields must not be null");
        }

        return new OrderJpaEntity(
            userId,
            OrderStatus.PENDING,
            totalAmount,
            LocalDateTime.now()
        );
    }

    // ✅ Static Factory Method - DB에서 재구성
    public static OrderJpaEntity reconstitute(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
    ) {
        OrderJpaEntity entity = new OrderJpaEntity(userId, status, totalAmount, createdAt);
        // JPA가 id를 자동 설정하므로 일반적으로 불필요
        return entity;
    }

    // ✅ Getter만 제공 (비즈니스 메서드 없음!)
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ❌ confirm(), ship(), cancel() 같은 비즈니스 메서드 없음!
    //    → 비즈니스 로직은 Domain Model에서 처리
}
```

#### ✅ Good - Domain Model (비즈니스 로직)

```java
/**
 * Order Domain Model (Domain Layer)
 *
 * ✅ 비즈니스 로직 포함
 * ✅ Framework 독립적
 */
public class Order {

    private final OrderId id;
    private final UserId userId;
    private OrderStatus status;
    private final Money totalAmount;
    private final LocalDateTime createdAt;

    // ✅ 비즈니스 메서드 - 명시적 상태 변경
    public void confirm() {
        // 비즈니스 규칙 검증
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                "Order can only be confirmed from PENDING status, current: " + this.status
            );
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void ship() {
        // 비즈니스 규칙 검증
        if (this.status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                "Order can only be shipped from CONFIRMED status, current: " + this.status
            );
        }
        this.status = OrderStatus.SHIPPED;
    }

    public void cancel() {
        // 비즈니스 규칙 검증
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                "Cannot cancel order in status: " + this.status
            );
        }
        this.status = OrderStatus.CANCELLED;
    }

    // Getter
    public OrderId getId() { return id; }
    public UserId getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public Money getTotalAmount() { return totalAmount; }
}
```

**장점**:
- 관심사 분리 (SRP): Entity는 DB 매핑, Domain은 비즈니스 로직
- Framework 독립성: Domain Model은 순수 Java
- 테스트 용이성: Domain Model은 프레임워크 없이 테스트
- 명확한 책임: 비즈니스 규칙은 Domain에서만 관리

---

### 패턴 2: final vs Non-final 필드 구분

#### 규칙

| 필드 유형 | final 여부 | 이유 |
|-----------|-----------|------|
| **식별자 (PK)** | ❌ Non-final | JPA가 생성 후 자동 설정 |
| **FK (외래키)** | ✅ final | 생성 후 변경 불가 |
| **상태 (Status)** | ❌ Non-final | 생명주기 동안 변경 필요 |
| **금액, 수량** | ✅ final | 생성 후 변경 불가 |
| **생성 시각** | ✅ final | 생성 후 변경 불가 |
| **수정 시각** | ❌ Non-final | 변경될 때마다 업데이트 |

#### 예시: Product JPA Entity (DB 매핑만)

```java
/**
 * Product JPA Entity (Persistence Layer)
 *
 * ✅ DB 매핑만 담당
 * ❌ 비즈니스 로직 없음!
 */
@Entity
@Table(name = "products")
public class ProductJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // ❌ Non-final - JPA가 자동 설정

    @Column(nullable = false)
    private final String name;  // ✅ final - 상품명 불변

    @Column(nullable = false)
    private final BigDecimal basePrice;  // ✅ final - 기본 가격 불변

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;  // ❌ Non-final - 상태 변경 가능

    @Column(nullable = false)
    private Integer stockQuantity;  // ❌ Non-final - 재고 변경 가능

    @Column(nullable = false)
    private final LocalDateTime createdAt;  // ✅ final - 생성 시각 불변

    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;  // ❌ Non-final - 수정 시각 변경

    protected ProductJpaEntity() {
        this.name = null;
        this.basePrice = null;
        this.createdAt = null;
    }

    private ProductJpaEntity(String name, BigDecimal basePrice, Integer initialStock) {
        this.name = name;
        this.basePrice = basePrice;
        this.status = ProductStatus.AVAILABLE;
        this.stockQuantity = initialStock;
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
    }

    public static ProductJpaEntity create(String name, BigDecimal basePrice, Integer initialStock) {
        // ⚠️ 최소 검증만 (비즈니스 검증은 Domain Layer에서)
        if (name == null || basePrice == null || initialStock == null) {
            throw new IllegalArgumentException("Required fields must not be null");
        }
        return new ProductJpaEntity(name, basePrice, initialStock);
    }

    // ✅ Getter만 제공 (비즈니스 메서드 없음!)
    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getBasePrice() { return basePrice; }
    public ProductStatus getStatus() { return status; }
    public Integer getStockQuantity() { return stockQuantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }

    // ❌ decreaseStock(), increaseStock() 같은 비즈니스 메서드 없음!
    //    → 비즈니스 로직은 Domain Model에서 처리
}
```

#### 예시: Product Domain Model (비즈니스 로직)

```java
/**
 * Product Domain Model (Domain Layer)
 *
 * ✅ 비즈니스 로직 포함
 * ✅ Framework 독립적
 */
public class Product {

    private final ProductId id;
    private final ProductName name;
    private final Money basePrice;
    private ProductStatus status;
    private Stock stock;
    private final LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    // ✅ 비즈니스 메서드 - 재고 감소
    public void decreaseStock(int quantity) {
        // 비즈니스 규칙 검증
        if (quantity <= 0) {
            throw new IllegalArgumentException("Decrease quantity must be positive");
        }
        if (this.stock.getQuantity() < quantity) {
            throw new InsufficientStockException(
                this.id, quantity, this.stock.getQuantity()
            );
        }
        this.stock = this.stock.decrease(quantity);
        this.lastModifiedAt = LocalDateTime.now();
    }

    // ✅ 비즈니스 메서드 - 재고 증가
    public void increaseStock(int quantity) {
        // 비즈니스 규칙 검증
        if (quantity <= 0) {
            throw new IllegalArgumentException("Increase quantity must be positive");
        }
        this.stock = this.stock.increase(quantity);
        this.lastModifiedAt = LocalDateTime.now();
    }

    // ✅ 비즈니스 메서드 - 품절 처리
    public void markAsOutOfStock() {
        // 비즈니스 규칙 검증
        if (this.stock.isAvailable()) {
            throw new IllegalStateException("Cannot mark as out of stock when stock is available");
        }
        this.status = ProductStatus.OUT_OF_STOCK;
        this.lastModifiedAt = LocalDateTime.now();
    }

    // Getter
    public ProductId getId() { return id; }
    public ProductName getName() { return name; }
    public Money getBasePrice() { return basePrice; }
    public ProductStatus getStatus() { return status; }
    public Stock getStock() { return stock; }
}
```

---

### 패턴 3: Collection 필드 처리

**원칙**: Entity에서 Collection 필드는 **최대한 피하되**, 불가피하면 **Unmodifiable Collection**으로 반환

#### ❌ Bad - Collection 직접 노출

```java
@Entity
public class OrderEntity {
    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderItemEntity> items;  // ❌ JPA 관계 금지!

    // ❌ Mutable Collection 반환
    public List<OrderItemEntity> getItems() {
        return items;  // 외부에서 추가/삭제 가능!
    }
}

// ❌ 사용 코드 - 외부에서 조작
OrderEntity order = orderRepository.findById(orderId);
order.getItems().clear();  // 전체 삭제!
order.getItems().add(new OrderItemEntity());  // 무단 추가!
```

**문제점**:
- 외부에서 Collection 조작 가능
- 비즈니스 규칙 우회
- 불변식 위반

---

#### ✅ Good - Long FK 전략 + Application Layer 조합

```java
/**
 * Order Entity (Collection 없음)
 */
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private final Long userId;

    // ✅ OrderItem은 별도 Entity
    //    - 여기서 참조하지 않음
    //    - Application Layer에서 명시적 조회

    protected OrderEntity() {
        this.userId = null;
    }

    private OrderEntity(Long userId) {
        this.userId = userId;
    }

    public static OrderEntity create(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        return new OrderEntity(userId);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
}

/**
 * OrderItem Entity (별도 관리)
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private final Long orderId;  // ✅ Long FK

    @Column(nullable = false)
    private final Long productId;

    @Column(nullable = false)
    private final Integer quantity;

    protected OrderItemEntity() {
        this.orderId = null;
        this.productId = null;
        this.quantity = null;
    }

    private OrderItemEntity(Long orderId, Long productId, Integer quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public static OrderItemEntity create(Long orderId, Long productId, Integer quantity) {
        if (orderId == null || productId == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid order item parameters");
        }
        return new OrderItemEntity(orderId, productId, quantity);
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
}

/**
 * Application Layer - 명시적 조합
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrderWithItemsService {
    private final LoadOrderPort loadOrderPort;
    private final LoadOrderItemsPort loadOrderItemsPort;

    public OrderWithItemsResult execute(OrderId orderId) {
        // 1. Order 조회
        Order order = loadOrderPort.loadById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 2. OrderItem 조회 (별도 쿼리)
        List<OrderItem> items = loadOrderItemsPort.loadByOrderId(orderId);

        // 3. 조합
        return OrderWithItemsResult.of(order, items);
    }
}
```

**장점**:
- Collection 노출 없음
- 비즈니스 규칙 강제
- 명시적 데이터 로딩

---

## 📐 Static Factory Method 패턴

### create() vs reconstitute()

| 메서드 | 용도 | ID 설정 | 검증 |
|--------|------|---------|------|
| **create()** | 새 Entity 생성 | ❌ 없음 (JPA 자동) | ✅ 비즈니스 규칙 |
| **reconstitute()** | DB에서 재구성 | ✅ 있음 | ❌ 최소 검증 |

### create() - 새 Entity 생성

```java
/**
 * 비즈니스 로직으로 새 Entity 생성
 */
public static OrderEntity create(Long userId, BigDecimal totalAmount) {
    // ✅ 비즈니스 규칙 검증
    if (userId == null) {
        throw new IllegalArgumentException("User ID must not be null");
    }
    if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Total amount must be positive");
    }

    // ✅ 초기 상태 설정
    return new OrderEntity(
        userId,
        OrderStatus.PENDING,  // 항상 PENDING으로 시작
        totalAmount,
        LocalDateTime.now()
    );
}
```

### reconstitute() - DB에서 재구성

```java
/**
 * DB 데이터로 Entity 재구성 (Repository에서 사용)
 */
public static OrderEntity reconstitute(
    Long id,
    Long userId,
    OrderStatus status,
    BigDecimal totalAmount,
    LocalDateTime createdAt
) {
    // ✅ Null 체크만 (비즈니스 규칙은 이미 검증됨)
    if (id == null || userId == null) {
        throw new IllegalArgumentException("ID fields must not be null");
    }

    // ✅ 모든 상태를 그대로 재구성
    OrderEntity entity = new OrderEntity(userId, status, totalAmount, createdAt);
    // JPA가 id를 자동 설정하므로 일반적으로 불필요
    // 필요 시 Reflection 사용
    return entity;
}
```

---

## 🔍 ArchUnit 검증

### Entity Setter 금지 규칙

```java
// application/src/test/java/architecture/PersistenceLayerTest.java

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@AnalyzeClasses(packages = "com.company.template")
public class PersistenceLayerTest {

    /**
     * Entity는 Setter 메서드를 가질 수 없다
     */
    @ArchTest
    static final ArchRule entities_should_not_have_setters =
        methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .and().arePublic()
            .should().notHaveNameMatching("set[A-Z].*")
            .because("Entity fields should be immutable, use business methods instead of setters");

    /**
     * Entity 필드는 private이어야 한다
     */
    @ArchTest
    static final ArchRule entity_fields_should_be_private =
        fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .and().areNotStatic()
            .should().bePrivate()
            .because("Entity fields must be private for encapsulation");

    /**
     * Entity는 public 생성자를 가질 수 없다
     */
    @ArchTest
    static final ArchRule entities_should_not_have_public_constructors =
        constructors()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .should().notBePublic()
            .because("Entities should only be created via static factory methods");

    /**
     * Entity는 create() 또는 reconstitute() 메서드를 가져야 한다
     */
    @ArchTest
    static final ArchRule entities_should_have_factory_methods =
        classes()
            .that().areAnnotatedWith(Entity.class)
            .should().haveOnlyPrivateConstructors()
            .orShould().haveOnlyProtectedConstructors()
            .andShould().declareMethodMatching(method ->
                method.getName().equals("create") && method.isStatic()
            )
            .because("Entities should provide static factory methods for creation");
}
```

---

## ✅ 체크리스트

코드 작성 전:
- [ ] Entity 필드는 가능한 한 `private final`
- [ ] Setter 메서드 절대 금지
- [ ] Public 생성자 금지
- [ ] Protected 기본 생성자 제공 (JPA용)
- [ ] Private 전체 생성자 제공 (Factory Method용)
- [ ] Static Factory Method 제공 (`create()`, `reconstitute()`)
- [ ] 상태 변경은 비즈니스 메서드로만

커밋 전:
- [ ] ArchUnit 테스트 통과 (`PersistenceLayerTest.java`)
- [ ] Setter 메서드 없음
- [ ] Factory Method 구현됨
- [ ] 비즈니스 규칙 검증 포함

---

## 📚 관련 가이드

**전제 조건**:
- [Long FK Strategy](./01_long-fk-strategy.md) - JPA 관계 어노테이션 금지

**연관 패턴**:
- [N+1 Prevention](./03_n-plus-one-prevention.md) - 성능 최적화
- [Repository SRP](../repository-srp/) - 단일 Entity 의존

**심화 학습**:
- [Domain Model Encapsulation](../../02-domain-layer/domain-encapsulation/) - 비즈니스 로직 캡슐화
- [Value Objects](../../02-domain-layer/value-objects/) - 불변 Value Object 설계

---

**작성일**: 2025-10-16
**검증 도구**: ArchUnit `PersistenceLayerTest.java`
