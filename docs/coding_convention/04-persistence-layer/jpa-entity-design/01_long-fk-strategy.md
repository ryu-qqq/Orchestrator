# Long FK Strategy (Foreign Key Strategy)

**Priority**: 🔴 CRITICAL
**Validation**: ArchUnit `PersistenceLayerTest.java`

---

## 📋 핵심 원칙

JPA 관계 어노테이션(`@OneToMany`, `@ManyToOne`, `@ManyToMany`) **절대 사용 금지**

Entity 간 관계는 **Long 타입 Foreign Key 필드**로만 표현합니다.

---

## 🚨 왜 JPA 관계 어노테이션을 금지하는가?

JPA 관계 어노테이션은 여러 심각한 문제를 야기합니다:

### 문제점 매트릭스

| 문제점 | 설명 | 영향 |
|--------|------|------|
| **Law of Demeter 위반** | 연관 Entity를 직접 탐색<br>(`order.getUser().getName()`) | Getter 체이닝 발생<br>캡슐화 위반 |
| **N+1 쿼리 문제** | 연관 Entity 로딩 시<br>추가 쿼리 발생 | 성능 저하<br>예측 불가능한 쿼리 |
| **양방향 참조 복잡도** | `mappedBy`, `cascade`,<br>`orphanRemoval` 관리 | 순환 참조<br>예상치 못한 삭제 |
| **영속성 컨텍스트 의존** | JPA 세션 외부에서<br>`LazyInitializationException` | 레이어 경계 침범<br>테스트 어려움 |
| **테스트 복잡도** | Entity 그래프 전체를<br>준비해야 함 | Mock 복잡도 증가<br>테스트 느림 |

---

## ✅ Long FK 전략의 5가지 장점

### 1. Law of Demeter 준수
Entity 간 직접 참조 없음 → Getter 체이닝 원천 차단

```java
// ❌ JPA 관계 사용 시 (Law of Demeter 위반)
String city = order.getCustomer().getAddress().getCity();

// ✅ Long FK 사용 시
//    - OrderEntity는 customerId만 가짐
//    - Application Layer에서 명시적 조회
Customer customer = loadCustomerPort.loadById(order.getCustomerId());
String city = customer.getAddressCity();  // 위임 메서드
```

### 2. 명시적 데이터 로딩
필요한 데이터만 Application Layer에서 명시적으로 로드

```java
// ✅ 필요한 데이터만 명시적 조회
@UseCase
@Transactional(readOnly = true)
public class GetOrderSummaryService {
    public OrderSummaryResult execute(OrderId orderId) {
        // Order만 필요하면 Order만 조회
        Order order = loadOrderPort.loadById(orderId).orElseThrow();
        return OrderSummaryResult.from(order);
    }
}

@UseCase
@Transactional(readOnly = true)
public class GetOrderDetailService {
    public OrderDetailResult execute(OrderId orderId) {
        // 상세 정보 필요 시 명시적으로 추가 조회
        Order order = loadOrderPort.loadById(orderId).orElseThrow();
        Customer customer = loadCustomerPort.loadById(order.getCustomerId()).orElseThrow();
        List<OrderItem> items = loadOrderItemsPort.loadByOrderId(orderId);

        return OrderDetailResult.of(order, customer, items);
    }
}
```

### 3. 성능 예측 가능
쿼리가 명확하고 최적화 용이

```java
// ✅ 정확히 어떤 쿼리가 실행되는지 명확
// 1. Order 조회 쿼리 1개
Order order = loadOrderPort.loadById(orderId);

// 2. Customer 조회 쿼리 1개 (필요 시에만)
if (needCustomerInfo) {
    Customer customer = loadCustomerPort.loadById(order.getCustomerId());
}

// 총 쿼리 수: 1-2개 (예측 가능)
```

### 4. 테스트 단순화
Entity를 독립적으로 테스트 가능

```java
// ✅ OrderEntity 테스트 - 독립적으로 가능
@Test
void shouldCreateOrderEntity() {
    OrderEntity entity = OrderEntity.create(
        1L,  // userId (Long FK)
        OrderStatus.PENDING,
        BigDecimal.valueOf(10000)
    );

    assertThat(entity.getUserId()).isEqualTo(1L);
    // UserEntity 생성 불필요!
}
```

### 5. 레이어 분리 강화
Persistence가 Domain 구조를 오염시키지 않음

```java
// ✅ Domain Layer: 순수 비즈니스 로직
public class Order {
    private final UserId userId;  // Value Object
    // Entity 참조 없음
}

// ✅ Persistence Layer: DB 구조
@Entity
public class OrderEntity {
    private Long userId;  // Long FK
    // User Entity 참조 없음
}
```

---

## 📐 Long FK 사용 패턴

### 패턴 1: 1:N 관계 (One-to-Many)

#### ❌ Bad - JPA 관계 어노테이션

```java
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItemEntity> items;  // ❌ 절대 금지!

    // 문제점:
    // 1. OrderEntity 조회 시 OrderItem도 자동 로딩 (N+1 위험)
    // 2. cascade 설정으로 예상치 못한 삭제 발생 가능
    // 3. 테스트 시 OrderItem까지 준비 필요
}
```

#### ✅ Good - Long FK 전략

```java
/**
 * Order Entity (부모)
 */
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
    private OrderStatus status;

    // ✅ OrderItem은 포함하지 않음
    //    - Application Layer에서 별도 조회
    //    - 필요한 경우에만 로딩
}

/**
 * OrderItem Entity (자식)
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Long FK로만 관계 표현
    @Column(nullable = false)
    private Long orderId;  // Order와의 관계

    @Column(nullable = false)
    private Long productId;  // Product와의 관계

    @Column(nullable = false)
    private Integer quantity;
}

/**
 * Application Layer에서 명시적 조합
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrderWithItemsService {
    private final LoadOrderPort loadOrderPort;
    private final LoadOrderItemsPort loadOrderItemsPort;

    public OrderWithItemsResult execute(OrderId orderId) {
        // 1. Order 조회 (쿼리 1개)
        Order order = loadOrderPort.loadById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 2. OrderItem 조회 (쿼리 1개)
        List<OrderItem> items = loadOrderItemsPort.loadByOrderId(orderId);

        // 3. 조합 (Application Layer 책임)
        return OrderWithItemsResult.of(order, items);
    }
}
```

**쿼리 수**:
- JPA 관계: 1 (Order) + N (각 OrderItem) = N+1 쿼리
- Long FK: 1 (Order) + 1 (OrderItems IN query) = 2 쿼리 (예측 가능)

---

### 패턴 2: N:1 관계 (Many-to-One)

#### ❌ Bad - JPA 관계 어노테이션

```java
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;  // ❌ 절대 금지!

    // 문제점:
    // 1. order.getUser().getName() → Law of Demeter 위반
    // 2. LazyInitializationException 위험
    // 3. 테스트 시 UserEntity까지 준비 필요
}
```

#### ✅ Good - Long FK 전략

```java
/**
 * Order Entity
 */
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Long FK로만 관계 표현
    @Column(nullable = false)
    private Long userId;

    // Getter
    public Long getUserId() { return userId; }
}

/**
 * Application Layer에서 명시적 로드
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrderWithUserService {
    private final LoadOrderPort loadOrderPort;
    private final LoadUserPort loadUserPort;

    public OrderWithUserResult execute(OrderId orderId) {
        // 1. Order 조회
        Order order = loadOrderPort.loadById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 2. User 조회 (필요한 경우에만!)
        User user = loadUserPort.loadById(order.getUserId())
            .orElseThrow(() -> new UserNotFoundException(order.getUserId()));

        // 3. 조합
        return OrderWithUserResult.of(order, user);
    }
}

/**
 * User 정보 불필요 시 - Order만 조회
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrderSummaryService {
    private final LoadOrderPort loadOrderPort;

    public OrderSummaryResult execute(OrderId orderId) {
        // ✅ User 정보 불필요 → User 조회 안 함
        //    - 불필요한 쿼리 실행 방지
        //    - 성능 최적화
        Order order = loadOrderPort.loadById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        return OrderSummaryResult.from(order);
    }
}
```

---

### 패턴 3: N:M 관계 (Many-to-Many)

#### ❌ Bad - JPA 관계 어노테이션

```java
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    private Long id;

    @ManyToMany
    @JoinTable(
        name = "order_products",
        joinColumns = @JoinColumn(name = "order_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private List<ProductEntity> products;  // ❌ 절대 금지!

    // 문제점:
    // 1. 중간 테이블의 추가 컬럼 (quantity 등) 표현 불가
    // 2. N+M 쿼리 발생 위험
    // 3. 테스트 복잡도 급증
}
```

#### ✅ Good - Long FK 전략 + 명시적 중간 테이블

```java
/**
 * Order Entity
 */
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Product 참조 없음
    //    - OrderProduct 중간 테이블로 관계 관리
}

/**
 * OrderProduct Entity (중간 테이블)
 */
@Entity
@Table(name = "order_products")
public class OrderProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Long FK로만 관계 표현
    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    // ✅ 추가 컬럼 표현 가능
    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;
}

/**
 * Application Layer에서 명시적 조합
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrderWithProductsService {
    private final LoadOrderPort loadOrderPort;
    private final LoadOrderProductsPort loadOrderProductsPort;
    private final LoadProductPort loadProductPort;

    public OrderWithProductsResult execute(OrderId orderId) {
        // 1. Order 조회 (쿼리 1개)
        Order order = loadOrderPort.loadById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 2. OrderProduct 중간 테이블 조회 (쿼리 1개)
        List<OrderProduct> orderProducts = loadOrderProductsPort.loadByOrderId(orderId);

        // 3. Product 조회 (쿼리 1개 - IN 절 사용)
        List<ProductId> productIds = orderProducts.stream()
            .map(OrderProduct::getProductId)
            .toList();
        List<Product> products = loadProductPort.loadByIds(productIds);

        // 4. 조합
        return OrderWithProductsResult.of(order, orderProducts, products);
    }
}
```

**쿼리 수**:
- JPA 관계: 1 (Order) + M (Products) + N (OrderProducts) = N+M+1 쿼리
- Long FK: 1 (Order) + 1 (OrderProducts) + 1 (Products IN) = 3 쿼리 (예측 가능)

---

## ✅ 체크리스트

코드 작성 전:
- [ ] JPA 관계 어노테이션 절대 사용 금지
  - [ ] `@OneToMany` 금지
  - [ ] `@ManyToOne` 금지
  - [ ] `@ManyToMany` 금지
  - [ ] `@OneToOne` 금지
- [ ] Entity 간 참조는 Long 타입 FK 필드로만
- [ ] N:M 관계는 명시적 중간 테이블 Entity 생성
- [ ] 데이터 조합은 Application Layer에서 처리

커밋 전:
- [ ] ArchUnit 테스트 통과 (`PersistenceLayerTest.java`)
- [ ] Repository는 단일 Entity만 의존 (SRP)

---

## 🔧 검증 방법

### ArchUnit 테스트

```java
// application/src/test/java/architecture/PersistenceLayerTest.java
@ArchTest
static final ArchRule jpa_entities_should_not_use_relationship_annotations =
    fields()
        .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
        .should().notBeAnnotatedWith(OneToMany.class)
        .andShould().notBeAnnotatedWith(ManyToOne.class)
        .andShould().notBeAnnotatedWith(ManyToMany.class)
        .andShould().notBeAnnotatedWith(OneToOne.class)
        .because("JPA relationship annotations cause Law of Demeter violations and N+1 query issues");
```

---

## 📚 관련 가이드

**전제 조건**:
- [Law of Demeter](../../02-domain-layer/law-of-demeter/) - Getter 체이닝 금지 원칙

**연관 패턴**:
- [Entity Immutability](./02_entity-immutability.md) - Entity 설계 원칙
- [N+1 Prevention](./03_n-plus-one-prevention.md) - 성능 최적화

**심화 학습**:
- [Repository SRP](../repository-srp/) - 단일 Entity 의존 원칙

---

**작성일**: 2025-10-16
**검증 도구**: ArchUnit `PersistenceLayerTest.java`
