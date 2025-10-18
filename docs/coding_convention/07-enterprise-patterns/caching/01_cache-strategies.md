# Cache Strategies - 캐싱 전략 및 패턴

**목적**: Look-Aside, Write-Through 등 캐싱 전략 이해 및 Spring Cache 적용

**관련 문서**:
- [Distributed Cache](./02_distributed-cache.md)
- [Cache Consistency](./03_cache-consistency.md)

**필수 버전**: Spring Framework 5.0+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### 캐싱이 필요한 이유

1. **성능 향상**: DB 조회 시간 감소 (ms → µs)
2. **부하 분산**: DB 부담 감소
3. **가용성 향상**: 일시적 DB 장애 시에도 서비스 가능
4. **비용 절감**: DB 리소스 사용량 감소

### 캐시 적용 기준

- ✅ **읽기 빈도 높음**: 조회가 쓰기보다 10배 이상
- ✅ **변경 빈도 낮음**: 데이터 업데이트가 드묾
- ✅ **계산 비용 높음**: 복잡한 집계, 조인 쿼리
- ❌ **실시간성 필수**: 최신 데이터 필수 (캐시 부적합)

---

## ❌ 캐시 없는 문제점

### 문제: 반복적인 DB 조회

```java
// ❌ Before - 캐시 없음 (매번 DB 조회)
@Service
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * ❌ 문제점:
     * - 동일 상품 조회 요청마다 DB 쿼리 실행
     * - 100 req/sec → 100 DB queries/sec
     * - DB 부하 증가, 응답 시간 느림
     */
    public ProductResponse getProduct(ProductId productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        return ProductResponse.from(product);
    }
}
```

**성능 측정**:
- DB 조회 시간: 평균 50ms
- 100 req/sec → 5,000ms DB 작업 시간
- DB CPU 사용률: 80% (병목)

---

## ✅ Cache Strategy 1: Look-Aside (Cache-Aside)

### 패턴: 애플리케이션이 캐시 직접 관리

```java
package com.company.application.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

/**
 * Product Service - Look-Aside 패턴
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * ✅ @Cacheable - Look-Aside 패턴 자동 적용
     *
     * - 흐름:
     *   1. 캐시 조회 → 있으면 반환
     *   2. 없으면 DB 조회 → 캐시 저장 → 반환
     *
     * - key: "product::{productId}"
     * - value: ProductResponse (직렬화됨)
     */
    @Cacheable(
        cacheNames = "products",
        key = "#productId.value()",
        unless = "#result == null"  // null은 캐싱하지 않음
    )
    public ProductResponse getProduct(ProductId productId) {
        log.info("Cache miss - querying DB for product: {}", productId);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        return ProductResponse.from(product);
    }

    /**
     * ✅ @CacheEvict - 캐시 무효화
     *
     * - 상품 업데이트 시 캐시 삭제
     * - 다음 조회 시 최신 데이터로 캐싱
     */
    @CacheEvict(cacheNames = "products", key = "#command.productId().value()")
    @Transactional
    public void updateProduct(UpdateProductCommand command) {
        Product product = productRepository.findById(command.productId()).orElseThrow();

        product.update(command.name(), command.price());

        productRepository.save(product);
    }
}
```

**Look-Aside 흐름**:
```
1. Client → App: getProduct(id=123)
2. App → Cache: GET product::123
3. Cache → App: null (Cache Miss)
4. App → DB: SELECT * FROM products WHERE id = 123
5. DB → App: Product(id=123, name="...")
6. App → Cache: SET product::123 = Product(...)
7. App → Client: ProductResponse(...)

[다음 요청]
1. Client → App: getProduct(id=123)
2. App → Cache: GET product::123
3. Cache → App: Product(...) (Cache Hit ✅)
4. App → Client: ProductResponse(...)  // DB 조회 스킵!
```

**성능 개선**:
- Cache Hit Ratio: 90% (10번 중 9번 캐시에서 반환)
- 평균 응답 시간: 50ms → 5ms (90% 개선)
- DB 부하: 100 queries/sec → 10 queries/sec (90% 감소)

---

## ✅ Cache Strategy 2: Write-Through

### 패턴: 쓰기 시 캐시와 DB 동시 업데이트

```java
/**
 * Product Service - Write-Through 패턴
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class ProductService {

    /**
     * ✅ @CachePut - Write-Through 패턴
     *
     * - 흐름:
     *   1. DB 업데이트
     *   2. 캐시 업데이트 (자동)
     *
     * - 장점: 항상 최신 데이터 캐싱
     * - 단점: 쓰기 지연 (캐시 업데이트 시간)
     */
    @CachePut(cacheNames = "products", key = "#command.productId().value()")
    @Transactional
    public ProductResponse updateProduct(UpdateProductCommand command) {
        Product product = productRepository.findById(command.productId()).orElseThrow();

        product.update(command.name(), command.price());

        productRepository.save(product);

        // ✅ 반환값이 캐시에 자동 저장됨
        return ProductResponse.from(product);
    }
}
```

**Write-Through 흐름**:
```
1. Client → App: updateProduct(id=123, name="NewName")
2. App → DB: UPDATE products SET name = 'NewName' WHERE id = 123
3. App → Cache: SET product::123 = Product(id=123, name="NewName")  // ✅ 자동
4. App → Client: ProductResponse(...)
```

**Look-Aside vs Write-Through**:

| 항목 | Look-Aside | Write-Through |
|------|-----------|--------------|
| 읽기 | 캐시 → DB (Miss 시) | 캐시 → DB (Miss 시) |
| 쓰기 | 캐시 삭제 (@CacheEvict) | 캐시 업데이트 (@CachePut) |
| 캐시 일관성 | 약함 (갱신 지연) | 강함 (즉시 갱신) |
| 쓰기 성능 | 빠름 (삭제만) | 느림 (업데이트 필요) |
| 사용 사례 | 읽기 위주 | 읽기/쓰기 균형 |

---

## 🎯 실전 예제: TTL 및 Eviction

### ✅ Example: Spring Cache Configuration

```java
package com.company.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Cache Configuration - TTL 및 Eviction 정책
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * ✅ Redis Cache Manager 설정
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))  // ✅ TTL: 10분
            .disableCachingNullValues()  // null 캐싱 금지
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );

        // ✅ 캐시별 개별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
            "products", defaultConfig.entryTtl(Duration.ofHours(1)),  // 1시간
            "categories", defaultConfig.entryTtl(Duration.ofDays(1)), // 1일
            "users", defaultConfig.entryTtl(Duration.ofMinutes(30))   // 30분
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
```

---

## 🎯 실전 예제: Transaction Boundaries와 통합

### ✅ Example: Cache와 Transaction 레이어 분리

**관련 문서**: [Transaction Boundaries](../../03-application-layer/transaction-management/01_transaction-boundaries.md)

#### ❌ Before - 잘못된 패턴 (Cache와 Transaction 혼용)

```java
/**
 * ❌ 안티패턴: @Cacheable과 @Transactional 동시 사용
 *
 * 문제점:
 * 1. Spring AOP Proxy 충돌 가능성
 * 2. Cache Hit 시에도 트랜잭션 시작 (불필요한 DB 커넥션 점유)
 * 3. 트랜잭션 롤백 시 캐시 일관성 문제
 */
@Service
public class ProductService {

    // ❌ @Cacheable과 @Transactional을 같은 메서드에 사용
    @Cacheable(cacheNames = "products", key = "#productId.value()")
    @Transactional(readOnly = true)
    public ProductResponse getProduct(ProductId productId) {
        // Cache Miss 시에도 불필요하게 트랜잭션 시작
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        return ProductResponse.from(product);
    }

    // ❌ @CacheEvict와 @Transactional 순서 문제
    @CacheEvict(cacheNames = "products", key = "#command.productId().value()")
    @Transactional
    public void updateProduct(UpdateProductCommand command) {
        Product product = productRepository.findById(command.productId()).orElseThrow();
        product.update(command.name(), command.price());
        productRepository.save(product);

        // ⚠️ 문제: @CacheEvict가 먼저 실행되어 트랜잭션 커밋 전에 캐시 삭제
        //    → 다른 요청이 삭제된 캐시를 보고 DB 조회 → 아직 커밋 안된 데이터 조회 불가
        //    → 트랜잭션 롤백 시 캐시는 이미 삭제되어 일관성 깨짐
    }
}
```

#### ✅ After - 올바른 패턴 (레이어 분리)

```java
package com.company.application.service;

/**
 * Product Application Service - Cache 레이어
 *
 * - ✅ @Cacheable만 사용 (트랜잭션 없음)
 * - ✅ 실제 DB 작업은 Persistence Service에 위임
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class ProductService {

    private final ProductPersistenceService persistenceService;

    /**
     * ✅ Cache 레이어 - 트랜잭션 없음
     *
     * - Cache Hit: 즉시 반환 (DB 커넥션 사용 안함)
     * - Cache Miss: Persistence Service 호출 (내부에서 트랜잭션)
     */
    @Cacheable(
        cacheNames = "products",
        key = "#productId.value()",
        unless = "#result == null"
    )
    public ProductResponse getProduct(ProductId productId) {
        log.info("Cache miss - delegating to persistence layer: {}", productId);

        // ✅ Persistence Service가 내부에서 @Transactional 처리
        return persistenceService.findProduct(productId);
    }

    /**
     * ✅ Cache 무효화 - 트랜잭션 커밋 후 실행
     *
     * - beforeInvocation = false (기본값)
     * - 트랜잭션 성공 후 캐시 삭제
     * - 트랜잭션 롤백 시 캐시 유지
     */
    @CacheEvict(
        cacheNames = "products",
        key = "#command.productId().value()",
        beforeInvocation = false  // ✅ 트랜잭션 성공 후 삭제
    )
    public void updateProduct(UpdateProductCommand command) {
        // ✅ Persistence Service가 내부에서 @Transactional 처리
        persistenceService.updateProduct(command);

        // 트랜잭션 커밋 → @CacheEvict 실행 순서 보장
    }
}

/**
 * Product Persistence Service - Transaction 레이어
 *
 * - ✅ @Transactional만 사용 (캐시 없음)
 * - ✅ 순수 DB 작업만 포함 (외부 API 호출 없음)
 *
 * @author development-team
 * @since 1.0.0
 */
@Service
public class ProductPersistenceService {

    private final ProductRepository productRepository;

    /**
     * ✅ Transaction 레이어 - 캐시 없음
     *
     * - readOnly = true로 최적화
     * - 트랜잭션 시간 최소화 (10-50ms)
     */
    @Transactional(readOnly = true)
    public ProductResponse findProduct(ProductId productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        return ProductResponse.from(product);
    }

    /**
     * ✅ 순수 DB 작업만 포함
     *
     * - 외부 API 호출 없음
     * - 트랜잭션 시간 짧게 유지
     */
    @Transactional
    public void updateProduct(UpdateProductCommand command) {
        Product product = productRepository.findById(command.productId())
            .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        product.update(command.name(), command.price());

        productRepository.save(product);
    }
}
```

#### 레이어 분리 아키텍처

```
┌─────────────────────────────────────────┐
│  Controller Layer                       │
│  - HTTP 요청/응답 처리                    │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  Application Service (Cache Layer)      │
│  ✅ @Cacheable, @CacheEvict             │
│  ❌ @Transactional 없음                  │
│  - Cache Hit: 즉시 반환                  │
│  - Cache Miss: Persistence 위임         │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  Persistence Service (Transaction)      │
│  ✅ @Transactional                      │
│  ❌ Cache 어노테이션 없음                 │
│  - 순수 DB 작업만                        │
│  - 트랜잭션 시간 최소화 (10-50ms)         │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  Repository (JPA/Port)                  │
│  - DB 접근 로직                          │
└─────────────────────────────────────────┘
```

#### @CacheEvict 순서 제어

```java
/**
 * @CacheEvict 순서 제어
 */
@Service
public class ProductService {

    /**
     * ✅ beforeInvocation = false (기본값, 권장)
     *
     * - 메서드 실행 → 트랜잭션 커밋 → 캐시 삭제
     * - 트랜잭션 롤백 시 캐시 유지 (일관성 보장)
     */
    @CacheEvict(
        cacheNames = "products",
        key = "#productId.value()",
        beforeInvocation = false  // ✅ 트랜잭션 성공 후 삭제
    )
    public void deleteProduct(ProductId productId) {
        persistenceService.delete(productId);
        // 트랜잭션 커밋 성공 → @CacheEvict 실행
    }

    /**
     * ⚠️ beforeInvocation = true (특수한 경우만)
     *
     * - 캐시 삭제 → 메서드 실행 → 트랜잭션 커밋
     * - 사용 사례: 메서드 실패 여부와 무관하게 캐시 무효화 필요 시
     * - 주의: 트랜잭션 롤백 시 캐시 일관성 깨질 수 있음
     */
    @CacheEvict(
        cacheNames = "products",
        key = "#productId.value()",
        beforeInvocation = true  // ⚠️ 메서드 실행 전 삭제
    )
    public void forceInvalidateProduct(ProductId productId) {
        // 캐시 먼저 삭제 → 이후 작업 실행
        persistenceService.invalidate(productId);
    }
}
```

#### 성능 비교

**Before (Cache + Transaction 혼용)**:
```
Cache Hit인 경우:
- 캐시 조회: 1ms
- 트랜잭션 시작: 5ms (불필요)
- DB 커넥션 획득: 2ms (불필요)
- 총 시간: 8ms

Cache Miss인 경우:
- 캐시 조회: 1ms
- 트랜잭션 시작: 5ms
- DB 조회: 50ms
- 총 시간: 56ms
```

**After (레이어 분리)**:
```
Cache Hit인 경우:
- 캐시 조회: 1ms
- 총 시간: 1ms ✅ (87.5% 개선)

Cache Miss인 경우:
- 캐시 조회: 1ms
- Persistence Service 호출 (내부 트랜잭션): 55ms
- 총 시간: 56ms (동일)
```

**개선 효과**:
- Cache Hit Ratio 90% 가정 시:
  - Before: 평균 11.6ms (0.9 * 8ms + 0.1 * 56ms)
  - After: 평균 6.5ms (0.9 * 1ms + 0.1 * 56ms)
  - **44% 성능 개선** ✅

---

## 📋 Cache Strategy 체크리스트

### 설계
- [ ] Cache Hit Ratio 목표 설정 (70% 이상)
- [ ] TTL 정책 결정 (데이터 특성별)
- [ ] Look-Aside vs Write-Through 선택
- [ ] **Cache와 Transaction 레이어 분리 설계**

### 구현
- [ ] `@Cacheable`, `@CacheEvict`, `@CachePut` 적용
- [ ] Key 생성 전략 (SpEL 활용)
- [ ] null 값 캐싱 방지 (`unless`)
- [ ] **`@Cacheable`과 `@Transactional` 동일 메서드 사용 금지**
- [ ] **Persistence Service로 트랜잭션 분리**
- [ ] **`@CacheEvict` 순서 제어 (`beforeInvocation` 설정)**

### 모니터링
- [ ] Cache Hit/Miss 메트릭 수집
- [ ] Eviction 횟수 추적
- [ ] 메모리 사용량 모니터링

---

## 📚 관련 문서

**필수 읽기**:
- [Transaction Boundaries](../../03-application-layer/transaction-management/01_transaction-boundaries.md) - 트랜잭션과 외부 호출 분리

**연관 패턴**:
- [Distributed Cache](./02_distributed-cache.md) - Redis 분산 캐시
- [Cache Consistency](./03_cache-consistency.md) - 캐시 일관성 전략

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
