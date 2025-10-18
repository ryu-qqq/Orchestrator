#!/bin/bash
# ========================================
# Law of Demeter Validator
# ========================================
# 데미터의 법칙 (Law of Demeter) 검증
# - Getter 체이닝 감지
# - Train wreck 패턴 금지
# - Tell, Don't Ask 원칙 준수
#
# @author Sangwon Ryu (ryu@company.com)
# @since 2025-01-10
# ========================================

set -e

FILE="$1"

# Java 파일만 검증
if [[ ! "$FILE" =~ \.java$ ]]; then
    exit 0
fi

# Colors
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

log_error() {
    echo -e "${RED}❌ [Demeter] $1${NC}" >&2
}

log_warning() {
    echo -e "${YELLOW}⚠️  [Demeter] $1${NC}" >&2
}

log_info() {
    echo -e "${GREEN}ℹ️  [Demeter] $1${NC}" >&2
}

VIOLATIONS=0

# ========================================
# 패턴 1: Getter 체이닝 감지
# ========================================

log_info "Checking getter chaining in: $(basename "$FILE")"

# .get...().get...() 패턴 (2단계 이상 체이닝)
GETTER_CHAIN=$(grep -nE '\.get[A-Z]\w*\(\)\s*\.get[A-Z]\w*\(\)' "$FILE" || true)

if [ -n "$GETTER_CHAIN" ]; then
    log_error "Getter chaining detected (데미터 법칙 위반)"
    echo "$GETTER_CHAIN" | while read -r line; do
        LINE_NUM=$(echo "$line" | cut -d: -f1)
        CODE=$(echo "$line" | cut -d: -f2-)
        log_error "  Line $LINE_NUM: ${CODE}"
    done

    cat << EOF

⚠️  데미터의 법칙 위반: Getter 체이닝

파일: $FILE

위반 패턴:
  obj.getX().getY()  // ❌ Train wreck

수정 방법:
  obj.getXY()        // ✅ 위임 메서드 추가

예시:
  // BEFORE
  String city = order.getUser().getAddress().getCity();

  // AFTER
  String city = order.getUserCity();

  // Order 클래스 내부
  public String getUserCity() {
      return user.getAddressCity();
  }

참고: global_rules.md의 "데미터의 법칙" 섹션
EOF
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ========================================
# 패턴 2: 3단계 이상 체이닝 (더 엄격)
# ========================================

DEEP_CHAIN=$(grep -nE '\.get[A-Z]\w*\(\)\s*\.get[A-Z]\w*\(\)\s*\.get[A-Z]\w*\(\)' "$FILE" || true)

if [ -n "$DEEP_CHAIN" ]; then
    log_error "Deep getter chaining detected (심각한 데미터 위반)"
    echo "$DEEP_CHAIN" | while read -r line; do
        LINE_NUM=$(echo "$line" | cut -d: -f1)
        CODE=$(echo "$line" | cut -d: -f2-)
        log_error "  Line $LINE_NUM: ${CODE}"
    done
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ========================================
# 패턴 3: 중간 객체 조작 감지
# ========================================

# .get...().add/set/remove 패턴
INTERMEDIATE_MANIPULATION=$(grep -nE '\.get[A-Z]\w*\(\)\s*\.(add|set|remove|put)' "$FILE" || true)

if [ -n "$INTERMEDIATE_MANIPULATION" ]; then
    log_error "Intermediate object manipulation detected (Tell, Don't Ask 위반)"
    echo "$INTERMEDIATE_MANIPULATION" | while read -r line; do
        LINE_NUM=$(echo "$line" | cut -d: -f1)
        CODE=$(echo "$line" | cut -d: -f2-)
        log_error "  Line $LINE_NUM: ${CODE}"
    done

    cat << EOF

⚠️  Tell, Don't Ask 원칙 위반

위반 패턴:
  order.getItems().add(item)  // ❌ 중간 객체 조작

수정 방법:
  order.addItem(item)         // ✅ Tell, Don't Ask

참고: 객체에게 "무엇을 하라"고 말하세요 (Tell)
      "어떻게 되어있는지" 묻지 마세요 (Don't Ask)
EOF
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ========================================
# 허용 패턴 확인 (False Positive 방지)
# ========================================

# Builder 패턴은 허용
BUILDER_PATTERN=$(grep -E '\.builder\(\)' "$FILE" || true)
if [ -n "$BUILDER_PATTERN" ]; then
    log_info "Builder pattern detected (allowed)"
fi

# Stream API는 허용
STREAM_PATTERN=$(grep -E '\.stream\(\)' "$FILE" || true)
if [ -n "$STREAM_PATTERN" ]; then
    log_info "Stream API detected (allowed)"
fi

# StringBuilder는 허용
STRINGBUILDER_PATTERN=$(grep -E 'StringBuilder|StringBuffer' "$FILE" || true)
if [ -n "$STRINGBUILDER_PATTERN" ]; then
    log_info "StringBuilder pattern detected (allowed)"
fi

# ========================================
# 결과 반환
# ========================================

if [ $VIOLATIONS -gt 0 ]; then
    log_error "Found $VIOLATIONS Law of Demeter violation(s)"
    log_warning "Please refactor to follow Tell, Don't Ask principle"

    cat << EOF

📚 추천 리팩토링 전략:

1. 위임 메서드 추가
   class Order {
       public String getUserCity() {
           return user.getAddressCity();
       }
   }

2. 행동 중심 인터페이스
   // ❌ order.getStatus() == CONFIRMED
   // ✅ order.isConfirmed()

3. 책임 이동
   // ❌ order.getUser().notifyOrderConfirmed()
   // ✅ order.notifyUserOrderConfirmed()

4. Value Object 활용
   class UserCity {
       private final UserId userId;
       // 내부에서 처리
   }

참고 문서:
- config/pmd/pmd-ruleset.xml (PMD 규칙)
- LawOfDemeterTest.java (ArchUnit 테스트)
EOF

    exit 1
fi

log_info "✅ Law of Demeter validation passed"
exit 0
