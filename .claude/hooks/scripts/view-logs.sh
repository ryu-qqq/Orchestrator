#!/bin/bash

# =====================================================
# Hook 로그 뷰어 (JSON 파싱)
# JSONL (JSON Lines) 형식 로그를 파싱하여 보기 좋게 표시
# =====================================================

LOG_FILE=".claude/hooks/logs/hook-execution.jsonl"

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# 도움말
show_help() {
    cat << EOF
🔍 Hook 로그 뷰어 (JSON 파싱)

사용법:
  $0 [옵션]

옵션:
  -f, --follow        실시간 모니터링 (tail -f)
  -n, --lines N       마지막 N개 이벤트 표시 (기본: 20)
  -c, --clear         로그 파일 삭제
  -s, --stats         통계 정보 표시
  -e, --event TYPE    특정 이벤트 타입만 필터링
  -p, --project NAME  특정 프로젝트만 필터링
  -r, --raw           원본 JSON 출력
  -h, --help          도움말 표시

이벤트 타입:
  session_start, keyword_analysis, decision, cache_injection,
  validation_start, validation_complete, validation_error

예시:
  $0                              # 마지막 20개 이벤트
  $0 -f                           # 실시간 모니터링
  $0 -n 50                        # 마지막 50개 이벤트
  $0 -s                           # 통계 정보
  $0 -e validation_complete       # 검증 완료 이벤트만
  $0 -p claude-spring-standards   # 특정 프로젝트만
  $0 -r                           # 원본 JSON

EOF
}

# 로그 파일 존재 확인
check_log_file() {
    if [[ ! -f "$LOG_FILE" ]]; then
        echo -e "${RED}❌ 로그 파일이 없습니다: $LOG_FILE${NC}"
        echo ""
        echo "Hook이 아직 실행되지 않았거나, 디렉토리가 생성되지 않았습니다."
        echo ""
        echo "다음 중 하나를 시도하세요:"
        echo "1. Claude Code에서 코드 생성 (예: /code-gen-domain Order)"
        echo "2. 'domain', 'controller' 등 키워드를 포함한 프롬프트 입력"
        echo ""
        exit 1
    fi
}

# jq 설치 확인
check_jq() {
    if ! command -v jq &> /dev/null; then
        echo -e "${RED}❌ jq가 설치되지 않았습니다${NC}"
        echo ""
        echo "jq를 설치해주세요:"
        echo "  macOS: brew install jq"
        echo "  Ubuntu: sudo apt-get install jq"
        echo ""
        exit 1
    fi
}

# 통계 정보 (JSON 기반)
show_stats() {
    check_log_file
    check_jq

    echo -e "${BLUE}📊 Hook 실행 통계 (JSON 분석)${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    # 총 이벤트 수
    TOTAL_EVENTS=$(wc -l < "$LOG_FILE")
    echo -e "${GREEN}총 이벤트:${NC} $TOTAL_EVENTS 개"
    echo ""

    # 이벤트 타입별 통계
    echo -e "${CYAN}이벤트 타입별 통계:${NC}"
    jq -r '.event' "$LOG_FILE" | sort | uniq -c | sort -rn | while read count event; do
        echo "  - $event: $count 회"
    done
    echo ""

    # 프로젝트별 통계
    echo -e "${CYAN}프로젝트별 통계:${NC}"
    jq -r 'select(.project != null) | .project' "$LOG_FILE" 2>/dev/null | sort | uniq -c | sort -rn | while read count project; do
        echo "  - $project: $count 회"
    done
    echo ""

    # Layer 감지 통계
    echo -e "${CYAN}Layer 감지 통계:${NC}"
    jq -r 'select(.detected_layers != null) | .detected_layers[]' "$LOG_FILE" 2>/dev/null | sort | uniq -c | sort -rn | while read count layer; do
        echo "  - $layer: $count 회"
    done
    echo ""

    # 검증 결과 통계
    VALIDATION_PASSED=$(jq -r 'select(.event == "validation_complete" and .status == "passed")' "$LOG_FILE" 2>/dev/null | wc -l)
    VALIDATION_FAILED=$(jq -r 'select(.event == "validation_complete" and .status == "failed")' "$LOG_FILE" 2>/dev/null | wc -l)

    echo -e "${CYAN}검증 결과:${NC}"
    echo "  - ✅ Passed: $VALIDATION_PASSED"
    echo "  - ❌ Failed: $VALIDATION_FAILED"
    echo ""

    # 평균 검증 시간
    AVG_TIME=$(jq -r 'select(.validation_time_ms != null) | .validation_time_ms' "$LOG_FILE" 2>/dev/null | awk '{sum+=$1; count++} END {if (count > 0) print int(sum/count); else print 0}')
    echo -e "${CYAN}평균 검증 시간:${NC} ${AVG_TIME}ms"
    echo ""

    # Context Score 분포
    echo -e "${CYAN}Context Score 분포:${NC}"
    jq -r 'select(.context_score != null) | .context_score' "$LOG_FILE" 2>/dev/null | sort -n | uniq -c | while read count score; do
        echo "  - Score $score: $count 회"
    done
    echo ""

    # 파일 정보
    FILE_SIZE=$(ls -lh "$LOG_FILE" | awk '{print $5}')
    LINE_COUNT=$(wc -l < "$LOG_FILE")

    echo -e "${CYAN}로그 파일 정보:${NC}"
    echo "  - 크기: $FILE_SIZE"
    echo "  - 이벤트 수: $LINE_COUNT"
    echo "  - 경로: $LOG_FILE"
    echo ""
}

# JSON 로그 파싱 및 출력
parse_json_log() {
    local raw_mode=$1

    if [[ "$raw_mode" == "true" ]]; then
        # 원본 JSON (pretty print)
        cat | jq '.'
    else
        # 보기 좋게 파싱
        cat | jq -r '
            # 타임스탬프 (HH:MM:SS)
            (.timestamp | sub("\\.[0-9]+"; "") | split("T")[1]) as $time |

            # 이벤트 타입별 포맷팅
            if .event == "session_start" then
                "[\($time)] 🚀 SESSION_START | project=\(.project) | hook=\(.hook) | command=\(.user_command // "N/A")"

            elif .event == "keyword_analysis" then
                "[\($time)] 🔍 KEYWORD_ANALYSIS | score=\(.context_score) | layers=\(.detected_layers | join(",")) | keywords=\(.detected_keywords | join(","))"

            elif .event == "decision" then
                "[\($time)] 🎯 DECISION | action=\(.action) | reason=\(.reason)"

            elif .event == "cache_injection" then
                "[\($time)] 💉 CACHE_INJECTION | layer=\(.layer) | rules=\(.rules_loaded)/\(.total_rules_available) | tokens=\(.estimated_tokens) | files=[\(.cache_files | join(", "))]"

            elif .event == "validation_start" then
                "[\($time)] ⚙️  VALIDATION_START | file=\(.file) | layer=\(.layer) | lines=\(.file_lines)"

            elif .event == "validation_complete" then
                if .status == "passed" then
                    "[\($time)] ✅ VALIDATION_PASSED | file=\(.file) | rules=\(.total_rules) | time=\(.validation_time_ms)ms"
                else
                    "[\($time)] ❌ VALIDATION_FAILED | file=\(.file) | failed=\(.failed)/\(.total_rules) | time=\(.validation_time_ms)ms"
                end

            elif .event == "validation_error" then
                "[\($time)] 🚨 VALIDATION_ERROR | file=\(.file) | error=\(.error)"

            elif .event == "cache_index_loaded" then
                "[\($time)] 📚 CACHE_INDEX_LOADED | file=\(.index_file) | rules=\(.total_rules)"

            else
                "[\($time)] \(.event | ascii_upcase) | \(. | to_entries | map("\(.key)=\(.value)") | join(" | "))"
            end
        '
    fi
}

# 기본 옵션
LINES=20
FOLLOW=false
STATS=false
CLEAR=false
RAW=false
EVENT_FILTER=""
PROJECT_FILTER=""

# 인자 파싱
while [[ $# -gt 0 ]]; do
    case $1 in
        -f|--follow)
            FOLLOW=true
            shift
            ;;
        -n|--lines)
            LINES="$2"
            shift 2
            ;;
        -s|--stats)
            STATS=true
            shift
            ;;
        -c|--clear)
            CLEAR=true
            shift
            ;;
        -e|--event)
            EVENT_FILTER="$2"
            shift 2
            ;;
        -p|--project)
            PROJECT_FILTER="$2"
            shift 2
            ;;
        -r|--raw)
            RAW=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "알 수 없는 옵션: $1"
            show_help
            exit 1
            ;;
    esac
done

# 로그 삭제
if [[ "$CLEAR" == true ]]; then
    if [[ -f "$LOG_FILE" ]]; then
        rm -f "$LOG_FILE"
        echo -e "${GREEN}✅ 로그 파일 삭제 완료${NC}"
    else
        echo -e "${YELLOW}⚠️  로그 파일이 없습니다${NC}"
    fi
    exit 0
fi

# 통계 표시
if [[ "$STATS" == true ]]; then
    show_stats
    exit 0
fi

# 로그 확인
check_log_file
check_jq

# 헤더 출력
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔍 Hook 실행 로그 (JSON 파싱)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [[ -n "$EVENT_FILTER" ]]; then
    echo -e "${YELLOW}필터: 이벤트 타입 = $EVENT_FILTER${NC}"
fi

if [[ -n "$PROJECT_FILTER" ]]; then
    echo -e "${YELLOW}필터: 프로젝트 = $PROJECT_FILTER${NC}"
fi

echo ""

# 필터 적용
FILTER_CMD="cat $LOG_FILE"

if [[ -n "$EVENT_FILTER" ]]; then
    FILTER_CMD="$FILTER_CMD | jq -c 'select(.event == \"$EVENT_FILTER\")'"
fi

if [[ -n "$PROJECT_FILTER" ]]; then
    FILTER_CMD="$FILTER_CMD | jq -c 'select(.project == \"$PROJECT_FILTER\")'"
fi

# 실시간 모니터링
if [[ "$FOLLOW" == true ]]; then
    echo -e "${CYAN}실시간 모니터링 중... (Ctrl+C로 종료)${NC}"
    echo ""

    if [[ -n "$EVENT_FILTER" || -n "$PROJECT_FILTER" ]]; then
        tail -f "$LOG_FILE" | while read line; do
            echo "$line" | jq -c "select(.event == \"$EVENT_FILTER\" or .project == \"$PROJECT_FILTER\")" 2>/dev/null | parse_json_log "$RAW"
        done
    else
        tail -f "$LOG_FILE" | parse_json_log "$RAW"
    fi
else
    # 마지막 N개 이벤트 표시
    if [[ -n "$EVENT_FILTER" || -n "$PROJECT_FILTER" ]]; then
        eval "$FILTER_CMD" | tail -n "$LINES" | parse_json_log "$RAW"
    else
        tail -n "$LINES" "$LOG_FILE" | parse_json_log "$RAW"
    fi

    echo ""
    echo -e "${YELLOW}💡 Tip: 실시간 모니터링은 '$0 -f'${NC}"
    echo -e "${YELLOW}💡 Tip: 통계 정보는 '$0 -s'${NC}"
    echo -e "${YELLOW}💡 Tip: 원본 JSON은 '$0 -r'${NC}"
fi
