# Query UseCase - 조회 유스케이스

**목적**: 조회 전용 유스케이스 설계 패턴 (Query/Response 내부 Record 포함)

**위치**: `application/[context]/port/in/`

**관련 문서**:
- [Command UseCase](./01_command-usecase.md)
- [UseCase 내부 DTO](../assembler-pattern/02_usecase-inner-dto.md)
- [Assembler Responsibility](../assembler-pattern/01_assembler-responsibility.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### Query UseCase란?

1. **조회만**: 부작용 없음
2. **Response 반환**: 내부 Record로 정의
3. **읽기 전용**: Service에서 `@Transactional(readOnly = true)`
4. **내부 DTO**: Query/Response를 내부 Record로 정의

---

## ❌ Query UseCase 안티패턴

```java
// ❌ Before - Entity 직접 반환
public interface GetOrderUseCase {
    Order getOrder(Long orderId);  // ❌ Entity 노출
}
```

---

## ✅ Query UseCase 패턴

```java
package com.company.application.order.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Get Order UseCase
 *
 * @author development-team
 * @since 1.0.0
 */
public interface GetOrderUseCase {

    /**
     * ✅ Query 실행 → Response 반환
     */
    Response getOrder(Query query);

    /**
     * ✅ Query - 내부 Record (조회 조건)
     */
    record Query(
        Long orderId
    ) {
        public Query {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("Invalid order ID");
            }
        }
    }

    /**
     * ✅ Response - 내부 Record (조회 결과)
     */
    record Response(
        Long orderId,
        CustomerInfo customer,
        List<LineItem> items,
        Long totalAmount,
        String status,
        Instant createdAt
    ) {
        public record CustomerInfo(
            Long customerId,
            String customerName,
            String email
        ) {}

        public record LineItem(
            Long productId,
            String productName,
            Integer quantity,
            Long unitPrice,
            Long subtotal
        ) {}
    }
}
```

---

## ✅ Service 구현체 (Assembler 사용)

```java
package com.company.application.order.service.query;

import com.company.application.order.port.in.GetOrderUseCase;
import com.company.application.order.port.out.LoadOrderPort;
import com.company.application.order.assembler.OrderAssembler;
import com.company.domain.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Get Order Service (구현체)
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
@Transactional(readOnly = true)
public class GetOrderService implements GetOrderUseCase {

    private final OrderAssembler orderAssembler;
    private final LoadOrderPort loadOrderPort;

    public GetOrderService(
        OrderAssembler orderAssembler,
        LoadOrderPort loadOrderPort
    ) {
        this.orderAssembler = orderAssembler;
        this.loadOrderPort = loadOrderPort;
    }

    @Override
    public Response getOrder(Query query) {
        // ✅ 1. Port: Domain 조회
        Order order = loadOrderPort.load(query.orderId())
            .orElseThrow(() -> new OrderNotFoundException(query.orderId()));

        // ✅ 2. Assembler: Domain → Response
        return orderAssembler.toGetResponse(order);
    }
}
```

---

## ✅ Search UseCase 패턴

```java
/**
 * Search Orders UseCase
 */
public interface SearchOrdersUseCase {

    /**
     * ✅ Page<DTO> 반환
     */
    Page<OrderSummaryResponse> searchOrders(SearchOrdersQuery query);
}

@Service
@Transactional(readOnly = true)
public class SearchOrdersService implements SearchOrdersUseCase {

    private final OrderRepository orderRepository;

    @Override
    public Page<OrderSummaryResponse> searchOrders(SearchOrdersQuery query) {
        Page<Order> orders = orderRepository.search(query);

        return orders.map(OrderSummaryResponse::from);
    }
}
```

---

## 📋 Query UseCase 체크리스트

- [ ] Query/Response를 **내부 Record**로 정의했는가?
- [ ] Service에서 **Assembler** 사용하는가?
- [ ] 조회만 수행하는가? (부작용 없음)
- [ ] `@Transactional(readOnly = true)` 적용되어 있는가? (Service 구현체)
- [ ] N+1 문제 방지하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
