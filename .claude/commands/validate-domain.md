# /validate-domain - Domain 레이어 코드 검증

Domain 레이어 파일의 규칙 준수 여부를 검증합니다.

## 사용법

```bash
/validate-domain <file_path>
```

## 예제

```bash
# 특정 파일 검증
/validate-domain domain/src/main/java/com/company/template/order/domain/model/Order.java

# 상대 경로도 지원
/validate-domain Order.java
```

## 작업 수행

당신은 **Domain 레이어 코드 검증 전문가**입니다. 다음 단계를 수행하세요:

1. **파일 경로 확인**
   - 사용자가 제공한 파일 경로 확인
   - 상대 경로인 경우 `domain/` 디렉토리에서 검색
   - 파일이 존재하지 않으면 에러 메시지 출력

2. **validation-helper.py 실행**
   ```bash
   python3 .claude/hooks/scripts/validation-helper.py <file_path> domain
   ```

3. **검증 결과 해석**
   - ✅ **통과**: 모든 규칙 준수
   - ❌ **실패**: 위반 규칙 리스트 출력
     - 위반 규칙 설명
     - 금지 사항 목록
     - 참고 문서 링크

4. **개선 제안 (실패 시)**
   - 구체적인 수정 방법 제안
   - 코드 예제 제공
   - 관련 문서 링크

## 검증 규칙 (Domain 레이어)

### ❌ Zero-Tolerance 규칙

- **Lombok 금지**: @Data, @Builder, @Getter, @Setter 등
- **Spring/JPA 금지**: @Entity, @Table, @Service, @Repository
- **Law of Demeter**: Getter 체이닝 금지 (예: `order.getCustomer().getAddress()`)

### ✅ 필수 규칙

- **Pure Java**: Spring, JPA 의존 없음
- **Javadoc**: @author, @since 포함
- **Tell, Don't Ask**: 메서드로 행동 캡슐화
- **Immutability**: 불변 객체 권장

## 출력 예시

### 성공 케이스

```
---

✅ **Validation Passed**

파일: `domain/src/main/java/.../Order.java`

모든 규칙을 준수합니다!

---
```

### 실패 케이스

```
---

⚠️ **Validation Failed**

**파일**: `domain/src/main/java/.../Order.java`

**규칙 위반**: Law of Demeter - Getter 체이닝 금지

**문제**: Anti-pattern detected: order.getCustomer().getAddress()

**금지 사항**:
- ❌ order.getCustomer().getAddress().getZip()
- ❌ order.getOrderItems().get(0).getProduct()
- ❌ customer.getAddress().getCity().getZipCode()

**참고**: `docs/coding_convention/02-domain-layer/law-of-demeter/01_getter-chaining-prohibition.md`

💡 코드를 수정한 후 다시 시도하세요.

---
```

## 참고

- Cache 기반 검증 시스템 사용
- Critical 규칙만 검증 (성능 최적화)
- 실행 시간: ~100ms

