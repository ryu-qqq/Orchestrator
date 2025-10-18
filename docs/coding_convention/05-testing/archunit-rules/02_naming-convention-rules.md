# Naming Convention Rules - ArchUnit으로 네이밍 규칙 검증

**목적**: ArchUnit을 활용하여 Port, Adapter, UseCase, Command/Query/Event 네이밍 규칙을 자동 검증

**관련 문서**:
- [Layer Dependency Rules](./01_layer-dependency-rules.md)
- [Annotation Rules](./03_annotation-rules.md)

**검증 도구**: ArchUnit 1.2.0+

---

## 📌 핵심 네이밍 규칙

### 헥사고날 아키텍처 네이밍 컨벤션

| 타입 | 네이밍 규칙 | 위치 | 예시 |
|------|------------|------|------|
| **Inbound Port** | `*UseCase` (Interface) | `domain.port.in` | `CreateOrderUseCase` |
| **Outbound Port** | `*Port` (Interface) | `domain.port.out` | `LoadOrderPort` |
| **Inbound Adapter** | `*Controller` | `application.in.web` | `OrderController` |
| **Outbound Adapter** | `*Adapter` | `application.out.*` | `OrderPersistenceAdapter` |
| **Command** | `*Command` (Record) | `domain.port.in` | `CreateOrderCommand` |
| **Query** | `*Query` (Record) | `domain.port.in` | `GetOrderQuery` |
| **Event** | `*Event` (Record) | `domain.event` | `OrderCreatedEvent` |
| **Aggregate** | 명사 (Class) | `domain.*` | `Order`, `Customer` |

---

## ❌ 금지 패턴 (Anti-Patterns)

### Anti-Pattern 1: Port 네이밍 규칙 위반

```java
// ❌ Inbound Port에 "UseCase" 접미사 누락
package com.company.domain.port.in;

public interface CreateOrder { // ❌ "UseCase" 접미사 필요
    OrderId create(CreateOrderCommand command);
}

// ✅ Correct
public interface CreateOrderUseCase { // ✅ "UseCase" 접미사
    OrderId createOrder(CreateOrderCommand command);
}
```

**ArchUnit 검증 실패**:
```
java.lang.AssertionError: Architecture Violation [Priority: MEDIUM] -
Rule 'classes that reside in a package '..domain.port.in..'
should have simple name ending with 'UseCase'' was violated (1 times):
Class <com.company.domain.port.in.CreateOrder> does not have simple name ending with 'UseCase'
```

---

### Anti-Pattern 2: Adapter 네이밍 규칙 위반

```java
// ❌ Outbound Adapter에 "Adapter" 접미사 누락
package com.company.application.out.persistence;

@Component
public class OrderRepository implements LoadOrderPort { // ❌ "Adapter" 접미사 필요
    // ...
}

// ✅ Correct
@Component
public class OrderPersistenceAdapter implements LoadOrderPort { // ✅ "Adapter" 접미사
    // ...
}
```

**ArchUnit 검증 실패**:
```
Architecture Violation -
Class <com.company.application.out.persistence.OrderRepository>
should have simple name ending with 'Adapter'
```

---

### Anti-Pattern 3: Command/Query 네이밍 규칙 위반

```java
// ❌ Command에 "Command" 접미사 누락
package com.company.domain.port.in;

public record CreateOrderRequest( // ❌ "Command" 접미사 필요 (Request는 Web Layer용)
    CustomerId customerId,
    List<OrderLineItem> items
) {}

// ✅ Correct (Application Layer Input)
public record CreateOrderCommand( // ✅ "Command" 접미사
    CustomerId customerId,
    List<OrderLineItem> items
) {}
```

---

## ✅ ArchUnit 검증 규칙

### 규칙 1: Inbound Port (UseCase) 네이밍 검증

```java
package com.company.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Inbound Port 네이밍 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class InboundPortNamingTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Inbound Port는 "*UseCase" 접미사 필수
     */
    @Test
    void inboundPortsShouldEndWithUseCase() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain.port.in..")
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("UseCase")
            .because("Inbound Ports represent use cases");

        rule.check(importedClasses);
    }

    /**
     * 규칙: UseCase는 public 인터페이스여야 함
     */
    @Test
    void useCasesShouldBePublicInterfaces() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().beInterfaces()
            .andShould().bePublic()
            .because("UseCases are contracts for external world");

        rule.check(importedClasses);
    }

    /**
     * 규칙: UseCase는 domain.port.in 패키지에만 위치
     */
    @Test
    void useCasesShouldResideInDomainPortInPackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().resideInAPackage("..domain.port.in..")
            .because("UseCases are inbound ports");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ `*UseCase` 접미사 필수
- ✅ Public Interface 여야 함
- ✅ `domain.port.in` 패키지에 위치

---

### 규칙 2: Outbound Port 네이밍 검증

```java
/**
 * Outbound Port 네이밍 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class OutboundPortNamingTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Outbound Port는 "*Port" 접미사 필수
     */
    @Test
    void outboundPortsShouldEndWithPort() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain.port.out..")
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("Port")
            .because("Outbound Ports represent external dependencies");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Outbound Port는 Load/Save/Send 등의 동사로 시작
     */
    @Test
    void outboundPortsShouldStartWithVerb() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain.port.out..")
            .and().areInterfaces()
            .should().haveSimpleNameMatching("(Load|Save|Send|Publish|Fetch|Delete|Update).*Port")
            .because("Outbound Ports should describe the action");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ `*Port` 접미사 필수
- ✅ `Load/Save/Send/Publish` 등 동사로 시작
- ✅ `domain.port.out` 패키지에 위치

---

### 규칙 3: Adapter 네이밍 검증

```java
/**
 * Adapter 네이밍 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class AdapterNamingTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Outbound Adapter는 "*Adapter" 접미사 필수
     */
    @Test
    void outboundAdaptersShouldEndWithAdapter() {
        ArchRule rule = classes()
            .that().resideInAPackage("..application.out..")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Adapter")
            .because("Adapters are infrastructure implementations");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Inbound Adapter (Controller)는 "*Controller" 접미사 필수
     */
    @Test
    void inboundAdaptersShouldEndWithController() {
        ArchRule rule = classes()
            .that().resideInAPackage("..application.in.web..")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Controller")
            .because("Controllers are inbound adapters");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Adapter는 Port를 구현해야 함
     */
    @Test
    void adaptersShouldImplementPorts() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Adapter")
            .should().implement(JavaClass.Predicates.resideInAPackage("..domain.port.out.."))
            .because("Adapters implement port interfaces");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ Outbound Adapter는 `*Adapter` 접미사
- ✅ Inbound Adapter (Controller)는 `*Controller` 접미사
- ✅ Adapter는 Port를 구현

---

### 규칙 4: Command/Query/Event 네이밍 검증

```java
/**
 * CQRS 네이밍 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class CqrsNamingTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Command는 "*Command" 접미사 필수
     */
    @Test
    void commandsShouldEndWithCommand() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain.port.in..")
            .and().areRecords()
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Command")
            .because("Commands represent state-changing operations");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Query는 "*Query" 접미사 필수
     */
    @Test
    void queriesShouldEndWithQuery() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain.port.in..")
            .and().areRecords()
            .and().haveSimpleNameMatching("(Get|Find|Search|List).*")
            .should().haveSimpleNameEndingWith("Query")
            .because("Queries represent read operations");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Domain Event는 "*Event" 접미사 필수
     */
    @Test
    void eventsShouldEndWithEvent() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain.event..")
            .and().areRecords()
            .should().haveSimpleNameEndingWith("Event")
            .because("Domain events represent state changes");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Event는 과거 시제 (Created, Updated, Deleted)
     */
    @Test
    void eventsShouldUsePastTense() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Event")
            .should().haveSimpleNameMatching(".*(Created|Updated|Deleted|Approved|Rejected|Cancelled)Event")
            .because("Events describe something that already happened");

        rule.check(importedClasses);
    }
}
```

**검증 범위**:
- ✅ Command는 `*Command` 접미사
- ✅ Query는 `Get/Find/Search` + `*Query`
- ✅ Event는 `*Event` 접미사 + 과거 시제

---

## 🎯 실전 예제: 올바른 네이밍 컨벤션

### ✅ Example 1: Order UseCase + Command

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
public interface CreateOrderUseCase { // ✅ "UseCase" 접미사
    OrderId createOrder(CreateOrderCommand command);
}
```

**Command (CQRS)**:
```java
// domain/port/in/CreateOrderCommand.java
package com.company.domain.port.in;

import com.company.domain.order.CustomerId;
import com.company.domain.order.OrderLineItem;

import java.util.List;

/**
 * Create Order Command
 *
 * @author development-team
 * @since 1.0.0
 */
public record CreateOrderCommand( // ✅ "Command" 접미사
    CustomerId customerId,
    List<OrderLineItem> items
) {}
```

**ArchUnit 검증 결과**:
```
✅ CreateOrderUseCase ends with "UseCase"
✅ CreateOrderUseCase is public interface
✅ CreateOrderCommand ends with "Command"
✅ CreateOrderCommand is record
```

---

### ✅ Example 2: Query + Outbound Port

**Query (CQRS)**:
```java
// domain/port/in/GetOrderQuery.java
package com.company.domain.port.in;

import com.company.domain.order.OrderId;

/**
 * Get Order Query
 *
 * @author development-team
 * @since 1.0.0
 */
public record GetOrderQuery( // ✅ "Get" 접두사 + "Query" 접미사
    OrderId orderId
) {}
```

**Outbound Port**:
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
public interface LoadOrderPort { // ✅ "Load" 접두사 + "Port" 접미사
    Optional<Order> loadOrder(OrderId orderId);
}
```

**ArchUnit 검증 결과**:
```
✅ GetOrderQuery ends with "Query"
✅ GetOrderQuery starts with "Get"
✅ LoadOrderPort ends with "Port"
✅ LoadOrderPort starts with "Load"
```

---

### ✅ Example 3: Event + Adapter

**Domain Event**:
```java
// domain/event/OrderCreatedEvent.java
package com.company.domain.event;

import com.company.domain.order.OrderId;
import com.company.domain.order.CustomerId;

import java.time.Instant;

/**
 * Order Created Event (Domain Event)
 *
 * @author development-team
 * @since 1.0.0
 */
public record OrderCreatedEvent( // ✅ "Created" (과거 시제) + "Event" 접미사
    OrderId orderId,
    CustomerId customerId,
    Instant occurredAt
) {}
```

**Outbound Adapter**:
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
public class OrderPersistenceAdapter implements LoadOrderPort { // ✅ "Adapter" 접미사
    private final OrderJpaRepository jpaRepository;

    public OrderPersistenceAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Order> loadOrder(OrderId orderId) {
        return jpaRepository.findById(orderId.getValue())
            .map(OrderMapper::toDomain);
    }
}
```

**ArchUnit 검증 결과**:
```
✅ OrderCreatedEvent ends with "Event"
✅ OrderCreatedEvent uses past tense ("Created")
✅ OrderPersistenceAdapter ends with "Adapter"
✅ OrderPersistenceAdapter implements LoadOrderPort
```

---

## 🔧 고급 네이밍 규칙

### 규칙 5: Service 네이밍 규칙

```java
/**
 * Service 네이밍 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class ServiceNamingTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Application Service는 "*Service" 접미사 필수
     */
    @Test
    void applicationServicesShouldEndWithService() {
        ArchRule rule = classes()
            .that().resideInAPackage("..application.service..")
            .and().areAnnotatedWith(org.springframework.stereotype.Service.class)
            .should().haveSimpleNameEndingWith("Service")
            .because("Application Services implement use cases");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Domain Service는 "*Service" 접미사 필수 (애노테이션 없음)
     */
    @Test
    void domainServicesShouldEndWithService() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain.service..")
            .should().haveSimpleNameEndingWith("Service")
            .andShould().notBeAnnotatedWith(org.springframework.stereotype.Service.class)
            .because("Domain Services are pure domain logic without Spring annotations");

        rule.check(importedClasses);
    }
}
```

---

### 규칙 6: Aggregate/Entity/Value Object 네이밍 규칙

```java
/**
 * Domain Model 네이밍 규칙 검증
 *
 * @author development-team
 * @since 1.0.0
 */
class DomainModelNamingTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.company");

    /**
     * 규칙: Aggregate Root는 명사형 (접미사 없음)
     */
    @Test
    void aggregatesShouldBeNouns() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain..")
            .and().areNotInterfaces()
            .and().areNotRecords()
            .and().areNotEnums()
            .should().haveSimpleNameNotEndingWith("Service")
            .andShould().haveSimpleNameNotEndingWith("Port")
            .andShould().haveSimpleNameNotEndingWith("UseCase")
            .because("Aggregates are domain entities representing business concepts");

        rule.check(importedClasses);
    }

    /**
     * 규칙: Value Object는 명사형 (Record 권장)
     */
    @Test
    void valueObjectsShouldBeRecords() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleNameEndingWith("Id") // OrderId, CustomerId 등
            .should().beRecords()
            .because("Value Objects should be immutable records");

        rule.check(importedClasses);
    }
}
```

---

## 📋 네이밍 컨벤션 체크리스트

### Inbound Port (UseCase)
- [ ] `*UseCase` 접미사 사용
- [ ] Public Interface
- [ ] `domain.port.in` 패키지에 위치

### Outbound Port
- [ ] `*Port` 접미사 사용
- [ ] `Load/Save/Send` 등 동사로 시작
- [ ] `domain.port.out` 패키지에 위치

### Adapter
- [ ] Outbound Adapter: `*Adapter` 접미사
- [ ] Inbound Adapter (Controller): `*Controller` 접미사
- [ ] Port를 구현

### Command/Query/Event
- [ ] Command: `*Command` 접미사
- [ ] Query: `Get/Find/Search` + `*Query`
- [ ] Event: 과거 시제 + `*Event` 접미사

### Domain Model
- [ ] Aggregate Root: 명사형 (접미사 없음)
- [ ] Value Object: 명사형 + Record (ID는 `*Id` 접미사)

---

## 🛠️ CI/CD 통합

**`.github/workflows/archunit-naming.yml`**:
```yaml
name: ArchUnit Naming Convention Check

on:
  pull_request:
    branches: [main, develop]

jobs:
  naming-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run ArchUnit Naming Tests
        run: |
          ./gradlew test --tests "*NamingTest"
      - name: Upload Test Report
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: archunit-naming-report
          path: build/reports/tests/test
```

---

## 📚 참고 자료

- [ArchUnit User Guide - Naming Conventions](https://www.archunit.org/userguide/html/000_Index.html#_naming_conventions)
- [Domain-Driven Design - Eric Evans](https://www.domainlanguage.com/ddd/)
- [Implementing Domain-Driven Design - Vaughn Vernon](https://vaughnvernon.com/)

---

**작성자**: Development Team
**최종 수정일**: 2025-10-16
**버전**: 1.0.0
