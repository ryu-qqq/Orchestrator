# Command UseCase - 상태 변경 유스케이스

**목적**: 명령형 유스케이스 설계 패턴 (Command/Response 내부 Record 포함)

**위치**: `application/[context]/port/in/`

**관련 문서**:
- [Query UseCase](./02_query-usecase.md)
- [UseCase 내부 DTO](../assembler-pattern/02_usecase-inner-dto.md)
- [Assembler Responsibility](../assembler-pattern/01_assembler-responsibility.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### Command UseCase란?

1. **상태 변경**: Create, Update, Delete
2. **최소 반환**: ID 또는 Response (내부 Record)
3. **트랜잭션**: Service 구현체에서 `@Transactional`
4. **내부 DTO**: Command/Response를 내부 Record로 정의

---

## ❌ Command UseCase 안티패턴

```java
// ❌ Before - Command/Response 외부 클래스
public record CreateOrderCommand(...) {}  // 별도 파일
public record CreateOrderResponse(...) {}  // 별도 파일

public interface CreateOrderUseCase {
    CreateOrderResponse createOrder(CreateOrderCommand command);
}
```

---

## ✅ Command UseCase 패턴

```java
package com.company.application.order.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Create Order UseCase
 *
 * @author development-team
 * @since 1.0.0
 */
public interface CreateOrderUseCase {

    /**
     * ✅ Command 실행 → Response 반환
     */
    Response createOrder(Command command);

    /**
     * ✅ Command - 내부 Record
     */
    record Command(
        Long customerId,
        List<OrderItem> items,
        String notes
    ) {
        public Command {
            if (customerId == null || customerId <= 0) {
                throw new IllegalArgumentException("Invalid customer ID");
            }

            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Order items cannot be empty");
            }

            items = List.copyOf(items);
        }

        public record OrderItem(
            Long productId,
            Integer quantity,
            Long unitPrice
        ) {
            public OrderItem {
                if (productId == null || productId <= 0) {
                    throw new IllegalArgumentException("Invalid product ID");
                }

                if (quantity == null || quantity <= 0) {
                    throw new IllegalArgumentException("Quantity must be positive");
                }
            }
        }
    }

    /**
     * ✅ Response - 내부 Record
     */
    record Response(
        Long orderId,
        String status,
        Long totalAmount,
        Instant createdAt
    ) {}
}
```

---

## ✅ Service 구현체 (Assembler 사용)

```java
package com.company.application.order.service.command;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.application.order.port.out.SaveOrderPort;
import com.company.application.order.assembler.OrderAssembler;
import com.company.domain.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create Order Service (구현체)
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderAssembler orderAssembler;
    private final SaveOrderPort saveOrderPort;

    public CreateOrderService(
        OrderAssembler orderAssembler,
        SaveOrderPort saveOrderPort
    ) {
        this.orderAssembler = orderAssembler;
        this.saveOrderPort = saveOrderPort;
    }

    @Override
    public Response createOrder(Command command) {
        // ✅ 1. Assembler: Command → Domain
        Order order = orderAssembler.toDomain(command);

        // ✅ 2. Port: Domain 저장
        Order savedOrder = saveOrderPort.save(order);

        // ✅ 3. Assembler: Domain → Response
        return orderAssembler.toResponse(savedOrder);
    }
}
```

---

## ✅ Update UseCase 패턴

```java
/**
 * Update Order Status UseCase
 */
public interface UpdateOrderStatusUseCase {

    /**
     * ✅ void 반환 (상태 변경만)
     */
    void updateOrderStatus(Command command);

    /**
     * ✅ Command - 내부 Record
     */
    record Command(
        Long orderId,
        String newStatus,
        String reason
    ) {
        public Command {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("Invalid order ID");
            }

            if (newStatus == null || newStatus.isBlank()) {
                throw new IllegalArgumentException("Status is required");
            }
        }
    }
}

@Service
@Transactional
public class UpdateOrderStatusService implements UpdateOrderStatusUseCase {

    private final LoadOrderPort loadOrderPort;
    private final SaveOrderPort saveOrderPort;

    @Override
    public void updateOrderStatus(Command command) {
        Order order = loadOrderPort.load(command.orderId()).orElseThrow();

        order.updateStatus(OrderStatus.valueOf(command.newStatus()));

        saveOrderPort.save(order);
    }
}
```

---

## 📋 Command UseCase 체크리스트

- [ ] Command/Response를 **내부 Record**로 정의했는가?
- [ ] Service에서 **Assembler** 사용하는가?
- [ ] 상태 변경만 수행하는가?
- [ ] `@Transactional` 적용되어 있는가? (Service 구현체)
- [ ] Domain Event 발행하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
