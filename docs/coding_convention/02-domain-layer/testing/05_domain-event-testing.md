# Domain Event Testing - 도메인 이벤트 테스트

**목적**: Domain Event의 구조, 불변성, 발행 로직을 검증

**관련 문서**:
- [Aggregate Testing](01_aggregate-testing.md)
- [Consistency Boundaries](../aggregate-design/03_consistency-boundaries.md)
- [Testing Support Toolkit](00_testing-support-toolkit.md)

**검증 도구**: JUnit 5, AssertJ, DomainEventsSpy

---

## 📌 핵심 원칙

### Domain Event 테스트 특징

1. **이벤트 구조 검증**: 스키마, 불변성, 직렬화
2. **발행 로직 검증**: Aggregate에서 올바른 이벤트 발행
3. **이벤트 순서 검증**: 여러 이벤트 발행 시 순서 보장
4. **멱등성 검증**: 동일 작업 반복 시 이벤트 중복 방지

---

## ✅ Domain Event 테스트 패턴

### 패턴 1: 이벤트 구조 검증 (Record)

```java
package com.company.domain.order.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderCreatedEvent 구조 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderCreatedEventTest {

    @Test
    void create_WithValidData_ShouldInitializeEvent() {
        // Given
        OrderId orderId = OrderId.of(1L);
        CustomerId customerId = CustomerId.of(1L);
        Money totalPrice = Money.of(1000);
        Instant occurredAt = Instant.now();

        // When
        OrderCreatedEvent event = new OrderCreatedEvent(
            orderId, customerId, totalPrice, occurredAt
        );

        // Then
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.totalPrice()).isEqualTo(totalPrice);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void create_ShouldBeImmutable() {
        // Given
        OrderCreatedEvent event = new OrderCreatedEvent(
            OrderId.of(1L),
            CustomerId.of(1L),
            Money.of(1000),
            Instant.now()
        );

        // Then - Record는 불변
        assertThat(event).isInstanceOf(Record.class);
        // Setter 메서드가 없어야 함
    }

    @Test
    void equals_WithSameData_ShouldBeEqual() {
        // Given
        Instant now = Instant.now();
        OrderCreatedEvent event1 = new OrderCreatedEvent(
            OrderId.of(1L), CustomerId.of(1L), Money.of(1000), now
        );
        OrderCreatedEvent event2 = new OrderCreatedEvent(
            OrderId.of(1L), CustomerId.of(1L), Money.of(1000), now
        );

        // Then
        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    void create_WithNullOrderId_ShouldThrowException() {
        assertThatThrownBy(() -> new OrderCreatedEvent(
            null, CustomerId.of(1L), Money.of(1000), Instant.now()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order ID must not be null");
    }
}
```

---

### 패턴 2: Aggregate에서 이벤트 발행 검증

```java
package com.company.domain.order;

import com.company.testing.domain.events.DomainEventsSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Order Aggregate 이벤트 발행 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderEventPublishingTest {

    private DomainEventsSpy eventsSpy;

    @BeforeEach
    void setUp() {
        eventsSpy = new DomainEventsSpy<>(DomainEvents::pull, DomainEvents::clear);
        eventsSpy.reset();
    }

    @Test
    void create_ShouldPublishOrderCreatedEvent() {
        // When
        Order order = Order.create(CustomerId.of(1L));

        // Then
        assertThat(eventsSpy.ofType(OrderCreatedEvent.class))
            .hasSize(1)
            .first()
            .satisfies(event -> {
                assertThat(event.orderId()).isEqualTo(order.getId());
                assertThat(event.customerId()).isEqualTo(CustomerId.of(1L));
            });
    }

    @Test
    void approve_ShouldPublishOrderApprovedEvent() {
        // Given
        Order order = Order.create(CustomerId.of(1L));
        eventsSpy.reset(); // 생성 이벤트 제거

        // When
        order.approve();

        // Then
        assertThat(eventsSpy.ofType(OrderApprovedEvent.class))
            .hasSize(1)
            .first()
            .satisfies(event -> {
                assertThat(event.orderId()).isEqualTo(order.getId());
                assertThat(event.occurredAt()).isNotNull();
            });
    }

    @Test
    void cancel_ShouldPublishOrderCancelledEvent() {
        // Given
        Order order = Order.create(CustomerId.of(1L));
        eventsSpy.reset();

        // When
        order.cancel();

        // Then
        assertThat(eventsSpy.ofType(OrderCancelledEvent.class))
            .hasSize(1)
            .first()
            .satisfies(event -> {
                assertThat(event.orderId()).isEqualTo(order.getId());
                assertThat(event.reason()).isNotEmpty();
            });
    }
}
```

---

### 패턴 3: 이벤트 순서 검증

```java
/**
 * 여러 이벤트 발행 시 순서 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderEventSequenceTest {

    private DomainEventsSpy eventsSpy;

    @BeforeEach
    void setUp() {
        eventsSpy = new DomainEventsSpy<>(DomainEvents::pull, DomainEvents::clear);
        eventsSpy.reset();
    }

    @Test
    void createAndApprove_ShouldPublishEventsInOrder() {
        // When
        Order order = Order.create(CustomerId.of(1L));
        order.addItem(ProductId.of(101L), Quantity.of(1), Money.of(1000));
        order.approve();

        // Then
        List<DomainEvent> events = eventsSpy.allEvents();
        assertThat(events).hasSize(3);

        // 순서 검증
        assertThat(events.get(0)).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(1)).isInstanceOf(OrderItemAddedEvent.class);
        assertThat(events.get(2)).isInstanceOf(OrderApprovedEvent.class);

        // 시간 순서 검증
        assertThat(events.get(0).occurredAt())
            .isBeforeOrEqualTo(events.get(1).occurredAt());
        assertThat(events.get(1).occurredAt())
            .isBeforeOrEqualTo(events.get(2).occurredAt());
    }

    @Test
    void multipleStateTransitions_ShouldPublishAllEvents() {
        // When
        Order order = Order.create(CustomerId.of(1L));
        order.approve();
        order.ship();
        order.complete();

        // Then
        assertThat(eventsSpy.allEvents())
            .extracting(DomainEvent::getClass)
            .containsExactly(
                OrderCreatedEvent.class,
                OrderApprovedEvent.class,
                OrderShippedEvent.class,
                OrderCompletedEvent.class
            );
    }
}
```

---

### 패턴 4: 멱등성 검증

```java
/**
 * 이벤트 중복 발행 방지 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderEventIdempotencyTest {

    private DomainEventsSpy eventsSpy;

    @BeforeEach
    void setUp() {
        eventsSpy = new DomainEventsSpy<>(DomainEvents::pull, DomainEvents::clear);
        eventsSpy.reset();
    }

    @Test
    void approve_WhenAlreadyApproved_ShouldNotPublishDuplicateEvent() {
        // Given
        Order order = Order.create(CustomerId.of(1L));
        order.approve();

        int initialEventCount = eventsSpy.ofType(OrderApprovedEvent.class).size();
        assertThat(initialEventCount).isEqualTo(1);

        // When - 이미 승인된 주문을 다시 승인 시도
        assertThatThrownBy(() -> order.approve())
            .isInstanceOf(IllegalStateException.class);

        // Then - 이벤트 중복 발행 안 됨
        assertThat(eventsSpy.ofType(OrderApprovedEvent.class))
            .hasSize(initialEventCount); // 여전히 1개
    }

    @Test
    void addSameItem_ShouldPublishEventOnlyOnce() {
        // Given
        Order order = Order.create(CustomerId.of(1L));
        eventsSpy.reset();

        // When
        order.addItem(ProductId.of(101L), Quantity.of(1), Money.of(1000));

        // Then
        assertThat(eventsSpy.ofType(OrderItemAddedEvent.class)).hasSize(1);

        // When - 같은 상품 추가 시도 (비즈니스 규칙에 따라 병합 또는 거부)
        order.addItem(ProductId.of(101L), Quantity.of(1), Money.of(1000));

        // Then - 이벤트는 추가 발행됨 (수량 증가 이벤트)
        assertThat(eventsSpy.ofType(OrderItemAddedEvent.class)).hasSize(2);
    }
}
```

---

### 패턴 5: 이벤트 페이로드 검증

```java
/**
 * 이벤트 페이로드 데이터 무결성 테스트
 *
 * @author development-team
 * @since 1.0.0
 */
class OrderEventPayloadTest {

    @Test
    void orderCreatedEvent_ShouldContainAllRequiredData() {
        // Given
        Order order = Order.create(CustomerId.of(1L));
        order.addItem(ProductId.of(101L), Quantity.of(2), Money.of(1000));

        // When
        List<DomainEvent> events = order.getDomainEvents();
        OrderCreatedEvent event = events.stream()
            .filter(e -> e instanceof OrderCreatedEvent)
            .map(e -> (OrderCreatedEvent) e)
            .findFirst()
            .orElseThrow();

        // Then
        assertThat(event.orderId()).isNotNull();
        assertThat(event.customerId()).isEqualTo(CustomerId.of(1L));
        assertThat(event.totalPrice()).isEqualTo(Money.of(2000));
        assertThat(event.itemCount()).isEqualTo(1);
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.occurredAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void orderApprovedEvent_ShouldContainApprovalMetadata() {
        // Given
        Order order = Order.create(CustomerId.of(1L));

        // When
        order.approveBy(UserId.of(100L), "Approved for shipment");

        // Then
        List<DomainEvent> events = order.getDomainEvents();
        OrderApprovedEvent event = events.stream()
            .filter(e -> e instanceof OrderApprovedEvent)
            .map(e -> (OrderApprovedEvent) e)
            .findFirst()
            .orElseThrow();

        assertThat(event.approvedBy()).isEqualTo(UserId.of(100L));
        assertThat(event.approvalReason()).isEqualTo("Approved for shipment");
        assertThat(event.approvedAt()).isNotNull();
    }
}
```

---

## 📋 Domain Event 테스트 체크리스트

- [ ] 이벤트 구조 검증 (Record, 불변성)
- [ ] Equals/HashCode 검증
- [ ] Aggregate에서 올바른 이벤트 발행
- [ ] 이벤트 순서 보장
- [ ] 멱등성 검증 (중복 방지)
- [ ] 페이로드 데이터 무결성
- [ ] 시간 필드 검증 (occurredAt)

---

## 🔗 Testing Support Toolkit 연계

**`00_testing-support-toolkit.md` 활용:**

```java
// DomainEventsSpy 사용
@BeforeEach
void setUp() {
    eventsSpy = new DomainEventsSpy<>(DomainEvents::pull, DomainEvents::clear);
    eventsSpy.reset();
}

// ClockFixtures 사용 (결정론적 시간)
@Test
void orderCreatedEvent_ShouldHaveFixedTimestamp() {
    Clock clock = ClockFixtures.fixedAt("2025-10-16T10:00:00Z");

    Order order = Order.create(CustomerId.of(1L), clock);

    OrderCreatedEvent event = eventsSpy.ofType(OrderCreatedEvent.class).get(0);
    assertThat(event.occurredAt()).isEqualTo(clock.instant());
}
```

---

## ⚠️ 주의사항

### ❌ Application 레벨 이벤트 처리 테스트하지 말 것

```java
// ❌ 도메인 테스트에서 Outbox, 메시지 발행 테스트 금지
@Test
void approve_ShouldPublishToKafka() { // ❌ 이건 통합 테스트!
    order.approve();
    verify(kafkaTemplate).send("order-events", event);
}
```

**올바른 방법**: 도메인은 이벤트 생성만 테스트, 발행은 Application Service 테스트에서

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
