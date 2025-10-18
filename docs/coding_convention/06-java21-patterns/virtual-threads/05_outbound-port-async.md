# Outbound Port Async Pattern - 외부 API 비동기 호출

**목적**: Hexagonal Architecture의 Outbound Port에서 Virtual Threads를 활용한 외부 API 비동기 호출

**관련 문서**:
- [Virtual Threads Basics](./01_virtual-threads-basics.md)
- [Async Processing](./02_async-processing.md)
- [Spring Integration](./04_spring-integration.md)
- [Repository Pattern](../../../04-persistence-layer/repository-pattern/01_repository-implementation.md)

**필수 버전**: Spring Boot 3.2+, Java 21+

---

## 📌 핵심 원칙

### Outbound Port Async 패턴

1. **Port (인터페이스)**: Domain Layer에 위치, Framework 독립
2. **Adapter (구현체)**: Application Layer, Virtual Threads로 비동기 처리
3. **트랜잭션 경계**: 외부 API 호출은 트랜잭션 밖에서
4. **에러 처리**: Result Type 또는 Circuit Breaker 패턴

---

## 🏗️ 아키텍처 레이어

```
┌──────────────────────────────────────────────────┐
│  Domain Layer (Framework 독립)                    │
│  - Outbound Port (Interface)                    │
│  - 외부 API 호출 계약 정의                          │
└──────────────────────────────────────────────────┘
                      ↕ (구현)
┌──────────────────────────────────────────────────┐
│  Application Layer (Adapter)                     │
│  - Outbound Port Adapter (구현체)                 │
│  - Virtual Threads로 비동기 처리                   │
│  - RestClient, WebClient, HttpClient 사용        │
└──────────────────────────────────────────────────┘
```

---

## ❌ 안티패턴 - 트랜잭션 내 외부 API 호출

### 문제점: @Transactional 내에서 외부 API 호출

```java
// ❌ Before - 트랜잭션 내 외부 API 호출
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestClient restClient;  // 외부 API

    /**
     * ❌ 문제점:
     * - 외부 API 호출이 트랜잭션 내부
     * - API 응답 지연 시 트랜잭션 길어짐
     * - DB Connection 낭비
     */
    public void createOrder(CreateOrderCommand command) {
        // ✅ 1. Order 저장 (트랜잭션)
        Order order = Order.create(command.customerId(), command.items());
        orderRepository.save(order);

        // ❌ 2. 외부 API 호출 (트랜잭션 내!)
        restClient.post()
            .uri("/api/inventory/reserve")
            .body(order.getItems())
            .retrieve()
            .toBodilessEntity();  // ❌ 3-5초 소요 가능

        // ❌ 3. 이메일 발송 (트랜잭션 내!)
        restClient.post()
            .uri("/api/email/send")
            .body(order)
            .retrieve()
            .toBodilessEntity();  // ❌ 2-3초 소요 가능
    }
}
```

**문제점**:
- ❌ 외부 API 호출이 트랜잭션 내부 (5-10초 소요 가능)
- ❌ DB Connection 장시간 점유
- ❌ 트랜잭션 타임아웃 위험
- ❌ 외부 API 실패 시 트랜잭션 롤백

---

## ✅ 권장 패턴 - Outbound Port with Virtual Threads

### 패턴 1: Outbound Port 정의 (Domain Layer)

```java
package com.company.domain.order.port.out;

import com.company.domain.order.OrderLineItem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Inventory Port (Outbound)
 *
 * - Domain Layer에 위치 (Framework 독립)
 * - 외부 재고 시스템 연동 계약
 * - CompletableFuture로 비동기 처리
 *
 * @author development-team
 * @since 1.0.0
 */
public interface InventoryPort {

    /**
     * ✅ 재고 예약 (비동기)
     *
     * - CompletableFuture 반환으로 비동기 처리
     * - Virtual Thread에서 실행
     */
    CompletableFuture<Void> reserveStock(List<OrderLineItem> items);

    /**
     * ✅ 재고 확인 (비동기)
     */
    CompletableFuture<Boolean> checkAvailability(List<OrderLineItem> items);

    /**
     * ✅ 재고 취소 (비동기)
     */
    CompletableFuture<Void> cancelReservation(Long orderId);
}
```

---

### 패턴 2: Outbound Port Adapter (Application Layer)

```java
package com.company.application.order.adapter.out.inventory;

import com.company.domain.order.OrderLineItem;
import com.company.domain.order.port.out.InventoryPort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Inventory Adapter (Outbound)
 *
 * - InventoryPort 구현체
 * - RestClient로 외부 API 호출
 * - Virtual Threads로 비동기 처리
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class InventoryAdapter implements InventoryPort {

    private final RestClient restClient;

    public InventoryAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .baseUrl("https://api.inventory.example.com")
            .build();
    }

    /**
     * ✅ @Async로 Virtual Thread에서 실행
     *
     * - 트랜잭션 밖에서 외부 API 호출
     * - CompletableFuture로 비동기 결과 반환
     * - Domain OrderLineItem을 그대로 사용 (Adapter는 외부 API 호출만 담당)
     */
    @Async
    @Override
    public CompletableFuture<Void> reserveStock(List<OrderLineItem> items) {
        try {
            // ✅ RestClient로 외부 API 호출
            // ✅ Domain 객체를 직접 전송 (필요 시 외부 API 스펙에 맞게 자동 변환됨)
            restClient.post()
                .uri("/api/v1/inventory/reserve")
                .body(items)
                .retrieve()
                .toBodilessEntity();

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * ✅ 재고 확인 (비동기)
     */
    @Async
    @Override
    public CompletableFuture<Boolean> checkAvailability(List<OrderLineItem> items) {
        try {
            Boolean available = restClient.post()
                .uri("/api/v1/inventory/check")
                .body(items)
                .retrieve()
                .body(Boolean.class);

            return CompletableFuture.completedFuture(available != null && available);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * ✅ 재고 취소 (비동기)
     */
    @Async
    @Override
    public CompletableFuture<Void> cancelReservation(Long orderId) {
        try {
            restClient.delete()
                .uri("/api/v1/inventory/reserve/{orderId}", orderId)
                .retrieve()
                .toBodilessEntity();

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
```

**핵심 포인트**:
- ✅ `@Async`로 Virtual Thread에서 실행
- ✅ `CompletableFuture`로 비동기 결과 반환
- ✅ RestClient 사용 (Spring Boot 3.2+)
- ✅ 외부 API 호출은 트랜잭션 밖

---

### 패턴 3: UseCase Service에서 Outbound Port 사용

```java
package com.company.application.order.service.command;

import com.company.application.order.port.in.CreateOrderUseCase;
import com.company.application.order.port.out.SaveOrderPort;
import com.company.domain.order.Order;
import com.company.domain.order.port.out.InventoryPort;
import com.company.domain.order.port.out.NotificationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * Create Order Service
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderAssembler orderAssembler;
    private final SaveOrderPort saveOrderPort;
    private final InventoryPort inventoryPort;
    private final NotificationPort notificationPort;

    private final OrderPersistenceService orderPersistenceService;

    /**
     * ✅ 트랜잭션 분리: Order 저장 vs 외부 API 호출
     *
     * - orderPersistenceService.save()는 별도 빈의 @Transactional 메서드
     * - processExternalApisAsync()는 트랜잭션 밖에서 실행
     */
    @Override
    public Response createOrder(Command command) {
        // ✅ 1. 트랜잭션 내: Order 저장 (별도 빈 호출 → 프록시 작동)
        Order savedOrder = orderPersistenceService.saveOrder(command);

        // ✅ 2. 트랜잭션 외: 외부 API 비동기 호출
        processExternalApisAsync(savedOrder);

        // ✅ 3. Response 반환 (즉시)
        return orderAssembler.toResponse(savedOrder);
    }

    /**
     * ✅ 트랜잭션 외: 외부 API 비동기 호출
     *
     * - 재고 예약, 알림 발송은 비동기 처리
     * - CompletableFuture.allOf()로 모든 작업 완료 대기 (선택적)
     */
    private void processExternalApisAsync(Order order) {
        // ✅ 병렬 실행: 재고 예약 + 알림 발송
        CompletableFuture<Void> inventoryFuture =
            inventoryPort.reserveStock(order.getItems());

        CompletableFuture<Void> notificationFuture =
            notificationPort.sendOrderConfirmation(order.getId(), order.getCustomerId());

        // ✅ (선택) 모든 작업 완료 대기 (필요 시)
        // CompletableFuture.allOf(inventoryFuture, notificationFuture).join();

        // ✅ 또는 Fire-and-Forget (대기 없이 즉시 반환)
    }
}

/**
 * Order Persistence Service
 *
 * - @Transactional 메서드를 가진 별도 빈
 * - Spring AOP 프록시가 정상 작동하도록 분리
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
class OrderPersistenceService {

    private final OrderAssembler orderAssembler;
    private final SaveOrderPort saveOrderPort;

    public OrderPersistenceService(
        OrderAssembler orderAssembler,
        SaveOrderPort saveOrderPort
    ) {
        this.orderAssembler = orderAssembler;
        this.saveOrderPort = saveOrderPort;
    }

    /**
     * ✅ @Transactional이 public 메서드에 적용 (프록시 정상 작동)
     *
     * - 외부 빈(CreateOrderService)에서 호출
     * - Spring AOP 프록시가 트랜잭션 관리
     */
    @Transactional
    public Order saveOrder(CreateOrderUseCase.Command command) {
        Order order = orderAssembler.toDomain(command);
        return saveOrderPort.save(order);
    }
}
```

**핵심 포인트**:
- ✅ **트랜잭션 내**: Order 저장만 (1-2초)
- ✅ **트랜잭션 외**: 외부 API 호출 (비동기)
- ✅ 병렬 실행: `inventoryPort`, `notificationPort` 동시 호출
- ✅ Fire-and-Forget: 외부 API 완료 대기 없이 즉시 반환

---

## 🎯 실전 예제: StructuredTaskScope

### 패턴 1: 여러 외부 API 병렬 호출

```java
package com.company.application.order.service.query;

import com.company.domain.order.port.out.CustomerPort;
import com.company.domain.order.port.out.ProductPort;
import com.company.domain.order.port.out.InventoryPort;
import org.springframework.stereotype.Service;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.Future;

/**
 * Order Enrichment Service
 *
 * - 여러 외부 API 병렬 호출
 * - StructuredTaskScope로 구조화된 동시성
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class OrderEnrichmentService {

    private final CustomerPort customerPort;
    private final ProductPort productPort;
    private final InventoryPort inventoryPort;

    /**
     * ✅ StructuredTaskScope로 여러 API 병렬 호출
     */
    public EnrichedOrderResponse enrichOrder(Long orderId, Long customerId, List<Long> productIds) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // ✅ 병렬 실행: Customer, Product, Inventory 조회
            Future<Customer> customerFuture = scope.fork(() ->
                customerPort.getCustomer(customerId).join()
            );

            Future<List<Product>> productsFuture = scope.fork(() ->
                productPort.getProducts(productIds).join()
            );

            Future<List<Inventory>> inventoryFuture = scope.fork(() ->
                inventoryPort.checkStockLevels(productIds).join()
            );

            // ✅ 모든 작업 완료 대기
            scope.join();
            scope.throwIfFailed();

            // ✅ 결과 조합
            return new EnrichedOrderResponse(
                orderId,
                customerFuture.resultNow(),
                productsFuture.resultNow(),
                inventoryFuture.resultNow()
            );
        } catch (Exception e) {
            throw new EnrichmentFailedException("Failed to enrich order", e);
        }
    }
}
```

---

### 패턴 2: WebClient (Reactive) vs RestClient (Blocking)

```java
/**
 * ✅ RestClient (권장 - Virtual Threads와 함께)
 *
 * - Blocking API이지만 Virtual Thread에서 실행되므로 성능 우수
 * - Spring Boot 3.2+에서 권장
 * - 코드 간결성
 */
@Component
public class RestClientInventoryAdapter implements InventoryPort {

    private final RestClient restClient;

    @Async
    @Override
    public CompletableFuture<Void> reserveStock(List<OrderLineItem> items) {
        try {
            // ✅ Blocking call이지만 Virtual Thread에서 실행
            restClient.post()
                .uri("/api/inventory/reserve")
                .body(items)
                .retrieve()
                .toBodilessEntity();

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}

/**
 * ✅ WebClient (Reactive - 선택적)
 *
 * - Non-blocking Reactive API
 * - 복잡한 Reactive 파이프라인 필요 시 사용
 * - Virtual Threads와 함께 사용 시 오버헤드 있을 수 있음
 */
@Component
public class WebClientInventoryAdapter implements InventoryPort {

    private final WebClient webClient;

    @Async
    @Override
    public CompletableFuture<Void> reserveStock(List<OrderLineItem> items) {
        return webClient.post()
            .uri("/api/inventory/reserve")
            .bodyValue(items)
            .retrieve()
            .toBodilessEntity()
            .toFuture();  // ✅ Mono → CompletableFuture 변환
    }
}
```

**핵심 포인트**:
- ✅ **RestClient 권장**: Virtual Threads와 함께 사용 시 간결하고 성능 우수
- ✅ **WebClient**: Reactive 파이프라인 필요 시만 사용
- ✅ 둘 다 Virtual Thread에서 실행 가능

---

## 🔧 고급 패턴

### 패턴 1: Retry with Exponential Backoff

```java
/**
 * ✅ Retry Logic (Exponential Backoff)
 */
@Component
public class ResilientInventoryAdapter implements InventoryPort {

    private final RestClient restClient;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 100;

    @Async
    @Override
    public CompletableFuture<Void> reserveStock(List<OrderLineItem> items) {
        return retryWithBackoff(() -> {
            restClient.post()
                .uri("/api/inventory/reserve")
                .body(items)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    /**
     * ✅ Exponential Backoff Retry
     */
    private <T> CompletableFuture<T> retryWithBackoff(Supplier<T> operation) {
        int attempts = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (attempts < MAX_RETRIES) {
            try {
                T result = operation.get();
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                attempts++;
                if (attempts >= MAX_RETRIES) {
                    return CompletableFuture.failedFuture(e);
                }

                // ✅ Exponential Backoff
                try {
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return CompletableFuture.failedFuture(ie);
                }
            }
        }

        return CompletableFuture.failedFuture(new RuntimeException("Max retries exceeded"));
    }
}
```

---

### 패턴 2: Circuit Breaker (Resilience4j)

```java
/**
 * ✅ Circuit Breaker Pattern
 */
@Component
public class CircuitBreakerInventoryAdapter implements InventoryPort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerInventoryAdapter(
        RestClient.Builder restClientBuilder,
        CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.restClient = restClientBuilder
            .baseUrl("https://api.inventory.example.com")
            .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("inventory-service");
    }

    @Async
    @Override
    public CompletableFuture<Void> reserveStock(List<OrderLineItem> items) {
        try {
            // ✅ Circuit Breaker로 감싸기
            circuitBreaker.executeRunnable(() -> {
                restClient.post()
                    .uri("/api/inventory/reserve")
                    .body(items)
                    .retrieve()
                    .toBodilessEntity();
            });

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}

/**
 * ✅ Circuit Breaker 설정 (application.yml)
 */
// resilience4j:
//   circuitbreaker:
//     instances:
//       inventory-service:
//         registerHealthIndicator: true
//         slidingWindowSize: 10
//         minimumNumberOfCalls: 5
//         permittedNumberOfCallsInHalfOpenState: 3
//         waitDurationInOpenState: 10s
//         failureRateThreshold: 50
```

---

## 📋 Outbound Port Async 체크리스트

### Port 설계 (Domain Layer)
- [ ] Port 인터페이스를 **Domain Layer**에 정의했는가?
- [ ] `CompletableFuture` 반환으로 **비동기 처리**하는가?
- [ ] Framework 의존성 없이 **순수 Java**로 작성했는가?

### Adapter 구현 (Application Layer)
- [ ] `@Async`로 **Virtual Thread**에서 실행하는가?
- [ ] RestClient 또는 WebClient 사용하는가?
- [ ] **트랜잭션 밖**에서 외부 API 호출하는가?
- [ ] 에러 처리 (`CompletableFuture.failedFuture()`) 구현했는가?

### UseCase 통합
- [ ] 트랜잭션 내: **DB 저장만** (짧게 유지)
- [ ] 트랜잭션 외: **외부 API 비동기 호출**
- [ ] 병렬 실행: `CompletableFuture.allOf()` 또는 `StructuredTaskScope`
- [ ] Fire-and-Forget 또는 완료 대기 전략 결정했는가?

### 고급 패턴
- [ ] Retry 로직 (Exponential Backoff) 구현했는가?
- [ ] Circuit Breaker (Resilience4j) 적용했는가?
- [ ] 타임아웃 설정했는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-17
**버전**: 1.0.0
