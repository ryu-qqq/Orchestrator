# Annotation Rules - ArchUnit으로 애노테이션 사용 규칙 검증

**목적**: ArchUnit을 활용하여 `@Transactional`, `@Entity`, Spring 애노테이션의 올바른 사용을 레이어별로 자동 검증

**관련 문서**:
- [Spring Proxy Limitations](../../03-application-layer/transaction-management/02_spring-proxy-limitations.md)
- [Transaction Best Practices](../../03-application-layer/transaction-management/03_transaction-best-practices.md)
- [Layer Dependency Rules](./01_layer-dependency-rules.md)

**검증 도구**: ArchUnit 1.2.0+

---

## 📌 핵심 원칙

### 애노테이션 레이어별 제약 사항

| 애노테이션 | Domain Layer | Application Layer | 비고 |
|-----------|--------------|-------------------|------|
| `@Transactional` | ❌ 금지 | ✅ 허용 (Public 메서드만) | Private/Final 금지 |
| `@Entity` | ❌ 금지 | ✅ 허용 (Persistence만) | Domain은 순수 객체 |
| `@Service` | ❌ 금지 | ✅ 허용 (Service만) | Domain Service는 POJO |
| `@Repository` | ❌ 금지 | ✅ 허용 (Repository만) | Port는 Interface |
| `@RestController` | ❌ 금지 | ✅ 허용 (Web만) | Controller에만 |

---

## ❌ 금지 패턴 (Anti-Patterns)

### Anti-Pattern 1: Private 메서드에 @Transactional

```java
// ❌ Private 메서드 - 프록시 우회됨!
package com.company.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    @Transactional // ❌ Private 메서드 - 프록시 작동 안 함!
    private void saveOrder(Order order) {
        orderRepository.save(order);
    }

    public void createOrder(CreateOrderCommand cmd) {
        Order order = Order.create(cmd);
        this.saveOrder(order); // ❌ @Transactional 무시됨!
    }
}
```

**문제점**:
- Spring AOP 프록시는 Public 메서드만 인터셉트
- Private 메서드 호출 시 프록시 우회 → 트랜잭션 미적용
- 내부 메서드 호출 (`this.method()`) 시 프록시 우회

**ArchUnit 검증 실패**:
```
Architecture Violation -
Method <com.company.application.service.OrderService.saveOrder(Order)> is
annotated with @Transactional, but is private
```

---

### Anti-Pattern 2: Final 메서드에 @Transactional

```java
// ❌ Final 메서드 - 프록시 오버라이드 불가!
package com.company.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    @Transactional // ❌ Final 메서드 - 프록시 생성 불가!
    public final void createOrder(CreateOrderCommand cmd) {
        Order order = Order.create(cmd);
        orderRepository.save(order);
    }
}
```

**문제점**:
- CGLIB 프록시는 메서드를 오버라이드하여 동작
- Final 메서드는 오버라이드 불가 → 프록시 생성 실패

---

### Anti-Pattern 3: Domain Layer에 @Entity

```java
// ❌ Domain Layer에 JPA 애노테이션
package com.company.domain.order;

import jakarta.persistence.Entity; // ❌ Domain → JPA 의존

@Entity // ❌ Domain Layer에 JPA 애노테이션 금지!
public class Order {
    // Domain 객체는 순수 Java여야 함
}
```

**문제점**:
- Domain Layer가 JPA 프레임워크에 의존
- 테스트 어려움 (JPA Context 필요)
- Domain 순수성 훼손

---

### Anti-Pattern 4: Domain Layer에 @Service

```java
// ❌ Domain Layer에 Spring 애노테이션
package com.company.domain.service;

import org.springframework.stereotype.Service; // ❌ Domain → Spring 의존

@Service // ❌ Domain Service는 POJO여야 함!
public class OrderDomainService {
    // Domain Service는 순수 Java로 작성
}
```

**문제점**:
- Domain Layer가 Spring Framework에 의존
- Domain의 재사용성 저하

---

## ✅ ArchUnit 검증 규칙

### 규칙 1: @Transactional 사용 제약

```java
package com.company.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * @Transactional 사용 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class TransactionalAnnotationTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: @Transactional은 Public 메서드에만 사용
     */
    @Test
    void transactionalMethodsShouldBePublic() {
        ArchRule rule = methods()
            .that().areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .should().bePublic()
            .because("Spring AOP proxies only intercept public methods");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @Transactional 메서드는 Final이 아니어야 함
     */
    @Test
    void transactionalMethodsShouldNotBeFinal() {
        ArchRule rule = methods()
            .that().areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .should().notBeFinal()
            .because("CGLIB proxies cannot override final methods");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @Transactional은 Domain Layer에서 사용 금지
     */
    @Test
    void transactionalShouldNotBeUsedInDomainLayer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .because("Domain Layer should be framework-independent");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @Transactional 클래스는 Final이 아니어야 함
     */
    @Test
    void transactionalClassesShouldNotBeFinal() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .should().notBeFinal()
            .because("CGLIB proxies cannot extend final classes");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ `@Transactional`은 Public 메서드만
- ✅ `@Transactional` 메서드는 Final 금지
- ✅ Domain Layer에서 `@Transactional` 금지
- ✅ `@Transactional` 클래스는 Final 금지

---

### 규칙 2: @Entity 사용 제약

```java
/**
 * @Entity 사용 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class EntityAnnotationTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: @Entity는 Domain Layer에서 사용 금지
     */
    @Test
    void entityShouldNotBeUsedInDomainLayer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(jakarta.persistence.Entity.class)
            .because("Domain Layer should not depend on JPA");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @Entity는 Persistence Layer에만 위치
     */
    @Test
    void entitiesShouldResideInPersistenceLayer() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("..application.out.persistence..")
            .because("JPA Entities are persistence implementation details");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @Entity 클래스는 "*JpaEntity" 접미사 필수
     */
    @Test
    void entitiesShouldEndWithJpaEntity() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().haveSimpleNameEndingWith("JpaEntity")
            .because("JPA Entities should be clearly distinguished from Domain Entities");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ Domain Layer에서 `@Entity` 금지
- ✅ `@Entity`는 Persistence Layer에만
- ✅ `@Entity` 클래스는 `*JpaEntity` 접미사

---

### 규칙 3: Spring 애노테이션 레이어별 제약

```java
/**
 * Spring 애노테이션 레이어별 제약 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class SpringAnnotationLayerTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Domain Layer에는 Spring 애노테이션 금지
     */
    @Test
    void domainShouldNotHaveSpringAnnotations() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
            .orShould().beAnnotatedWith(org.springframework.stereotype.Component.class)
            .orShould().beAnnotatedWith(org.springframework.stereotype.Repository.class)
            .because("Domain Layer should be framework-independent");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @Service는 Application Service에만 사용
     */
    @Test
    void serviceShouldBeUsedInApplicationServiceOnly() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(org.springframework.stereotype.Service.class)
            .should().resideInAPackage("..application.service..")
            .because("@Service is for Application Services, not Domain Services");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @RestController는 Web Adapter에만 사용
     */
    @Test
    void restControllerShouldBeUsedInWebAdapterOnly() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("..application.in.web..")
            .because("@RestController is for inbound web adapters");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @Repository는 Persistence Adapter에만 사용
     */
    @Test
    void repositoryShouldBeUsedInPersistenceAdapterOnly() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(org.springframework.stereotype.Repository.class)
            .should().resideInAPackage("..application.out.persistence..")
            .because("@Repository is for persistence adapters");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ Domain Layer에 Spring 애노테이션 금지
- ✅ `@Service`는 Application Service만
- ✅ `@RestController`는 Web Adapter만
- ✅ `@Repository`는 Persistence Adapter만

---

## 🎯 실전 예제: 올바른 애노테이션 사용

### ✅ Example 1: Application Service with @Transactional

**Application Service (UseCase 구현)**:
```java
// application/service/CreateOrderService.java
package com.company.application.service;

import com.company.domain.order.Order;
import com.company.domain.order.OrderId;
import com.company.domain.port.in.CreateOrderCommand;
import com.company.domain.port.in.CreateOrderUseCase;
import com.company.domain.port.out.LoadOrderPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create Order Service (Application Service)
 *
 * @author development-team
 * @since 1.0.0
 */
@Service // ✅ Application Service에 @Service
public class CreateOrderService implements CreateOrderUseCase {
    private final LoadOrderPort loadOrderPort;

    public CreateOrderService(LoadOrderPort loadOrderPort) {
        this.loadOrderPort = loadOrderPort;
    }

    @Override
    @Transactional // ✅ Public 메서드에 @Transactional
    public OrderId createOrder(CreateOrderCommand command) {
        Order order = Order.create(command.customerId(), command.items());
        loadOrderPort.saveOrder(order);
        return order.getId();
    }
}
```

**ArchUnit 검증 결과**:
```
✅ @Service is in application.service package
✅ @Transactional method is public
✅ @Transactional method is not final
```

---

### ✅ Example 2: Domain Service without Spring Annotations

**Domain Service (POJO)**:
```java
// domain/service/OrderDomainService.java
package com.company.domain.service;

import com.company.domain.order.Order;
import com.company.domain.payment.Payment;

/**
 * Order Domain Service (Pure Java)
 *
 * @author development-team
 * @since 1.0.0
 */
public class OrderDomainService { // ✅ No Spring annotations

    /**
     * 여러 Aggregate 조율 (Domain 로직)
     */
    public void approveOrderWithPayment(Order order, Payment payment) {
        if (!payment.isCompleted()) {
            throw new IllegalStateException("Payment must be completed before order approval");
        }
        order.approve();
    }
}
```

**ArchUnit 검증 결과**:
```
✅ Domain Service has no @Service annotation
✅ Domain Service resides in domain.service package
✅ Domain Service has no Spring dependencies
```

---

### ✅ Example 3: JPA Entity in Persistence Layer

**JPA Entity (Persistence Layer)**:
```java
// application/out/persistence/OrderJpaEntity.java
package com.company.application.out.persistence;

import jakarta.persistence.*;

/**
 * Order JPA Entity (Persistence Implementation)
 *
 * @author development-team
 * @since 1.0.0
 */
@Entity // ✅ Persistence Layer에만 @Entity
@Table(name = "orders")
public class OrderJpaEntity { // ✅ "*JpaEntity" 접미사
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private String status;

    // Constructors, Getters, Setters
}
```

**Domain Entity (Pure Java)**:
```java
// domain/order/Order.java
package com.company.domain.order;

/**
 * Order Aggregate Root (Pure Domain Model)
 *
 * @author development-team
 * @since 1.0.0
 */
public class Order { // ✅ No JPA annotations
    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;

    // Pure business logic
}
```

**ArchUnit 검증 결과**:
```
✅ OrderJpaEntity has @Entity in persistence package
✅ OrderJpaEntity ends with "JpaEntity"
✅ Order (Domain) has no @Entity annotation
✅ Order (Domain) has no Jakarta dependencies
```

---

## 🔧 고급 애노테이션 규칙

### 규칙 4: @Async와 @Transactional 조합 검증

```java
/**
 * @Async + @Transactional 조합 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class AsyncTransactionalTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: @Async와 @Transactional을 같은 메서드에 사용 금지
     * (트랜잭션 컨텍스트가 비동기 스레드로 전파되지 않음)
     */
    @Test
    void asyncAndTransactionalShouldNotBeUsedTogether() {
        ArchRule rule = methods()
            .that().areAnnotatedWith(org.springframework.scheduling.annotation.Async.class)
            .should().notBeAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .because("Transaction context does not propagate to async threads");

        rule.check(importedClasses);
    }
}
```

---

### 규칙 5: @Validated vs @Valid 사용 규칙

```java
/**
 * Validation 애노테이션 사용 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class ValidationAnnotationTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: @Valid는 Controller 메서드 파라미터에만 사용
     */
    @Test
    void validShouldBeUsedOnControllerParameters() {
        ArchRule rule = methods()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.PostMapping.class)
            .or().areAnnotatedWith(org.springframework.web.bind.annotation.PutMapping.class)
            .should().haveRawParameterTypes(
                JavaClass.Predicates.assignableTo(jakarta.validation.Valid.class)
            )
            .because("Controller should validate request DTOs");

        rule.check(importedClasses);
    }

    /**
     * 규칙: @Validated는 Service 클래스에만 사용
     */
    @Test
    void validatedShouldBeUsedOnServiceClasses() {
        ArchRule rule = classes()
            .that().areAnnotatedWith(org.springframework.validation.annotation.Validated.class)
            .should().resideInAPackage("..application.service..")
            .because("@Validated is for method-level validation in services");

        rule.check(importedClasses);
    }
}
```

---

## 📋 애노테이션 사용 체크리스트

### @Transactional
- [ ] Public 메서드에만 사용
- [ ] Final 메서드/클래스 금지
- [ ] Domain Layer에서 사용 금지
- [ ] `@Async`와 동시 사용 금지

### @Entity
- [ ] Domain Layer에서 사용 금지
- [ ] Persistence Layer에만 위치
- [ ] `*JpaEntity` 접미사 사용

### Spring Annotations (@Service, @Repository, @Component)
- [ ] Domain Layer에서 사용 금지
- [ ] `@Service`는 Application Service만
- [ ] `@RestController`는 Web Adapter만
- [ ] `@Repository`는 Persistence Adapter만

---

## 🛠️ Git Pre-commit Hook 통합

**`hooks/validators/validate-annotations.sh`**:
```bash
#!/bin/bash

echo "🔍 Validating annotation usage..."

# @Transactional on private methods
if git diff --cached --name-only | grep -E '\.java$' | xargs grep -l '@Transactional' | xargs grep -l 'private.*@Transactional'; then
    echo "❌ Error: @Transactional on private method detected!"
    exit 1
fi

# @Entity in domain layer
if git diff --cached --name-only | grep -E 'domain/.*\.java$' | xargs grep -l '@Entity'; then
    echo "❌ Error: @Entity in domain layer detected!"
    exit 1
fi

# @Service in domain layer
if git diff --cached --name-only | grep -E 'domain/.*\.java$' | xargs grep -l '@Service'; then
    echo "❌ Error: @Service in domain layer detected!"
    exit 1
fi

echo "✅ Annotation validation passed."
```

---

## 📚 참고 자료

- [Spring AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
