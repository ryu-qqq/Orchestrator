# Event Sourcing - 이벤트 기반 상태 저장 및 복원

**목적**: Event를 유일한 진실의 원천(Source of Truth)으로 활용하여 상태 복원 및 감사 로그 구현

**관련 문서**:
- [Domain Events](./01_domain-events.md)
- [Saga Pattern](./03_saga-pattern.md)

**필수 버전**: Spring Framework 5.0+, PostgreSQL 12+ (JSONB)

---

## 📌 핵심 원칙

### Event Sourcing이란?

1. **Event = Source of Truth**: 상태를 직접 저장하지 않고 Event만 저장
2. **상태 복원**: Event 재생(Replay)으로 현재 상태 계산
3. **완벽한 감사 로그**: 모든 변경 이력 자동 보존
4. **시간 여행**: 과거 특정 시점의 상태 재현 가능

### 전통적 CRUD vs Event Sourcing

| 항목 | CRUD (현재 상태 저장) | Event Sourcing (Event 저장) |
|------|---------------------|----------------------------|
| 저장 대상 | 현재 상태 | 모든 Event |
| 이력 추적 | 별도 Audit 테이블 필요 | 자동 보존 |
| 상태 복원 | DB에서 직접 조회 | Event Replay |
| 디버깅 | 현재 상태만 확인 | 전체 과정 추적 |
| 저장 용량 | 적음 | 많음 (Event 누적) |

---

## ❌ 전통적 CRUD 문제점

### 문제 1: 변경 이력 손실

```java
// ❌ Before - CRUD (현재 상태만 저장)
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;  // ⚠️ 현재 상태만 저장

    /**
     * ❌ 문제점:
     * - PENDING → APPROVED 변경 시각 알 수 없음
     * - 누가 승인했는지 알 수 없음
     * - 취소 후 재승인 가능 여부 판단 불가
     */
    public void approve() {
        this.status = OrderStatus.APPROVED;  // ⚠️ 이전 상태 손실
    }
}

/**
 * ❌ Audit 테이블 별도 관리 필요
 */
@Entity
@Table(name = "order_history")
public class OrderHistory {
    @Id
    private Long id;
    private Long orderId;
    private OrderStatus beforeStatus;
    private OrderStatus afterStatus;
    private Instant changedAt;
    // ⚠️ 수동으로 이력 관리 → 누락 위험
}
```

**문제점**:
- ❌ 변경 이력 추적을 위해 별도 Audit 테이블 관리
- ❌ 이력 기록 누락 가능 (개발자 실수)
- ❌ 과거 특정 시점 상태 복원 불가
- ❌ "왜 이렇게 되었는가?" 답변 어려움

---

## ✅ Event Sourcing 패턴

### 패턴 1: Event Store 설계

```java
package com.company.infrastructure.eventsourcing;

/**
 * Event Store - Event 영속화
 *
 * @author development-team
 * @since 1.0.0
 */
@Entity
@Table(name = "event_store", indexes = {
    @Index(name = "idx_aggregate_id", columnList = "aggregate_id"),
    @Index(name = "idx_aggregate_type_id", columnList = "aggregate_type, aggregate_id")
})
public class EventStoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Aggregate 식별자
     */
    @Column(nullable = false, length = 100)
    private String aggregateId;

    /**
     * Aggregate 타입 (Order, Payment 등)
     */
    @Column(nullable = false, length = 50)
    private String aggregateType;

    /**
     * Event 버전 (낙관적 잠금)
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Event 타입 (OrderCreated, OrderCancelled 등)
     */
    @Column(nullable = false, length = 100)
    private String eventType;

    /**
     * Event 데이터 (JSON)
     */
    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;

    /**
     * Event 발생 시각
     */
    @Column(nullable = false)
    private Instant occurredAt;

    /**
     * 메타데이터 (사용자, IP 주소 등)
     */
    @Column(columnDefinition = "JSONB")
    private String metadata;
}
```

---

### 패턴 2: Event Store Repository

```java
package com.company.infrastructure.eventsourcing;

/**
 * Event Store Repository
 *
 * @author development-team
 * @since 1.0.0
 */
@Repository
public interface EventStoreRepository extends JpaRepository<EventStoreEntity, Long> {

    /**
     * Aggregate의 모든 Event 조회 (시간순)
     */
    List<EventStoreEntity> findByAggregateTypeAndAggregateIdOrderByVersionAsc(
        String aggregateType, String aggregateId
    );

    /**
     * 특정 버전 이후 Event만 조회 (Snapshot 이후)
     */
    List<EventStoreEntity> findByAggregateTypeAndAggregateIdAndVersionGreaterThanOrderByVersionAsc(
        String aggregateType, String aggregateId, Long afterVersion
    );
}

/**
 * Event Store Service
 */
@Service
public class EventStoreService {

    private final EventStoreRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * ✅ Event 저장
     */
    @Transactional
    public void save(String aggregateType, String aggregateId, Object event) {
        EventStoreEntity entity = new EventStoreEntity();
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setEventType(event.getClass().getSimpleName());
        entity.setPayload(serializeToJson(event));
        entity.setOccurredAt(Instant.now());

        repository.save(entity);
    }

    /**
     * ✅ Event 조회 (Aggregate 복원용)
     */
    public List<Object> loadEvents(String aggregateType, String aggregateId) {
        List<EventStoreEntity> entities = repository
            .findByAggregateTypeAndAggregateIdOrderByVersionAsc(aggregateType, aggregateId);

        return entities.stream()
            .map(this::deserializeEvent)
            .toList();
    }

    private String serializeToJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(e);
        }
    }

    private Object deserializeEvent(EventStoreEntity entity) {
        try {
            Class<?> eventClass = Class.forName("com.company.domain.order.event." + entity.getEventType());
            return objectMapper.readValue(entity.getPayload(), eventClass);
        } catch (Exception e) {
            throw new EventDeserializationException(e);
        }
    }
}
```

---

### 패턴 3: Aggregate 복원 (Event Replay)

```java
package com.company.domain.order;

/**
 * Order Aggregate - Event Sourcing
 *
 * @author development-team
 * @since 1.0.0
 */
public class Order {

    private OrderId id;
    private CustomerId customerId;
    private List<OrderLineItem> items;
    private OrderStatus status;
    private Money totalAmount;

    /**
     * ✅ Event Replay로 상태 복원
     */
    public static Order reconstitute(List<Object> events) {
        Order order = new Order();

        for (Object event : events) {
            order.apply(event);
        }

        return order;
    }

    /**
     * ✅ Event별 상태 변경 로직
     */
    private void apply(Object event) {
        switch (event) {
            case OrderCreated(var id, var customerId, var items, var amount, var time) -> {
                this.id = id;
                this.customerId = customerId;
                this.items = new ArrayList<>(items);
                this.totalAmount = amount;
                this.status = OrderStatus.PENDING;
            }

            case OrderApproved(var id, var approvedBy, var time) -> {
                this.status = OrderStatus.APPROVED;
            }

            case OrderShipped(var id, var tracking, var address, var time) -> {
                this.status = OrderStatus.SHIPPED;
            }

            case OrderCancelled(var id, var reason, var time) -> {
                this.status = OrderStatus.CANCELLED;
            }

            default -> throw new UnknownEventException(event.getClass());
        }
    }
}

/**
 * Event Sourced Repository
 */
@Component
public class EventSourcedOrderRepository {

    private final EventStoreService eventStore;

    /**
     * ✅ Event 저장
     */
    @Transactional
    public void save(Order order) {
        List<Object> uncommittedEvents = order.getUncommittedEvents();

        for (Object event : uncommittedEvents) {
            eventStore.save("Order", order.getId().value(), event);
        }

        order.markEventsAsCommitted();
    }

    /**
     * ✅ Event Replay로 Aggregate 복원
     */
    public Optional<Order> findById(OrderId orderId) {
        List<Object> events = eventStore.loadEvents("Order", orderId.value());

        if (events.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Order.reconstitute(events));
    }
}
```

---

## 🎯 실전 예제: Snapshot 패턴

### ✅ Example: 성능 최적화

```java
/**
 * Snapshot - 특정 시점의 상태 저장
 *
 * - 문제: Event 1만 개 재생 시 느림
 * - 해결: 1000번째 Event마다 Snapshot 저장 → 이후 Event만 재생
 *
 * @author development-team
 * @since 1.0.0
 */
@Entity
@Table(name = "aggregate_snapshots")
public class SnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    /**
     * Snapshot 생성 시점의 Event 버전
     */
    @Column(nullable = false)
    private Long version;

    /**
     * Aggregate 상태 (JSON)
     */
    @Column(nullable = false, columnDefinition = "JSONB")
    private String state;

    @Column(nullable = false)
    private Instant createdAt;
}

/**
 * Snapshot Service
 */
@Service
public class SnapshotService {

    private static final int SNAPSHOT_INTERVAL = 1000;

    /**
     * ✅ Snapshot 저장 (1000개 Event마다)
     */
    @Transactional
    public void saveSnapshotIfNeeded(Order order, Long currentVersion) {
        if (currentVersion % SNAPSHOT_INTERVAL == 0) {
            SnapshotEntity snapshot = new SnapshotEntity();
            snapshot.setAggregateType("Order");
            snapshot.setAggregateId(order.getId().value());
            snapshot.setVersion(currentVersion);
            snapshot.setState(serializeToJson(order));
            snapshot.setCreatedAt(Instant.now());

            snapshotRepository.save(snapshot);
        }
    }

    /**
     * ✅ Aggregate 복원 (Snapshot + 이후 Event)
     */
    public Order reconstituteWithSnapshot(OrderId orderId) {
        // 1. 최신 Snapshot 조회
        Optional<SnapshotEntity> snapshot = snapshotRepository
            .findTopByAggregateTypeAndAggregateIdOrderByVersionDesc("Order", orderId.value());

        if (snapshot.isEmpty()) {
            // Snapshot 없음 → 전체 Event 재생
            return Order.reconstitute(eventStore.loadEvents("Order", orderId.value()));
        }

        // 2. Snapshot으로 초기 상태 복원
        Order order = deserializeFromJson(snapshot.get().getState());

        // 3. Snapshot 이후 Event만 재생
        List<Object> events = eventStore.loadEventsAfterVersion(
            "Order", orderId.value(), snapshot.get().getVersion()
        );

        for (Object event : events) {
            order.apply(event);
        }

        return order;
    }
}
```

**Snapshot 성능 비교**:

| Event 수 | Snapshot 없음 | Snapshot 있음 (1000개마다) |
|---------|-------------|--------------------------|
| 100 | 10ms | 10ms |
| 1,000 | 100ms | 100ms |
| 10,000 | 1,000ms | 110ms ✅ (90% 개선) |
| 100,000 | 10,000ms | 1,100ms ✅ (89% 개선) |

---

## 📋 Event Sourcing 체크리스트

### 설계
- [ ] Event Store 테이블 설계 (JSONB 활용)
- [ ] Aggregate별 Event 타입 정의
- [ ] Snapshot 정책 결정 (1000개 단위 권장)

### 구현
- [ ] `apply()` 메서드로 Event 반영
- [ ] `reconstitute()` 메서드로 복원
- [ ] Event 버전 관리 (`@Version`)

### 성능
- [ ] Snapshot 주기 설정
- [ ] Event Store 인덱스 (aggregate_id)
- [ ] JSONB 쿼리 최적화

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
