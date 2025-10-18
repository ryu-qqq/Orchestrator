# Layer Dependency Rules - ArchUnit으로 헥사고날 아키텍처 검증

**목적**: ArchUnit을 활용하여 헥사고날 아키텍처의 레이어 의존성 규칙을 자동 검증

**관련 문서**:
- [Transaction Management](../../03-application-layer/transaction-management/01_transaction-boundaries.md)
- [Law of Demeter](../../02-domain-layer/law-of-demeter/01_getter-chaining-prohibition.md)

**검증 도구**: ArchUnit 1.2.0+

---

## 📌 핵심 원칙

### 헥사고날 아키텍처 레이어 의존성 규칙

```
┌────────────────────────────────────────────┐
│          External World                     │
│  (Web, DB, Message Queue, External API)    │
└────────────────────────────────────────────┘
                    ↕
┌────────────────────────────────────────────┐
│        Application Layer (Adapters)        │
│  ┌──────────────┐      ┌───────────────┐  │
│  │ Inbound      │      │ Outbound      │  │
│  │ (Controller) │      │ (Repository)  │  │
│  └──────────────┘      └───────────────┘  │
└────────────────────────────────────────────┘
                    ↓ (의존 방향)
┌────────────────────────────────────────────┐
│           Domain Layer (Core)              │
│  ┌─────────┐  ┌──────┐  ┌──────────────┐  │
│  │ Entity  │  │ Port │  │ Domain       │  │
│  │ (Agg.)  │  │(I/F) │  │ Service      │  │
│  └─────────┘  └──────┘  └──────────────┘  │
└────────────────────────────────────────────┘
```

**핵심 규칙**:
1. **Domain Layer는 어떤 레이어에도 의존하지 않음** (순수 Java)
2. **Application Layer는 Domain Layer에만 의존**
3. **외부 세계는 Application Layer를 통해서만 Domain에 접근**

---

## ❌ 금지 패턴 (Anti-Patterns)

### Anti-Pattern 1: Domain → Application 의존성

```java
// ❌ Domain Layer에서 Application Layer 의존
package com.company.domain.order;

import com.company.application.in.web.OrderController; // ❌ Domain → Web

public class Order {
    private OrderController controller; // ❌ 절대 금지!

    public void processOrder() {
        controller.notifyUser(); // ❌ Domain이 Application 호출
    }
}
```

**문제점**:
- Domain의 순수성 훼손
- 테스트 어려움 (Web 의존성 필요)
- 순환 의존성 위험

---

### Anti-Pattern 2: Domain → Framework 의존성

```java
// ❌ Domain Layer에서 Spring Framework 의존
package com.company.domain.order;

import org.springframework.stereotype.Service; // ❌ Framework 의존

@Service // ❌ Domain에 Spring 애노테이션
public class OrderService {
    // Domain Service는 순수 Java여야 함
}
```

**문제점**:
- Framework 변경 시 Domain 영향
- Domain의 재사용성 저하
- 단위 테스트 복잡도 증가

---

### Anti-Pattern 3: Controller → Repository 직접 호출

```java
// ❌ Controller에서 Repository 직접 호출 (UseCase 우회)
package com.company.application.in.web;

import com.company.application.out.persistence.OrderRepository;

@RestController
public class OrderController {
    private final OrderRepository repository; // ❌ UseCase 없이 Repository 직접 호출

    @PostMapping("/orders")
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        Order order = repository.save(Order.create(request)); // ❌ 비즈니스 로직 누락
        return OrderResponse.from(order);
    }
}
```

**문제점**:
- 비즈니스 로직이 Controller에 분산
- 트랜잭션 경계 불명확
- 재사용성 저하

---

## ✅ ArchUnit 검증 규칙

### 규칙 1: Domain Layer 의존성 검증

```java
package com.company.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Domain Layer 의존성 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class DomainLayerDependencyTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Domain Layer는 Application Layer에 의존하지 않음
     */
    @Test
    void domainShouldNotDependOnApplication() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Domain Layer는 Spring Framework에 의존하지 않음
     */
    @Test
    void domainShouldNotDependOnSpringFramework() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Domain Layer는 Jakarta EE (구 Java EE)에 의존하지 않음
     */
    @Test
    void domainShouldNotDependOnJakartaEE() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("jakarta..");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ Domain → Application 의존성 금지
- ✅ Domain → Spring 의존성 금지
- ✅ Domain → Jakarta EE 의존성 금지

---

### 규칙 2: Application Layer 의존성 검증

```java
/**
 * Application Layer 의존성 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class ApplicationLayerDependencyTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Controller는 UseCase를 통해서만 Domain에 접근
     */
    @Test
    void controllersShouldOnlyAccessDomainThroughUseCases() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..application.in.web..")
            .should().dependOnClassesThat().resideInAPackage("..application.out..")
            .because("Controllers should use UseCases, not Repositories directly");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Adapter (Repository 구현체)는 Port (인터페이스)를 구현해야 함
     */
    @Test
    void adaptersShouldImplementPorts() {
        ArchRule rule = classes()
            .that().resideInAPackage("..application.out..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().implement(JavaClass.Predicates.resideInAPackage("..domain.port.out.."));

        rule.check(importedClasses);
    }

    /**
     * 규칙: Application Layer는 Domain Layer에만 의존
     */
    @Test
    void applicationShouldOnlyDependOnDomain() {
        ArchRule rule = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..domain..",
                "java..",
                "org.springframework..",
                "jakarta..",
                "com.fasterxml.jackson.."
            );

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ Controller → Repository 직접 호출 금지
- ✅ Adapter는 Port 구현 필수
- ✅ Application은 Domain에만 의존

---

### 규칙 3: Port-Adapter 패턴 검증

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Port-Adapter 패턴 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class PortAdapterPatternTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Port (인터페이스)는 Domain Layer에 위치
     */
    @Test
    void portsShouldResideInDomainLayer() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Port")
            .should().resideInAPackage("..domain.port..")
            .because("Ports are domain contracts");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Adapter (구현체)는 Application Layer에 위치
     */
    @Test
    void adaptersShouldResideInApplicationLayer() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Adapter")
            .should().resideInAPackage("..application..")
            .because("Adapters are infrastructure implementation");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Inbound Port (UseCase)는 domain.port.in 패키지에 위치
     */
    @Test
    void useCasesShouldResideInInboundPortPackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().resideInAPackage("..domain.port.in..")
            .andShould().beInterfaces()
            .because("UseCases are inbound ports");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Outbound Port (Repository, External API)는 domain.port.out 패키지에 위치
     */
    @Test
    void repositoryInterfacesShouldResideInOutboundPortPackage() {
        ArchRule rule = classes()
            .that().areInterfaces()
            .and().haveSimpleNameEndingWith("Port")
            .and().resideInAPackage("..domain.port.out..")
            .should().bePublic()
            .because("Outbound ports are domain contracts for infrastructure");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ Port는 Domain Layer (`domain.port`)
- ✅ Adapter는 Application Layer (`application`)
- ✅ UseCase는 Inbound Port (`domain.port.in`)
- ✅ Repository 인터페이스는 Outbound Port (`domain.port.out`)

---

## 🎯 실전 예제: 올바른 레이어 구조

### ✅ Example 1: Order Aggregate + Port-Adapter

**Domain Layer (Core)**:
```java
// domain/order/Order.java
package com.company.domain.order;

/**
 * Order Aggregate Root
 *
 * @author development-team
 * @since 1.0.0
 */
public class Order {
    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;

    private Order(OrderId id, CustomerId customerId) {
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.PENDING;
    }

    public static Order create(CustomerId customerId) {
        return new Order(OrderId.generate(), customerId);
    }

    public void approve() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be approved");
        }
        this.status = OrderStatus.APPROVED;
    }

    // Getters (NO Setters)
    public OrderId getId() { return id; }
    public CustomerId getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
}
```

**Inbound Port (UseCase)**:
```java
// domain/port/in/CreateOrderUseCase.java
package com.company.domain.port.in;

import com.company.domain.order.OrderId;

/**
 * Create Order UseCase (Inbound Port)
 *
 * @author development-team
 * @since 1.0.0
 */
public interface CreateOrderUseCase {
    OrderId createOrder(CreateOrderCommand command);
}
```

**Outbound Port (Repository Interface)**:
```java
// domain/port/out/LoadOrderPort.java
package com.company.domain.port.out;

import com.company.domain.order.Order;
import com.company.domain.order.OrderId;

import java.util.Optional;

/**
 * Load Order Port (Outbound Port)
 *
 * @author development-team
 * @since 1.0.0
 */
public interface LoadOrderPort {
    Optional<Order> loadOrder(OrderId orderId);
    void saveOrder(Order order);
}
```

**Application Layer - Inbound Adapter (Controller)**:
```java
// application/in/web/OrderController.java
package com.company.application.in.web;

import com.company.domain.port.in.CreateOrderUseCase;
import org.springframework.web.bind.annotation.*;

/**
 * Order REST Controller (Inbound Adapter)
 *
 * @author development-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final CreateOrderUseCase createOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        CreateOrderCommand command = OrderMapper.toCommand(request);
        OrderId orderId = createOrderUseCase.createOrder(command);
        return OrderResponse.from(orderId);
    }
}
```

**Application Layer - Outbound Adapter (Repository 구현체)**:
```java
// application/out/persistence/OrderPersistenceAdapter.java
package com.company.application.out.persistence;

import com.company.domain.order.Order;
import com.company.domain.order.OrderId;
import com.company.domain.port.out.LoadOrderPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Order Persistence Adapter (Outbound Adapter)
 *
 * @author development-team
 * @since 1.0.0
 */
@Component
public class OrderPersistenceAdapter implements LoadOrderPort {
    private final OrderJpaRepository jpaRepository;

    public OrderPersistenceAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Order> loadOrder(OrderId orderId) {
        return jpaRepository.findById(orderId.getValue())
            .map(OrderMapper::toDomain);
    }

    @Override
    public void saveOrder(Order order) {
        OrderJpaEntity entity = OrderMapper.toEntity(order);
        jpaRepository.save(entity);
    }
}
```

**ArchUnit 검증 결과**:
```
✅ Domain Layer는 Application Layer에 의존하지 않음
✅ Domain Layer는 Spring Framework에 의존하지 않음
✅ Controller는 UseCase를 통해서만 Domain 접근
✅ Adapter는 Port를 구현함
✅ Port는 Domain Layer에 위치
✅ Adapter는 Application Layer에 위치
```

---

## 🔧 고급 ArchUnit 규칙

### 규칙 4: 순환 의존성 검증

```java
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 순환 의존성 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class CyclicDependencyTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: 패키지 간 순환 의존성 금지
     */
    @Test
    void noCircularDependenciesBetweenPackages() {
        ArchRule rule = slices()
            .matching("com.company.(*)..")
            .should().beFreeOfCycles()
            .because("Circular dependencies make the system hard to maintain");

        rule.check(importedClasses);
    }
}
```

---

### 규칙 5: 애노테이션 레이어 제약

```java
/**
 * 애노테이션 레이어 제약 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class AnnotationLayerConstraintTest {

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
            .orShould().beAnnotatedWith(org.springframework.stereotype.Repository.class);

        rule.check(importedClasses);
    }

    /**
     * 규칙: Controller는 @RestController 또는 @Controller 필수
     */
    @Test
    void controllersShouldHaveControllerAnnotation() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().resideInAPackage("..application.in.web..")
            .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .orShould().beAnnotatedWith(org.springframework.stereotype.Controller.class);

        rule.check(importedClasses);
    }
}
```

---

## 📋 레이어 의존성 체크리스트

### Domain Layer
- [ ] Spring Framework 의존성 없음
- [ ] Jakarta EE 의존성 없음
- [ ] Application Layer 의존성 없음
- [ ] 순수 Java + Domain 로직만 포함

### Application Layer
- [ ] Domain Layer에만 의존
- [ ] Controller는 UseCase 통해서만 Domain 접근
- [ ] Adapter는 Port 구현
- [ ] Repository 구현체는 Outbound Adapter

### Port-Adapter Pattern
- [ ] Port (인터페이스)는 Domain Layer
- [ ] Adapter (구현체)는 Application Layer
- [ ] UseCase는 Inbound Port
- [ ] Repository 인터페이스는 Outbound Port

---

## 🛠️ Git Pre-commit Hook 통합

**`.git/hooks/pre-commit`**:
```bash
#!/bin/bash

echo "🔍 Running ArchUnit Layer Dependency Tests..."

# ArchUnit 테스트 실행
./gradlew test --tests "*LayerDependencyTest" --tests "*PortAdapterPatternTest"

if [ $? -ne 0 ]; then
    echo "❌ ArchUnit tests failed. Commit rejected."
    echo "Please fix layer dependency violations."
    exit 1
fi

echo "✅ ArchUnit tests passed."
```

---

## 📚 참고 자료

- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
