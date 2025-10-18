# Batch Processing - QueryDSL 대량 처리

**목적**: Batch INSERT/UPDATE/DELETE 최적화

**관련 문서**:
- [DTO Projection](./01_dto-projection.md)
- [Transaction Management](../../01-core-architecture/transaction-management/01_transaction-basics.md)

**필수 버전**: Java 21+, Spring Boot 3.0+

---

## 📌 핵심 원칙

### Batch Processing이란?

1. **대량 처리**: 여러 건을 한 번에 처리
2. **성능 최적화**: DB 왕복 최소화
3. **메모리 효율**: Chunk 단위 처리

---

## ❌ 반복문 저장

```java
// ❌ Before - 1건씩 저장 (N번 DB 왕복)
public void updatePrices(List<UpdatePriceCommand> commands) {
    for (UpdatePriceCommand command : commands) {
        Product product = productRepository.findById(command.productId()).get();
        product.updatePrice(command.newPrice());
        productRepository.save(product);  // ❌ N번 INSERT/UPDATE
    }
}
```

---

## ✅ Batch UPDATE 패턴

```java
/**
 * Product QueryDSL Repository
 *
 * 📁 Package: adapter.out.persistence-mysql.product.querydsl
 *
 * @author development-team
 * @since 1.0.0
 */
@Repository
public class ProductQueryDslRepository {

    /**
     * ✅ Batch UPDATE - 1번 쿼리로 여러 건 업데이트
     */
    public long updatePrices(List<ProductId> productIds, Money newPrice) {
        return queryFactory
            .update(product)
            .set(product.price, newPrice)
            .where(product.id.in(productIds))
            .execute();  // ✅ 1번의 UPDATE 쿼리
    }

    /**
     * ✅ Batch DELETE
     */
    public long deleteByStatus(ProductStatus status) {
        return queryFactory
            .delete(product)
            .where(product.status.eq(status))
            .execute();
    }
}
```

---

## 📋 Batch Processing 체크리스트

- [ ] Batch UPDATE/DELETE 사용하는가?
- [ ] N번 쿼리를 1번으로 최적화했는가?
- [ ] Chunk 단위 처리하는가?

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
