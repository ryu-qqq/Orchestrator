# Distributed Cache - Redis 분산 캐싱

**목적**: Redis를 활용한 분산 캐시 구성 및 Serialization 전략

**관련 문서**:
- [Cache Strategies](./01_cache-strategies.md)
- [Cache Consistency](./03_cache-consistency.md)

**필수 버전**: Spring Boot 3.0+, Spring Data Redis 3.0+

---

## 📌 핵심 원칙

### Distributed Cache가 필요한 이유

1. **다중 인스턴스**: 여러 서버 간 캐시 공유
2. **영속성**: 서버 재시작 후에도 캐시 유지
3. **확장성**: 캐시 용량 독립적 확장
4. **고가용성**: Redis Cluster/Sentinel

---

## ✅ Redis 통합

### 패턴 1: Redis 의존성 및 설정

```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 2
          max-wait: -1ms

  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10분 (밀리초)
      cache-null-values: false
      key-prefix: "myapp:"
      use-key-prefix: true
```

```gradle
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-cache'
}
```

---

### 패턴 2: RedisTemplate vs Spring Cache

```java
package com.company.infrastructure.cache;

/**
 * Redis Template - 저수준 제어
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
public class RedisConfig {

    /**
     * ✅ RedisTemplate - 직접 제어
     *
     * - 장점: 세밀한 제어 (expire, scan 등)
     * - 단점: 보일러플레이트 코드
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key Serializer (String)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value Serializer (JSON)
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }
}

/**
 * RedisTemplate 직접 사용
 */
@Service
public class ProductCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;

    /**
     * ✅ RedisTemplate 활용 (수동 제어)
     */
    public ProductResponse getProduct(ProductId productId) {
        String key = "product::" + productId.value();

        // 1. 캐시 조회
        ProductResponse cached = (ProductResponse) redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return cached;  // Cache Hit
        }

        // 2. DB 조회
        Product product = productRepository.findById(productId).orElseThrow();
        ProductResponse response = ProductResponse.from(product);

        // 3. 캐시 저장 (TTL 1시간)
        redisTemplate.opsForValue().set(key, response, Duration.ofHours(1));

        return response;
    }
}

/**
 * Spring Cache Abstraction 사용 (권장)
 */
@Service
public class ProductService {

    /**
     * ✅ @Cacheable - 선언적 캐싱 (권장)
     *
     * - 장점: 간결한 코드, AOP 기반
     * - 단점: 세밀한 제어 어려움
     */
    @Cacheable(cacheNames = "products", key = "#productId.value()")
    public ProductResponse getProduct(ProductId productId) {
        Product product = productRepository.findById(productId).orElseThrow();
        return ProductResponse.from(product);
    }
}
```

**RedisTemplate vs Spring Cache**:

| 항목 | RedisTemplate | Spring Cache |
|------|--------------|--------------|
| 코드 양 | 많음 (수동 제어) | 적음 (선언적) |
| 세밀한 제어 | 가능 (TTL, scan 등) | 제한적 |
| 추상화 | 낮음 (Redis 의존) | 높음 (구현체 교체 가능) |
| 사용 사례 | 복잡한 캐싱 로직 | 일반적인 CRUD 캐싱 |

---

## 🎯 실전 예제: Serialization 전략

### ✅ Example: JSON vs Binary Serialization

```java
/**
 * Serialization Strategy
 *
 * @author development-team
 * @since 1.0.0
 */
@Configuration
public class RedisSerializationConfig {

    /**
     * ✅ JSON Serialization (권장)
     *
     * - 장점: 사람이 읽을 수 있음, 디버깅 쉬움
     * - 단점: 용량 큼 (Binary 대비 2-3배)
     * - 사용 사례: 대부분의 일반적 케이스
     */
    @Bean
    public CacheManager jsonCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }

    /**
     * ✅ Binary Serialization (Kryo)
     *
     * - 장점: 용량 작음, 성능 우수
     * - 단점: 사람이 읽을 수 없음, 버전 관리 복잡
     * - 사용 사례: 대용량 객체, 성능 크리티컬
     */
    @Bean
    public CacheManager binaryCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new KryoRedisSerializer<>()
                )
            );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}

/**
 * Kryo Serializer
 */
public class KryoRedisSerializer<T> implements RedisSerializer<T> {

    private final Kryo kryo = new Kryo();

    @Override
    public byte[] serialize(T value) {
        if (value == null) {
            return new byte[0];
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Output output = new Output(stream);
        kryo.writeClassAndObject(output, value);
        output.close();

        return stream.toByteArray();
    }

    @Override
    public T deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        Input input = new Input(new ByteArrayInputStream(bytes));
        @SuppressWarnings("unchecked")
        T value = (T) kryo.readClassAndObject(input);
        input.close();

        return value;
    }
}
```

**Serialization 비교**:

| 형식 | 크기 | 속도 | 가독성 | 사용 사례 |
|------|-----|------|-------|----------|
| JSON | 1000 bytes | 느림 | 높음 | 일반적 객체 |
| Kryo | 400 bytes (60% 절감) | 빠름 | 없음 | 대용량 객체 |
| Protobuf | 350 bytes (65% 절감) | 빠름 | 낮음 | 서비스 간 통신 |

---

## 📋 Distributed Cache 체크리스트

### 설계
- [ ] Redis Cluster vs Sentinel 선택
- [ ] Serialization 전략 (JSON vs Binary)
- [ ] Key 네이밍 규칙 (prefix 활용)

### 구현
- [ ] Connection Pool 설정 (max-active, max-idle)
- [ ] Timeout 설정 (2초 권장)
- [ ] Eviction 정책 (allkeys-lru)

### 모니터링
- [ ] Redis 메모리 사용량
- [ ] Connection Pool 상태
- [ ] Serialization 성능

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
