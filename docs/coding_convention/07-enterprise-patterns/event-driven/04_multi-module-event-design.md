# Multi-Module Event Design - 멀티모듈 이벤트 설계 규칙

**목적**: 멀티모듈 환경에서 Domain Event의 패키지 위치 및 의존성 규칙 정의

**관련 문서**:
- [Domain Events](./01_domain-events.md)
- [Event Modeling with Sealed Classes](../../06-java21-patterns/sealed-classes/02_event-modeling.md)
- [Domain Package Guide](../../02-domain-layer/package-guide/01_domain_package_guide.md)

**필수 버전**: Spring Framework 5.0+, Java 21+

---

## 📌 핵심 원칙

### 멀티모듈에서의 Event 설계 원칙

1. **Event는 domain 모듈에만 배치**: Application/Infrastructure 의존 금지
2. **Event는 domain 객체만 사용**: DTO, Infrastructure 타입 사용 금지
3. **모듈 간 Event 구독 시 domain 모듈만 의존**: Application 모듈 간 의존 금지
4. **Long FK 기반 참조**: Entity 직접 참조 대신 Long 타입 ID 사용

---

## 📁 멀티모듈 패키지 구조

### Rule 1: Event는 {모듈}-domain 모듈에 배치

```
root-project/
├── order-domain/
│   └── src/main/java/
│       └── com/company/order/domain/
│           ├── model/
│           │   ├── Order.java
│           │   └── OrderLineItem.java
│           ├── vo/
│           │   ├── OrderId.java
│           │   └── CustomerId.java
│           └── event/                    # ✅ Event 위치
│               ├── OrderEvent.java       # Sealed Interface
│               ├── OrderCreated.java
│               ├── OrderCancelled.java
│               └── OrderShipped.java
│
├── order-application/
│   └── src/main/java/
│       └── com/company/order/application/
│           ├── service/
│           │   └── OrderService.java
│           └── event/                    # ✅ 자신의 Event Handler
│               └── OrderEventHandler.java
│
├── inventory-domain/
│   └── src/main/java/
│       └── com/company/inventory/domain/
│           └── event/
│               └── InventoryEvent.java
│
└── inventory-application/
    └── src/main/java/
        └── com/company/inventory/application/
            └── event/                     # ✅ 다른 모듈 Event Handler
                ├── InventoryEventHandler.java
                └── OrderEventHandler.java  # Order Event 구독
```

---

## ✅ 의존성 규칙

### Rule 2: Event는 domain 레이어 객체만 사용

```java
package com.company.order.domain.event;

import com.company.order.domain.vo.OrderId;
import com.company.order.domain.vo.CustomerId;
import com.company.order.domain.model.OrderLineItem;

import java.time.Instant;
import java.util.List;

/**
 * Order Created Event - domain 객체만 사용
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderCreated(
    OrderId orderId,                // ✅ domain Value Object
    CustomerId customerId,          // ✅ domain Value Object
    List<OrderLineItem> items,      // ✅ domain Entity
    Money totalAmount,              // ✅ domain Value Object
    Instant occurredAt              // ✅ Java 표준 타입
) implements OrderEvent {

    /**
     * ✅ 정적 팩토리 메서드
     */
    public static OrderCreated of(Order order) {
        return new OrderCreated(
            order.getId(),
            order.getCustomerId(),
            order.getItems(),
            order.getTotalAmount(),
            Instant.now()
        );
    }
}
```

### ❌ Bad - Application/Infrastructure 의존

```java
package com.company.order.domain.event;

import com.company.order.application.dto.OrderResponse;     // ❌ Application DTO
import com.company.order.infrastructure.s3.S3FileInfo;      // ❌ Infrastructure
import com.company.order.application.service.UserService;   // ❌ Application Service

/**
 * ❌ Bad - domain 레이어 벗어난 의존
 */
public record OrderCreated(
    OrderId orderId,
    OrderResponse orderInfo,    // ❌ Application DTO (순환 의존 위험)
    S3FileInfo fileInfo,        // ❌ Infrastructure 의존
    UserService userService,    // ❌ Service 의존 (절대 금지)
    Instant occurredAt
) implements OrderEvent {}

// 문제점:
// - domain → application 순환 의존
// - domain이 Infrastructure 알게 됨
// - Event가 Service 의존 (설계 오류)
```

---

## 🔧 모듈 간 의존성 관리

### Rule 3: 모듈 간 Event 구독 시 domain 모듈만 의존

```gradle
// order-domain/build.gradle
dependencies {
    // ✅ domain은 다른 모듈 의존 없음 (순수)
    implementation 'org.springframework.data:spring-data-jpa'
}

// order-application/build.gradle
dependencies {
    implementation project(':order-domain')           // ✅ 자신의 domain
    implementation project(':common-domain')          // ✅ 공통 domain
    // ❌ order-infrastructure 의존 금지
    // ❌ 다른 모듈 application 의존 금지
}

// inventory-application/build.gradle
dependencies {
    implementation project(':inventory-domain')       // ✅ 자신의 domain
    implementation project(':order-domain')           // ✅ Order Event 구독용
    implementation project(':common-domain')          // ✅ 공통 domain
    // ✅ order-domain만 의존 (order-application 의존 금지!)
}
```

**의존성 방향:**
```
inventory-application
    ↓ (의존)
order-domain (Event 정의)
    ↑ (발행)
order-application
```

---

## 📝 Long FK 기반 Event 설계

### Pattern: Entity 대신 Long ID 사용

```java
package com.company.order.domain.event;

/**
 * ✅ Good - Long FK 기반 Event
 *
 * - Entity 직접 참조 없음
 * - Law of Demeter 준수
 * - 직렬화 안전
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderCreated(
    OrderId orderId,
    Long customerId,           // ✅ Long FK (Customer Entity ID)
    Long shippingAddressId,    // ✅ Long FK (Address Entity ID)
    List<OrderLineItem> items,
    Money totalAmount,
    Instant occurredAt
) implements OrderEvent {

    /**
     * ✅ Aggregate에서 Long FK 추출
     */
    public static OrderCreated from(Order order) {
        return new OrderCreated(
            order.getId(),
            order.getCustomerId(),        // Long FK 반환
            order.getShippingAddressId(), // Long FK 반환
            order.getItems(),
            order.getTotalAmount(),
            Instant.now()
        );
    }
}
```

### ❌ Bad - Entity 직접 참조

```java
/**
 * ❌ Bad - Entity 직접 포함
 */
public record OrderCreated(
    OrderId orderId,
    Customer customer,         // ❌ Entity 직접 참조
    Address shippingAddress,   // ❌ Entity 직접 참조
    List<OrderLineItem> items,
    Instant occurredAt
) implements OrderEvent {}

// 문제점:
// 1. Law of Demeter 위반 위험: event.customer().getEmail()
// 2. 직렬화 문제: Entity 그래프 전체 직렬화
// 3. LazyInitializationException 위험
// 4. 영속성 컨텍스트 의존
```

---

## 🎯 실전 예제: 모듈 간 Event 전파

### Example 1: Order → Inventory 이벤트 전파

```java
/**
 * Order Module - Event 발행
 */
package com.company.order.application.service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ✅ Order 생성 → Event 발행
     */
    @Transactional
    public OrderId createOrder(CreateOrderCommand command) {
        // 1. Aggregate 생성
        Order order = Order.create(command.customerId(), command.items());

        // 2. 저장
        Order savedOrder = orderRepository.save(order);

        // 3. Event 발행 (트랜잭션 커밋 후)
        eventPublisher.publishEvent(OrderCreated.from(savedOrder));

        return savedOrder.getId();
    }
}
```

```java
/**
 * Inventory Module - Event 구독
 */
package com.company.inventory.application.event;

import com.company.order.domain.event.OrderCreated;  // ✅ order-domain 의존

@Component
public class OrderEventHandler {

    private final InventoryService inventoryService;
    private final LoadProductPort loadProductPort;

    /**
     * ✅ OrderCreated Event 수신
     *
     * - order-domain 모듈의 Event 구독
     * - Long FK로 Product 조회
     * - 별도 트랜잭션에서 실행
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreated event) {
        // ✅ Event에서 Long FK 추출
        List<Long> productIds = event.items().stream()
            .map(item -> item.getProductId())  // Long FK
            .toList();

        // ✅ 필요한 Entity 명시적 조회
        List<Product> products = loadProductPort.loadByIds(productIds);

        // ✅ 재고 차감 (별도 트랜잭션)
        inventoryService.decreaseStock(products, event.items());

        log.info("Inventory reserved for order: {}", event.orderId());
    }
}
```

---

### Example 2: Payment 실패 시 Order 보상

```java
/**
 * Payment Module - Event 발행
 */
package com.company.payment.domain.event;

public sealed interface PaymentEvent permits PaymentCompleted, PaymentFailed {}

public record PaymentFailed(
    PaymentId paymentId,
    OrderId orderId,      // ✅ Long FK (Order 참조)
    String reason,
    Instant occurredAt
) implements PaymentEvent {}
```

```java
/**
 * Order Module - Payment Event 구독
 */
package com.company.order.application.event;

import com.company.payment.domain.event.PaymentFailed;  // ✅ payment-domain 의존

@Component
public class PaymentEventHandler {

    private final OrderRepository orderRepository;

    /**
     * ✅ PaymentFailed Event 수신 → Order 취소
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailed event) {
        // ✅ Event의 Long FK로 Order 조회
        Order order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        // ✅ 보상 트랜잭션: Order 취소
        order.cancel("Payment failed: " + event.reason());

        orderRepository.save(order);

        log.warn("Order cancelled due to payment failure: {}", event.orderId());
    }
}
```

---

## 🔧 Common Event 처리 전략

### Pattern 1: Common Domain 모듈

여러 모듈에서 공통으로 사용하는 Event는 `common-domain` 모듈에 배치:

```
root-project/
├── common-domain/
│   └── src/main/java/
│       └── com/company/common/domain/
│           └── event/
│               ├── DomainEvent.java       # 공통 인터페이스
│               └── EventMetadata.java     # 공통 메타데이터
│
├── order-domain/
│   └── dependencies: common-domain
│
└── inventory-domain/
    └── dependencies: common-domain
```

```java
package com.company.common.domain.event;

/**
 * 공통 Domain Event 인터페이스
 *
 * @author development-team
 * @since 1.0.0
 */
public interface DomainEvent {

    /**
     * Event 발생 시각
     */
    Instant occurredAt();

    /**
     * Event 타입
     */
    default String eventType() {
        return this.getClass().getSimpleName();
    }
}
```

```java
package com.company.order.domain.event;

import com.company.common.domain.event.DomainEvent;

/**
 * Order Event - 공통 인터페이스 확장
 */
public sealed interface OrderEvent extends DomainEvent
    permits OrderCreated, OrderCancelled, OrderShipped {}
```

---

### Pattern 2: Event Metadata 분리

```java
package com.company.common.domain.event;

/**
 * Event 메타데이터
 *
 * - 모든 Event에 공통으로 필요한 정보
 * - 감사, 추적용
 *
 * @author development-team
 * @since 1.0.0
 */
public record EventMetadata(
    String eventId,          // UUID
    String eventType,        // 클래스명
    Instant occurredAt,      // 발생 시각
    String correlationId,    // 요청 추적 ID
    String causationId       // 원인 Event ID
) {
    public static EventMetadata create(String eventType) {
        return new EventMetadata(
            UUID.randomUUID().toString(),
            eventType,
            Instant.now(),
            MDC.get("correlationId"),
            null
        );
    }
}
```

```java
package com.company.order.domain.event;

import com.company.common.domain.event.EventMetadata;

/**
 * Event에 메타데이터 포함
 */
public record OrderCreated(
    OrderId orderId,
    Long customerId,
    List<OrderLineItem> items,
    Money totalAmount,
    EventMetadata metadata  // ✅ 공통 메타데이터
) implements OrderEvent {

    public static OrderCreated of(Order order) {
        return new OrderCreated(
            order.getId(),
            order.getCustomerId(),
            order.getItems(),
            order.getTotalAmount(),
            EventMetadata.create("OrderCreated")
        );
    }

    @Override
    public Instant occurredAt() {
        return metadata.occurredAt();
    }
}
```

---

## 📋 멀티모듈 Event 설계 체크리스트

### 패키지 배치
- [ ] Event는 반드시 `{모듈}-domain/event` 패키지에 배치
- [ ] Event Handler는 `{모듈}-application/event` 패키지에 배치
- [ ] 공통 Event는 `common-domain/event` 패키지에 배치

### 의존성 관리
- [ ] Event는 domain 레이어 객체만 사용
- [ ] Event에서 Application DTO, Infrastructure 타입 사용 금지
- [ ] 다른 모듈 Event 구독 시 해당 domain 모듈만 의존
- [ ] application 모듈 간 직접 의존 금지

### Long FK 전략
- [ ] Event에 Entity 직접 참조 금지
- [ ] Entity 대신 Long 타입 ID 사용
- [ ] Event Handler에서 필요한 Entity 명시적 조회

### Event 설계
- [ ] Sealed Interface로 Event 계층 구조 정의
- [ ] Record 사용 (불변 객체)
- [ ] 정적 팩토리 메서드 제공 (`of()`, `from()`)

### 트랜잭션 경계
- [ ] Event 발행은 트랜잭션 내부 (`@Transactional`)
- [ ] Event Handler는 별도 트랜잭션 (`@TransactionalEventListener`)
- [ ] Event Handler에서 외부 API 호출은 트랜잭션 밖

---

## 🔍 의존성 검증

### Gradle 의존성 체크

```gradle
// order-domain/build.gradle
dependencies {
    // ✅ domain은 다른 모듈 의존 없음
    implementation 'org.springframework.data:spring-data-jpa'
    implementation project(':common-domain')  // 공통 domain만 허용
}

// order-application/build.gradle
dependencies {
    implementation project(':order-domain')
    implementation project(':common-domain')

    // ❌ 다음 의존 금지
    // implementation project(':order-infrastructure')
    // implementation project(':inventory-application')
}

// inventory-application/build.gradle
dependencies {
    implementation project(':inventory-domain')
    implementation project(':order-domain')      // ✅ Event 구독용
    implementation project(':common-domain')

    // ❌ order-application 의존 금지!
}
```

**의존성 규칙:**
1. `domain` → 다른 모듈 의존 없음 (common-domain 제외)
2. `application` → 자신의 domain + 구독할 Event의 domain
3. `application` → 다른 모듈의 application 의존 절대 금지

---

## 🚨 자주 하는 실수

### 실수 1: Event에 Application DTO 포함

```java
// ❌ Bad
package com.company.order.domain.event;

import com.company.order.application.dto.OrderResponse;  // ❌

public record OrderCreated(
    OrderId orderId,
    OrderResponse orderInfo,  // ❌ domain → application 순환 의존
    Instant occurredAt
) implements OrderEvent {}
```

**해결:**
```java
// ✅ Good - domain 객체만 사용
public record OrderCreated(
    OrderId orderId,
    Long customerId,
    Money totalAmount,
    Instant occurredAt
) implements OrderEvent {}
```

---

### 실수 2: application 모듈 간 직접 의존

```gradle
// ❌ Bad - inventory-application/build.gradle
dependencies {
    implementation project(':order-application')  // ❌ application 간 의존
}
```

**해결:**
```gradle
// ✅ Good
dependencies {
    implementation project(':order-domain')  // ✅ domain만 의존
}
```

---

### 실수 3: Event Handler를 domain에 배치

```java
// ❌ Bad - order-domain/src/.../event/OrderEventHandler.java
package com.company.order.domain.event;

@Component  // ❌ domain에 Spring 의존
public class OrderEventHandler {
    // domain 레이어에 Handler 금지!
}
```

**해결:**
```java
// ✅ Good - order-application/src/.../event/OrderEventHandler.java
package com.company.order.application.event;

@Component  // ✅ application 레이어에 배치
public class OrderEventHandler {
    // Event Handler는 application에만!
}
```

---

## 📚 관련 가이드

**전제 조건**:
- [Domain Events](./01_domain-events.md) - Event 기본 개념
- [Long FK Strategy](../../04-persistence-layer/jpa-entity-design/01_long-fk-strategy.md) - Long FK 전략
- [Domain Package Guide](../../02-domain-layer/package-guide/01_domain_package_guide.md) - 패키지 구조

**연관 패턴**:
- [Event Modeling](../../06-java21-patterns/sealed-classes/02_event-modeling.md) - Sealed Classes 활용
- [Transaction Boundaries](../../03-application-layer/transaction-management/01_transaction-boundaries.md) - 트랜잭션 경계

**심화 학습**:
- [Saga Pattern](./03_saga-pattern.md) - 모듈 간 분산 트랜잭션
- [Event Sourcing](./02_event-sourcing.md) - Event Store 구현

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
