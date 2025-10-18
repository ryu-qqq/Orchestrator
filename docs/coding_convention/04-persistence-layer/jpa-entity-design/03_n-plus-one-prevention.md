# N+1 Query Prevention (N+1 쿼리 예방)

**Priority**: 🔴 CRITICAL
**Validation**: Performance Testing, Query Logging

---

## 📋 핵심 원칙

N+1 쿼리 문제는 **설계 단계에서 예방**해야 합니다.

1. **Long FK 전략** - JPA 관계 어노테이션 금지로 근본적 차단
2. **명시적 조회** - Application Layer에서 필요한 데이터만 명시적 로드
3. **Batch Fetch** - IN 절로 한 번에 조회
4. **DTO Projection** - 필요한 컬럼만 SELECT

---

## 🚨 N+1 쿼리 문제란?

### 문제 시나리오

```java
// ❌ JPA 관계 어노테이션 사용 시
@Entity
public class OrderEntity {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;  // ❌ Entity 참조
}

// ❌ N+1 쿼리 발생
List<OrderEntity> orders = orderRepository.findAll();  // 쿼리 1개

for (OrderEntity order : orders) {
    String userName = order.getUser().getName();  // 쿼리 N개 추가!
}

// 결과: 1 (Order 조회) + N (각 Order의 User 조회) = N+1 쿼리
```

**성능 영향**:
- 주문 100개 → 101개 쿼리 (1 + 100)
- 주문 1,000개 → 1,001개 쿼리 (1 + 1,000)
- DB 부하 급증, 응답 시간 폭발

---

## ✅ 해결책 1: Long FK 전략 (근본적 해결)

**원리**: Entity 간 참조를 Long FK로만 표현 → N+1 자체를 원천 차단

### ✅ Good - Long FK + 명시적 조회

```java
/**
 * Order Entity (Entity 참조 없음)
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

    @Column(nullable = false)
    private BigDecimal totalAmount;

    // Getter
    public Long getUserId() { return userId; }
}

/**
 * Application Layer - 명시적 조회
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrdersWithUsersService {
    private final LoadOrdersPort loadOrdersPort;
    private final LoadUsersPort loadUsersPort;

    public List<OrderWithUserResult> execute(GetOrdersQuery query) {
        // 1. Order 조회 (쿼리 1개)
        List<Order> orders = loadOrdersPort.loadAll();

        // 2. User ID 추출
        List<UserId> userIds = orders.stream()
            .map(Order::getUserId)
            .distinct()
            .toList();

        // 3. User 일괄 조회 (쿼리 1개 - IN 절)
        List<User> users = loadUsersPort.loadByIds(userIds);

        // 4. Map으로 변환 (메모리 조합)
        Map<UserId, User> userMap = users.stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

        // 5. 조합 (추가 쿼리 없음)
        return orders.stream()
            .map(order -> OrderWithUserResult.of(
                order,
                userMap.get(order.getUserId())
            ))
            .toList();
    }
}

/**
 * Repository 구현 - IN 절 사용
 */
@Component
public class UserJpaAdapter implements LoadUsersPort {

    @Override
    public List<User> loadByIds(List<UserId> userIds) {
        List<Long> ids = userIds.stream()
            .map(UserId::getValue)
            .toList();

        // ✅ IN 절로 한 번에 조회
        List<UserEntity> entities = userRepository.findAllById(ids);

        return entities.stream()
            .map(UserMapper::toDomain)
            .toList();
    }
}
```

**쿼리 수**:
- Before (N+1): 1 (Orders) + 100 (각 User) = 101 쿼리
- After: 1 (Orders) + 1 (Users IN) = 2 쿼리 (98% 감소)

**성능 개선**:
- 응답 시간: 1,000ms → 20ms (98% 감소)
- DB 부하: 101 queries → 2 queries

---

## ✅ 해결책 2: QueryDSL + DTO Projection

**원리**: 필요한 컬럼만 SELECT → JOIN 최소화

### ❌ Bad - Entity 전체 조회

```java
// ❌ Entity 전체 조회 (불필요한 컬럼 포함)
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status")
    List<OrderEntity> findByStatus(@Param("status") OrderStatus status);
}

// ✅ 사용 코드 - 실제로는 id, totalAmount만 필요
List<OrderEntity> orders = orderRepository.findByStatus(OrderStatus.PENDING);
for (OrderEntity order : orders) {
    System.out.println(order.getId() + ": " + order.getTotalAmount());
    // userId, createdAt 등 다른 필드는 사용하지 않음
}
```

**문제점**:
- 불필요한 컬럼 전송 (네트워크 대역폭 낭비)
- Entity 전체 로딩 (메모리 낭비)

---

### ✅ Good - QueryDSL DTO Projection

```java
/**
 * DTO - 필요한 필드만
 */
public record OrderSummaryDto(
    Long orderId,
    BigDecimal totalAmount
) {
    // Record는 불변 DTO에 최적
}

/**
 * QueryDSL Repository
 */
@Repository
public class OrderQuerydslRepository {
    private final JPAQueryFactory queryFactory;

    public List<OrderSummaryDto> findSummariesByStatus(OrderStatus status) {
        QOrderEntity order = QOrderEntity.orderEntity;

        // ✅ DTO Projection - 필요한 컬럼만 SELECT
        return queryFactory
            .select(Projections.constructor(
                OrderSummaryDto.class,
                order.id,
                order.totalAmount
            ))
            .from(order)
            .where(order.status.eq(status))
            .fetch();
    }
}
```

**쿼리**:
```sql
-- ✅ 필요한 컬럼만 SELECT
SELECT o.id, o.total_amount
FROM orders o
WHERE o.status = 'PENDING'
```

**성능 개선**:
- 네트워크 전송량: 100% → 20% (80% 감소)
- 메모리 사용량: 100% → 20% (80% 감소)

---

## ✅ 해결책 3: Fetch Join (불가피한 경우)

**주의**: Long FK 전략을 우선 사용하고, **정말 불가피한 경우**에만 Fetch Join 사용

### 시나리오: Aggregate 내부 Collection

```java
/**
 * Aggregate Root
 */
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    private Long id;

    // ✅ Aggregate 내부 Collection (예외적 허용)
    //    - OrderItem은 Order의 생명주기에 완전히 종속
    //    - 단독 조회/수정 없음
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    // Package-private: 외부 접근 제한
    List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }
}

/**
 * Fetch Join 사용
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    @Query("SELECT DISTINCT o FROM OrderEntity o " +
           "LEFT JOIN FETCH o.items " +
           "WHERE o.status = :status")
    List<OrderEntity> findByStatusWithItems(@Param("status") OrderStatus status);
}
```

**쿼리**:
```sql
-- ✅ Fetch Join - 한 번에 조회
SELECT DISTINCT o.*, i.*
FROM orders o
LEFT JOIN order_items i ON o.id = i.order_id
WHERE o.status = 'PENDING'
```

**주의사항**:
1. **DISTINCT 필수** - Cartesian Product 방지
2. **Paging 불가** - Collection Fetch Join + Paging → 메모리 OOM 위험
3. **제한적 사용** - Aggregate 내부에만, 일반 Entity 간 참조는 Long FK 사용

---

## 🎯 전략별 비교

| 전략 | 쿼리 수 | 사용 시점 | 장점 | 단점 |
|------|---------|-----------|------|------|
| **Long FK + IN 절** | 2 | 기본 전략 | N+1 근본 차단, 명시적 | 코드 약간 길어짐 |
| **DTO Projection** | 1 | 조회 전용 | 최소 데이터 전송 | DTO 추가 정의 |
| **Fetch Join** | 1 | Aggregate 내부 | 한 번에 조회 | Paging 불가, 제한적 |

---

## 📐 실전 패턴

### 패턴 1: 1:N 관계 조회

```java
/**
 * Order → OrderItems (1:N)
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrdersWithItemsService {

    public List<OrderWithItemsResult> execute() {
        // 1. Order 조회 (쿼리 1개)
        List<Order> orders = loadOrdersPort.loadAll();

        // 2. Order ID 추출
        List<OrderId> orderIds = orders.stream()
            .map(Order::getId)
            .toList();

        // 3. OrderItems 일괄 조회 (쿼리 1개 - IN 절)
        List<OrderItem> allItems = loadOrderItemsPort.loadByOrderIds(orderIds);

        // 4. OrderId별로 그룹핑 (메모리 작업)
        Map<OrderId, List<OrderItem>> itemsMap = allItems.stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 5. 조합 (추가 쿼리 없음)
        return orders.stream()
            .map(order -> OrderWithItemsResult.of(
                order,
                itemsMap.getOrDefault(order.getId(), List.of())
            ))
            .toList();
    }
}

/**
 * Repository - IN 절 구현
 */
@Component
public class OrderItemJpaAdapter implements LoadOrderItemsPort {

    @Override
    public List<OrderItem> loadByOrderIds(List<OrderId> orderIds) {
        List<Long> ids = orderIds.stream()
            .map(OrderId::getValue)
            .toList();

        // ✅ IN 절로 한 번에 조회
        List<OrderItemEntity> entities = orderItemRepository
            .findByOrderIdIn(ids);

        return entities.stream()
            .map(OrderItemMapper::toDomain)
            .toList();
    }
}
```

---

### 패턴 2: N:M 관계 조회

```java
/**
 * Order ↔ Products (N:M, OrderProduct 중간 테이블)
 */
@UseCase
@Transactional(readOnly = true)
public class GetOrdersWithProductsService {

    public List<OrderWithProductsResult> execute() {
        // 1. Order 조회 (쿼리 1개)
        List<Order> orders = loadOrdersPort.loadAll();

        List<OrderId> orderIds = orders.stream()
            .map(Order::getId)
            .toList();

        // 2. OrderProduct 중간 테이블 조회 (쿼리 1개 - IN 절)
        List<OrderProduct> orderProducts = loadOrderProductsPort
            .loadByOrderIds(orderIds);

        // 3. Product ID 추출
        List<ProductId> productIds = orderProducts.stream()
            .map(OrderProduct::getProductId)
            .distinct()
            .toList();

        // 4. Product 일괄 조회 (쿼리 1개 - IN 절)
        List<Product> products = loadProductsPort.loadByIds(productIds);

        // 5. Product Map 생성
        Map<ProductId, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 6. OrderProduct를 Order별로 그룹핑
        Map<OrderId, List<OrderProduct>> orderProductsMap = orderProducts.stream()
            .collect(Collectors.groupingBy(OrderProduct::getOrderId));

        // 7. 조합 (추가 쿼리 없음)
        return orders.stream()
            .map(order -> {
                List<OrderProduct> orderProductList = orderProductsMap
                    .getOrDefault(order.getId(), List.of());

                List<ProductWithQuantity> productsWithQuantity = orderProductList.stream()
                    .map(op -> new ProductWithQuantity(
                        productMap.get(op.getProductId()),
                        op.getQuantity()
                    ))
                    .toList();

                return OrderWithProductsResult.of(order, productsWithQuantity);
            })
            .toList();
    }
}
```

**쿼리 수**:
- JPA N:M: 1 (Orders) + N (OrderProducts per Order) + M (Products per OrderProduct) = N+M+1
- Long FK: 1 (Orders) + 1 (OrderProducts IN) + 1 (Products IN) = 3 (예측 가능)

---

## 🔧 성능 측정 및 검증

### Hibernate Query Logging

```yaml
# application.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### P6Spy (쿼리 카운팅)

```yaml
# application.yml
decorator:
  datasource:
    p6spy:
      enable-logging: true
      multiline: true
      logging: slf4j
```

### 성능 테스트

```java
@SpringBootTest
public class OrderQueryPerformanceTest {

    @Autowired
    private GetOrdersWithUsersService service;

    @Test
    void shouldExecuteOnlyTwoQueries() {
        // Given: 100개 Order 생성
        createOrders(100);

        // When: 조회
        long startTime = System.currentTimeMillis();
        List<OrderWithUserResult> results = service.execute(new GetOrdersQuery());
        long endTime = System.currentTimeMillis();

        // Then: 쿼리 수 검증
        // - 1개: Orders 조회
        // - 1개: Users IN 절 조회
        // 총 2개 (N+1 아님)

        assertThat(results).hasSize(100);
        assertThat(endTime - startTime).isLessThan(100); // 100ms 이내

        // 로그 확인: 정확히 2개 쿼리 실행됨
    }
}
```

---

## ✅ 체크리스트

코드 작성 전:
- [ ] JPA 관계 어노테이션 사용 안 함 (Long FK 전략)
- [ ] 1:N 관계는 IN 절로 일괄 조회
- [ ] N:M 관계는 중간 테이블 + 2번 IN 절 조회
- [ ] DTO Projection 우선 고려 (필요한 컬럼만)
- [ ] Fetch Join은 Aggregate 내부에만 제한적 사용

커밋 전:
- [ ] Query Logging 확인 (쿼리 수 검증)
- [ ] 성능 테스트 작성 및 통과
- [ ] 응답 시간 100ms 이내 (목표)

---

## 📚 관련 가이드

**전제 조건**:
- [Long FK Strategy](./01_long-fk-strategy.md) - JPA 관계 어노테이션 금지 (N+1 근본 차단)
- [Entity Immutability](./02_entity-immutability.md) - Entity 설계 원칙

**연관 패턴**:
- [QueryDSL Patterns](../querydsl-patterns/) - DTO Projection, 동적 쿼리
- [Repository SRP](../repository-srp/) - 단일 Entity 의존 원칙

**심화 학습**:
- [Caching Strategy](../../08-enterprise-patterns/caching-strategy/) - 캐싱으로 추가 최적화

---

**작성일**: 2025-10-16
**검증 도구**: Hibernate Query Logging, P6Spy, Performance Tests
