# Hook 실행 로그

이 디렉토리에는 Dynamic Hooks 시스템의 실행 로그가 저장됩니다.

---

## 📂 로그 파일

### `hook-execution.log`

**자동 생성**: Hook 실행 시 자동으로 생성되고 append됩니다.

**기록 내용**:
- Hook 실행 시각 (Timestamp)
- 사용자 입력 (User Input)
- 감지된 키워드와 레이어 (Detected Layer)
- Context Score (키워드 매칭 점수)
- 규칙 주입 여부 (inject-rules.py 호출)
- 검증 결과 (Validation Passed/Failed)

**로그 형식 예시**:
```
[2025-10-17 18:30:15] user-prompt-submit triggered
User Input: domain aggregate Order 생성해줘
  → Detected: aggregate → domain (+30 score)
  → Detected: domain context (+15 score)
  → Context Score: 45
  → Detected Layers: domain
  → Strategy: CACHE_BASED (inject-rules.py)
  → Injecting rules for layer: domain

[2025-10-17 18:30:22] after-tool-use triggered
File: domain/src/main/java/com/company/template/order/domain/model/Order.java
  → Detected Layer: DOMAIN
  → Running cache-based validation for layer: domain
  ✅ PASSED: No Lombok
  ✅ PASSED: Javadoc @author present
  ✅ PASSED: Pure Java (no Spring/JPA)
  → FINAL RESULT: ALL VALIDATIONS PASSED ✅
```

---

## 🔍 로그 확인 방법

### 1. **실시간 모니터링** (권장)

```bash
# 프로젝트 루트에서
./.claude/hooks/scripts/view-logs.sh -f

# 또는
./view-logs.sh --follow
```

**출력 예시**:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔍 Hook 실행 로그
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

실시간 모니터링 중... (Ctrl+C로 종료)

[2025-10-17 18:30:15] user-prompt-submit triggered
User Input: domain aggregate Order 생성해줘
  → Detected: aggregate → domain (+30 score)
  ...
```

### 2. **마지막 N줄 확인**

```bash
# 마지막 50줄 (기본값)
./view-logs.sh

# 마지막 100줄
./view-logs.sh -n 100
```

### 3. **통계 정보 확인**

```bash
./view-logs.sh -s
# 또는
./view-logs.sh --stats
```

**출력 예시**:
```
📊 Hook 실행 통계
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

총 Hook 실행: 45 회
  - user-prompt-submit: 23 회
  - after-tool-use: 22 회

Layer 감지 통계:
  - DOMAIN: 15 회
  - APPLICATION: 5 회
  - ADAPTER-REST: 3 회

최근 Context Scores:
  - Context Score: 45
  - Context Score: 60
  - Context Score: 30

검증 결과:
  - ✅ Passed: 20
  - ❌ Failed: 2

로그 파일 정보:
  - 크기: 15.3 KB
  - 라인 수: 458
  - 경로: .claude/hooks/logs/hook-execution.log
```

### 4. **직접 파일 확인**

```bash
# 전체 로그
cat .claude/hooks/logs/hook-execution.log

# 마지막 20줄
tail -20 .claude/hooks/logs/hook-execution.log

# 실시간 모니터링
tail -f .claude/hooks/logs/hook-execution.log

# 특정 키워드 검색
grep "DOMAIN" .claude/hooks/logs/hook-execution.log
grep "FAILED" .claude/hooks/logs/hook-execution.log
```

---

## 📊 로그 분석 팁

### 1. **Hook 트리거 여부 확인**

```bash
# user-prompt-submit이 실행되었는지 확인
grep "user-prompt-submit triggered" hook-execution.log

# 특정 시간대 로그
grep "2025-10-17 18:" hook-execution.log
```

### 2. **Context Score 패턴 분석**

```bash
# Context Score가 낮아서 스킵된 경우
grep "Low Context Score" hook-execution.log

# 높은 Context Score
grep "Context Score:" hook-execution.log | awk '{print $NF}' | sort -n
```

### 3. **검증 실패 원인 분석**

```bash
# Lombok 사용 감지
grep "Lombok annotation detected" hook-execution.log

# Javadoc 누락
grep "Missing @author" hook-execution.log

# Spring/JPA 사용 감지
grep "Spring/JPA annotation in domain" hook-execution.log
```

### 4. **레이어별 통계**

```bash
# Domain 레이어만
grep "DOMAIN" hook-execution.log | wc -l

# Application 레이어만
grep "APPLICATION" hook-execution.log | wc -l
```

---

## 🧹 로그 관리

### 로그 삭제

```bash
# view-logs.sh 사용
./view-logs.sh -c

# 또는 직접 삭제
rm .claude/hooks/logs/hook-execution.log
```

### 로그 백업

```bash
# 날짜별 백업
cp hook-execution.log "hook-execution-$(date +%Y%m%d).log.bak"

# 압축 백업
gzip -c hook-execution.log > "hook-execution-$(date +%Y%m%d).log.gz"
```

### 로그 로테이션

```bash
# 파일 크기가 10MB 넘으면 백업
if [ $(stat -f%z hook-execution.log) -gt 10485760 ]; then
    mv hook-execution.log "hook-execution-$(date +%Y%m%d-%H%M%S).log"
fi
```

---

## 🐛 문제 해결

### 문제 1: 로그 파일이 생성되지 않음

**원인**:
- Hook이 실행되지 않음
- 디렉토리 권한 문제

**해결**:
```bash
# 디렉토리 생성 확인
mkdir -p .claude/hooks/logs

# 권한 확인
ls -la .claude/hooks/logs/

# Hook 실행 권한 확인
ls -la .claude/hooks/*.sh
chmod +x .claude/hooks/*.sh
```

### 문제 2: 로그가 기록되지 않음

**원인**:
- Hook이 트리거되지 않음 (Context Score < 25)
- stderr로 출력되는 오류 미확인

**해결**:
```bash
# Hook을 수동으로 테스트
echo "domain aggregate Order" | ./.claude/hooks/user-prompt-submit.sh

# stderr 확인
cat .claude/hooks/logs/hook-execution.log
```

### 문제 3: 로그가 너무 많음

**해결**:
```bash
# 최근 100줄만 유지
tail -100 hook-execution.log > hook-execution-temp.log
mv hook-execution-temp.log hook-execution.log

# 또는 삭제 후 재시작
./view-logs.sh -c
```

---

## 📚 추가 정보

### Hook 실행 흐름 이해

```
사용자 입력
    ↓
user-prompt-submit.sh
    ├─ 키워드 감지 (aggregate, domain, etc.)
    ├─ Context Score 계산 (30점/키워드)
    ├─ Layer 매핑 (domain, application, etc.)
    └─ inject-rules.py 호출 (Score >= 25)
    ↓
Claude Code (코드 생성)
    ↓
after-tool-use.sh
    ├─ 파일 경로에서 Layer 감지
    ├─ validation-helper.py 호출
    ├─ Lombok, Javadoc, Law of Demeter 검증
    └─ 결과 출력 (Passed/Failed)
```

### 로그 활용 사례

1. **개발 피드백**:
   - Hook이 제대로 트리거되는지 확인
   - Context Score 임계값 조정 근거

2. **통계 분석**:
   - 가장 많이 사용되는 Layer
   - 검증 실패 패턴 분석

3. **디버깅**:
   - 규칙 주입 실패 원인 파악
   - 검증 오류 재현

4. **성능 모니터링**:
   - Hook 실행 빈도
   - 로그 파일 크기 추이

---

**마지막 업데이트**: 2025-10-17
**관련 문서**: [DYNAMIC_HOOKS_GUIDE.md](../../docs/DYNAMIC_HOOKS_GUIDE.md)
