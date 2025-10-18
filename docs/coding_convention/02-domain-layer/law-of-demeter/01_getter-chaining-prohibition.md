# Law of Demeter — Getter 체이닝 금지 (도메인 레이어 규칙)

> **목표**: 도메인 내부에서 **원거리 탐색(chain)** 을 차단하여 **캡슐화 유지, 변경 영향 최소화, 테스트 용이성**을 확보한다.

본 문서는 **도메인 레이어**에 강제되는 규칙이며, 어댑터 계층은 제한을 완화할 수 있습니다(하단 *예외/완화 규칙* 참조).  
공통 원칙은 [_shared.business-logic-placement.md]을 참고하세요.

---

## 적용 범위
- `/domain` 모듈/패키지 전부
- **애그리게이트 루트 및 엔티티/VO 메서드 본문**
- **애플리케이션 서비스의 비즈니스 로직** (Application 레벨에 위치하는 UseCase 구현체)

## 금지 규칙 (핵심)
- `a.getB().getC().doX()` 형태의 **다단계 호출** 금지
- 다른 애그리게이트 내부 컬렉션/엔티티의 **내부 상태에 직접 접근** 금지
- **DTO/엔티티 변환을 위해** 도메인 깊은 구조를 탐색하는 코드 금지 (변환은 어댑터 책임)

### 허용 화이트리스트 (오탐 방지)
- **Java Streams**: `stream()`, `map`, `filter`, `flatMap`, `collect`
- **Optional**: `map`, `flatMap`, `orElse*`, `ifPresent*`
- **Builder/Fluent API**: 빌더/플루언트 DSL(예: `Money.of(...).multiply(...).add(...)`)
- **BigDecimal/Math** 연산 체이닝
- **패턴 스위치**(Java 21) 및 메서드 참조
- **JPA Lazy 프록시 초기화** 목적의 단일 depth 접근

> 의도는 **“원거리 탐색으로 인한 결합 증가”** 를 막는 것이지, **표현적 API/연산 체이닝 자체**를 막는 것이 아닙니다.

### 어댑터 계층 완화 규칙
- `adapter-in-*`(컨트롤러/메시징), `adapter-out-*`(JPA/S3 등)에서는 **직렬화/매핑 목적**의 얕은 체이닝은 허용합니다.
- 단, **비즈니스 규칙 확인/결정**을 위해 체이닝을 하는 것은 금지합니다(그 로직은 도메인으로 이동).

---

## 리팩터링 패턴
- **Tell, Don’t Ask**: 원격 구조 탐색 대신 **의미 있는 도메인 질의 또는 명령**을 노출합니다.
  - ❌ `order.getCustomer().getAddress().getZip()`  
    ✅ `order.shippingZip()` 또는 `order.belongsTo(zipRegion)`
- **의미 있는 VO 도입**: 중첩 구조를 표현하는 **값 객체**를 도입하고, 질의를 캡슐화합니다.
- **Query(Read) 모델 분리**: 조회 전용 케이스는 **CQRS Read 모델**로 분리하여 체이닝 필요를 낮춥니다.

---

## 예시 (Before → After)

```java
// ❌ Before
if (order.getCustomer().getAddress().getZip().startsWith("06")) {
    discount = discount.add(new BigDecimal("10.00"));
}

// ✅ After
if (order.isShippingToRegion("06")) {
    discount = discount.add(new BigDecimal("10.00"));
}

// 도메인 내부
public boolean isShippingToRegion(String prefix) {
    return this.shippingAddress().zip().startsWith(prefix);
}
```

---

## 정적 분석 & 규칙

### PMD XPath Rule (예시, 허용 패턴 제외)
> *프로젝트 상황에 맞게 조정하세요. 오탐을 줄이기 위해 화이트리스트 메서드를 XPaths에서 제외합니다.*

```xml
<rule name="LawOfDemeterGetterChain" language="java" message="과도한 체이닝 금지 (LoD)"
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule" severity="warn">
    <properties>
        <property name="xpath">
            <value>
                //PrimaryExpression
                    [count(PrimarySuffix/arguments) &gt;= 2]
                    [not(.//Name[contains(., 'stream') or contains(., 'map') or contains(., 'flatMap') or contains(., 'filter') or contains(., 'collect')])]
                    [not(.//Name[contains(., 'Optional')])]
                    [not(.//Name[contains(., 'BigDecimal')])]
            </value>
        </property>
    </properties>
</rule>
```

### Checkstyle Regexp (보조, 휴리스틱)
```xml
<module name="RegexpSinglelineJava">
    <property name="format" value="\)\.[a-zA-Z_][a-zA-Z0-9_]*\(\)\."/>
    <property name="message" value="메서드 체이닝 과다 의심 (검토 필요)"/>
    <property name="ignoreComments" value="true"/>
</module>
```

### @SuppressWarnings 사용 기준
- 오탐일 때만 허용하고, **근거를 주석으로** 남길 것.
- 도메인에서는 최대한 억제하고, **어댑터에서 변환/직렬화 시에 제한적으로 사용**.

---

## 테스트 관점
- **LoD 제거 전/후 테스트**: 복잡한 픽스처 없이 도메인 메서드만으로 시나리오를 재현 가능해야 합니다.
- 도메인 API 한두 개로 **불변식과 규칙**이 드러나는지 확인합니다.

---

## 체크리스트
- [ ] 도메인에서 `a.b().c()` 류 호출이 사라졌는가?
- [ ] 의미 있는 질의/명령 메서드로 치환했는가?
- [ ] 조회-only 경우 Read 모델로 분리했는가?
- [ ] PMD/Checkstyle 예외(화이트리스트) 업데이트는 최신의가 맞는가?

> 공통 원칙: [_shared.business-logic-placement.md] 참조