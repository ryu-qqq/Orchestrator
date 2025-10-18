# Cache Consistency - 캐시 일관성 관리

**목적**: DB 업데이트 시 캐시 동기화 및 Invalidation 전략

**관련 문서**:
- [Cache Strategies](./01_cache-strategies.md)
- [Domain Events](../event-driven/01_domain-events.md)

**필수 버전**: Spring Framework 5.0+, Spring Data Redis 3.0+

---

## 📌 핵심 원칙

### Cache Consistency 문제

1. **Stale Data**: 캐시와 DB 데이터 불일치
2. **Race Condition**: 동시 업데이트 시 충돌
3. **Cache Stampede**: 대량 캐시 만료 시 DB 폭주

---

## ❌ 일관성 문제 사례

### 문제 1: 캐시 무효화 누락

```java
// ❌ Before - 캐시 무효화 누락
@Service
public class ProductService {

    /**
     * ❌ 문제점:
     * - DB는 업데이트되었으나 캐시는 이전 데이터
     * - 다음 조회 시 오래된 데이터 반환
     * - TTL 만료 전까지 불일치 지속
     */
    @Transactional
    public void updateProductPrice(ProductId productId, Money newPrice) {
        Product product = productRepository.findById(productId).orElseThrow();

        product.updatePrice(newPrice);

        productRepository.save(product);  // ⚠️ 캐시 무효화 없음!
    }

    @Cacheable(cacheNames = "products", key = "#productId.value()")
    public ProductResponse getProduct(ProductId productId) {
        // ⚠️ 캐시에 오래된 가격 정보가 남아있음
        return ProductResponse.from(productRepository.findById(productId).orElseThrow());
    }
}
```

**문제 시나리오**:
```
1. 시각 T0: getProduct(id=123) → 가격 100원 (DB & Cache)
2. 시각 T1: updateProductPrice(id=123, 200원) → DB만 업데이트
3. 시각 T2: getProduct(id=123) → 가격 100원 (⚠️ 캐시에서 반환, 오래된 데이터)
4. 시각 T3 (TTL 만료): getProduct(id=123) → 가격 200원 (DB에서 반환)
```

---

## ✅ Invalidation Strategy 1: @CacheEvict

### 패턴: 명시적 캐시 삭제

```java
package com.company.application.service;

/**
 * Product Service - Cache Eviction
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class ProductService {

    /**
     * ✅ @CacheEvict - 캐시 삭제
     *
     * - DB 업데이트 후 캐시 삭제
     * - 다음 조회 시 최신 데이터로 캐싱
     */
    @CacheEvict(cacheNames = "products", key = "#productId.value()")
    @Transactional
    public void updateProductPrice(ProductId productId, Money newPrice) {
        Product product = productRepository.findById(productId).orElseThrow();

        product.updatePrice(newPrice);

        productRepository.save(product);

        // ✅ @CacheEvict에 의해 캐시 자동 삭제
    }

    /**
     * ✅ 여러 캐시 동시 삭제
     */
    @CacheEvict(
        cacheNames = {"products", "productSummaries"},
        key = "#productId.value()"
    )
    @Transactional
    public void updateProduct(UpdateProductCommand command) {
        Product product = productRepository.findById(command.productId()).orElseThrow();

        product.update(command.name(), command.price(), command.description());

        productRepository.save(product);
    }

    /**
     * ✅ 캐시 전체 삭제 (주의: 성능 영향)
     */
    @CacheEvict(cacheNames = "products", allEntries = true)
    @Transactional
    public void updateAllProductPrices(Money increaseRate) {
        List<Product> products = productRepository.findAll();

        for (Product product : products) {
            product.updatePrice(product.getPrice().multiply(increaseRate));
        }

        productRepository.saveAll(products);
    }
}
```

---

## ✅ Invalidation Strategy 2: Event-Driven Invalidation

### 패턴: Domain Event로 캐시 무효화

```java
/**
 * Product Event
 *
 * @author development-team
 * @since 1.0.0
 */
public record ProductPriceUpdated(
    ProductId productId,
    Money oldPrice,
    Money newPrice,
    Instant occurredAt
) {}

/**
 * Product Service - Event 발행
 */
@Service
public class ProductService {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * ✅ Event 발행으로 캐시 무효화 분리
     */
    @Transactional
    public void updateProductPrice(ProductId productId, Money newPrice) {
        Product product = productRepository.findById(productId).orElseThrow();

        Money oldPrice = product.getPrice();
        product.updatePrice(newPrice);

        productRepository.save(product);

        // ✅ Event 발행
        eventPublisher.publishEvent(
            new ProductPriceUpdated(productId, oldPrice, newPrice, Instant.now())
        );
    }
}

/**
 * Cache Invalidation Event Handler
 */
@Component
public class CacheInvalidationEventHandler {

    private final CacheManager cacheManager;

    /**
     * ✅ ProductPriceUpdated Event 수신 → 캐시 삭제
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductPriceUpdated(ProductPriceUpdated event) {
        Cache cache = cacheManager.getCache("products");

        if (cache != null) {
            cache.evict(event.productId().value());
            log.info("Cache evicted for product: {}", event.productId());
        }
    }
}
```

**Event-Driven Invalidation 장점**:
- ✅ 비즈니스 로직과 캐시 관리 분리 (SRP)
- ✅ 여러 캐시를 한 번에 관리 (확장성)
- ✅ 비동기 처리 가능

---

## 🎯 실전 예제: Cache Warming

### ✅ Example: 대량 만료 방지

```java
/**
 * Cache Warming - 미리 캐시 로딩
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class CacheWarmingScheduler {

    private final ProductService productService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * ✅ 매일 새벽 3시 인기 상품 캐시 로딩
     *
     * - Cache Stampede 방지
     * - TTL 만료 전 미리 갱신
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void warmPopularProducts() {
        log.info("Starting cache warming for popular products");

        List<ProductId> popularProductIds = getPopularProductIds();

        for (ProductId productId : popularProductIds) {
            try {
                // ✅ 캐시 Miss 유도 → 자동 캐싱
                productService.getProduct(productId);

                // ✅ TTL 랜덤화 (±10%)로 동시 만료 방지
                String key = "product::" + productId.value();
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

                if (ttl != null && ttl > 0) {
                    long randomizedTtl = (long) (ttl * (0.9 + Math.random() * 0.2));
                    redisTemplate.expire(key, Duration.ofSeconds(randomizedTtl));
                }

            } catch (Exception e) {
                log.error("Failed to warm cache for product: {}", productId, e);
            }
        }

        log.info("Cache warming completed for {} products", popularProductIds.size());
    }

    private List<ProductId> getPopularProductIds() {
        // 인기 상품 조회 로직 (예: 최근 7일 판매량 Top 100)
        return productRepository.findTop100ByOrderBySalesCountDesc().stream()
            .map(Product::getId)
            .toList();
    }
}
```

**Cache Stampede 시나리오**:
```
문제 상황:
- 인기 상품 100개가 동일 시각(새벽 3시)에 TTL 만료
- 오전 9시 트래픽 급증 시 100개 모두 Cache Miss
- DB에 100 queries/초 폭주 → DB 다운

해결 방안:
1. Cache Warming: 새벽 3시 미리 캐시 로딩
2. TTL 랜덤화: 각 상품의 TTL을 ±10% 분산
3. Lock 메커니즘: 첫 요청만 DB 조회, 나머지는 대기
```

---

## 📋 Cache Consistency 체크리스트

### 설계
- [ ] Invalidation 전략 (Evict vs Event)
- [ ] Cache Warming 대상 선정
- [ ] TTL 랜덤화 정책

### 구현
- [ ] `@CacheEvict` 모든 업데이트 메서드에 적용
- [ ] Event Handler로 분리된 캐시 관리
- [ ] Cache Stampede 방지 (Lock, Warming)

### 모니터링
- [ ] Cache Invalidation 횟수
- [ ] Cache Miss Ratio (목표: <30%)
- [ ] DB 부하 (Stampede 감지)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
