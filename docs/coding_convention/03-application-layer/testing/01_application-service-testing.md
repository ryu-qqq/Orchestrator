# Application Service Testing - UseCase 구현체 테스트

**목적**: Application Service (UseCase 구현)의 오케스트레이션 로직 검증

**위치**: `application/service/` (UseCase 구현체)

**관련 문서**:
- [UseCase Design](../usecase-design/)
- [Transaction Management](../transaction-management/)
- [Domain Aggregate Testing](../../02-domain-layer/testing/01_aggregate-testing.md)

**검증 도구**: JUnit 5, Mockito (Port Mocking), Spring Test (선택적)

---

## 📌 핵심 원칙

### Application Service 특징

1. **Stateless**: 상태를 가지지 않음
2. **오케스트레이션**: 여러 Port와 Domain을 조율
3. **트랜잭션 경계**: `@Transactional` 관리
4. **Port 의존**: Inbound Port 구현, Outbound Port 사용
5. **DTO 변환**: Command/Query → Domain, Domain → Response

---

## ✅ Application Service 테스트 패턴

### 패턴 1: Port Mocking (순수 단위 테스트)

```java
package com.company.application.service;

import com.company.domain.order.*;
import com.company.domain.payment.*;
import com.company.application.port.in.*;
import com.company.application.port.out.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * CreateOrderService 테스트 (UseCase 구현)
 *
 * @author development-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    @Mock
    private LoadCustomerPort loadCustomerPort;

    @Mock
    private SaveOrderPort saveOrderPort;

    @Mock
    private PublishEventPort publishEventPort;

    @InjectMocks
    private CreateOrderService createOrderService;

    @Test
    void createOrder_WhenValidCommand_ShouldCreateAndSaveOrder() {
        // Given
        CreateOrderCommand command = new CreateOrderCommand(
            CustomerId.of(1L),
            List.of(new OrderItemCommand(ProductId.of(101L), Quantity.of(2)))
        );
        Customer customer = Customer.create(CustomerId.of(1L), "John Doe");

        given(loadCustomerPort.loadCustomer(command.customerId()))
            .willReturn(Optional.of(customer));

        // When
        OrderId orderId = createOrderService.createOrder(command);

        // Then
        assertThat(orderId).isNotNull();
        verify(saveOrderPort).saveOrder(any(Order.class));
        verify(publishEventPort).publish(any(OrderCreatedEvent.class));
    }

    @Test
    void createOrder_WhenCustomerNotFound_ShouldThrowException() {
        // Given
        CreateOrderCommand command = new CreateOrderCommand(
            CustomerId.of(999L),
            List.of(new OrderItemCommand(ProductId.of(101L), Quantity.of(2)))
        );

        given(loadCustomerPort.loadCustomer(command.customerId()))
            .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> createOrderService.createOrder(command))
            .isInstanceOf(CustomerNotFoundException.class)
            .hasMessageContaining("Customer not found: 999");
    }
}
```

---

### 패턴 2: 트랜잭션 경계 테스트

```java
/**
 * ApproveOrderService 테스트 (트랜잭션 포함)
 *
 * @author development-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ApproveOrderServiceTest {

    @Mock
    private LoadOrderPort loadOrderPort;

    @Mock
    private LoadPaymentPort loadPaymentPort;

    @Mock
    private SaveOrderPort saveOrderPort;

    @Mock
    private PublishEventPort publishEventPort;

    @InjectMocks
    private ApproveOrderService approveOrderService;

    @Test
    void approveOrder_WhenPaymentCompleted_ShouldApproveAndPublishEvent() {
        // Given
        OrderId orderId = OrderId.of(1L);
        Order order = Order.create(CustomerId.of(1L));
        Payment payment = Payment.create(orderId, Money.of(1000));
        payment.complete();

        given(loadOrderPort.loadOrder(orderId)).willReturn(Optional.of(order));
        given(loadPaymentPort.loadPayment(orderId)).willReturn(Optional.of(payment));

        // When
        approveOrderService.approveOrder(new ApproveOrderCommand(orderId));

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
        verify(saveOrderPort).saveOrder(order);
        verify(publishEventPort).publish(argThat(event ->
            event instanceof OrderApprovedEvent &&
            ((OrderApprovedEvent) event).orderId().equals(orderId)
        ));
    }

    @Test
    void approveOrder_WhenPaymentNotCompleted_ShouldThrowException() {
        // Given
        OrderId orderId = OrderId.of(1L);
        Order order = Order.create(CustomerId.of(1L));
        Payment payment = Payment.create(orderId, Money.of(1000));
        // payment.complete() 호출 안 함 - PENDING 상태

        given(loadOrderPort.loadOrder(orderId)).willReturn(Optional.of(order));
        given(loadPaymentPort.loadPayment(orderId)).willReturn(Optional.of(payment));

        // When & Then
        assertThatThrownBy(() -> approveOrderService.approveOrder(new ApproveOrderCommand(orderId)))
            .isInstanceOf(PaymentNotCompletedException.class)
            .hasMessageContaining("Payment must be completed before approval");

        // 트랜잭션 롤백 시뮬레이션: saveOrder 호출되지 않음
        verify(saveOrderPort, never()).saveOrder(any());
        verify(publishEventPort, never()).publish(any());
    }
}
```

---

### 패턴 3: DTO 변환 검증

```java
/**
 * GetOrderQuery 테스트 (조회 UseCase)
 *
 * @author development-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class GetOrderQueryTest {

    @Mock
    private LoadOrderPort loadOrderPort;

    @InjectMocks
    private GetOrderQueryService getOrderQueryService;

    @Test
    void getOrder_ShouldReturnOrderResponse() {
        // Given
        OrderId orderId = OrderId.of(1L);
        Order order = Order.create(CustomerId.of(1L));
        order.addItem(ProductId.of(101L), Quantity.of(2), Money.of(1000));

        given(loadOrderPort.loadOrder(orderId)).willReturn(Optional.of(order));

        // When
        OrderResponse response = getOrderQueryService.getOrder(new GetOrderQuery(orderId));

        // Then
        assertThat(response.orderId()).isEqualTo(orderId.value());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalPrice()).isEqualTo(2000);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getOrder_WhenNotFound_ShouldThrowException() {
        // Given
        OrderId orderId = OrderId.of(999L);
        given(loadOrderPort.loadOrder(orderId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> getOrderQueryService.getOrder(new GetOrderQuery(orderId)))
            .isInstanceOf(OrderNotFoundException.class);
    }
}
```

---

## 📋 Application Service 테스트 체크리스트

- [ ] Outbound Port Mocking (Repository, External API)
- [ ] 오케스트레이션 로직 검증 (여러 Port 조율)
- [ ] 트랜잭션 경계 검증 (성공 시 저장, 실패 시 롤백)
- [ ] DTO 변환 검증 (Command → Domain, Domain → Response)
- [ ] 예외 시나리오 (Not Found, Validation Failure)
- [ ] Domain Event 발행 검증

---

## 🚫 Anti-Pattern

### ❌ Domain 로직을 Application Service에서 테스트

```java
// ❌ Application Service에서 도메인 로직 검증
@Test
void approveOrder_ShouldCalculateTotalPrice() {
    Order order = Order.create(CustomerId.of(1L));
    order.addItem(ProductId.of(101L), Quantity.of(2), Money.of(1000));

    assertThat(order.getTotalPrice()).isEqualTo(Money.of(2000)); // ❌ 도메인 테스트!
}
```

**올바른 방법**: 도메인 로직은 `01_aggregate-testing.md`에 따라 도메인 레이어에서 테스트

---

## 🔄 Domain vs Application Service 테스트 구분

| 구분 | Domain Test | Application Service Test |
|------|-------------|--------------------------|
| **목적** | 비즈니스 규칙 검증 | 오케스트레이션 검증 |
| **위치** | `domain/` | `application/service/` |
| **의존성** | 없음 (POJO) | Port Mocking |
| **검증** | 상태 전이, 불변식 | Port 호출, DTO 변환 |
| **예시** | `order.approve()` | `approveOrderService.execute()` |

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
