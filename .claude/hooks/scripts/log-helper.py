#!/usr/bin/env python3

"""
JSON 로그 헬퍼
JSONL (JSON Lines) 형식으로 로그 작성
"""

import json
import sys
from datetime import datetime
from pathlib import Path

# 로그 파일 경로
SCRIPT_DIR = Path(__file__).parent
LOG_FILE = SCRIPT_DIR.parent / "logs" / "hook-execution.jsonl"

def log_event(event_type: str, data: dict):
    """
    JSON Lines 형식으로 이벤트 로그 작성

    Args:
        event_type: 이벤트 타입 (session_start, keyword_analysis, cache_injection 등)
        data: 추가 데이터 (dict)
    """
    log_entry = {
        "timestamp": datetime.now().isoformat(),
        "event": event_type,
        **data
    }

    # 로그 디렉토리 생성
    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)

    # JSONL 형식으로 append
    with open(LOG_FILE, 'a', encoding='utf-8') as f:
        f.write(json.dumps(log_entry, ensure_ascii=False) + '\n')


def main():
    """
    CLI 사용법:
    echo '{"session_id":"123","project":"myproject"}' | python3 log-helper.py session_start
    """
    if len(sys.argv) < 2:
        print("Usage: log-helper.py <event_type> [json_data]")
        sys.exit(1)

    event_type = sys.argv[1]

    # stdin에서 JSON 데이터 읽기
    if not sys.stdin.isatty():
        data_str = sys.stdin.read().strip()
        if data_str:
            try:
                data = json.loads(data_str)
            except json.JSONDecodeError:
                data = {"raw": data_str}
        else:
            data = {}
    else:
        data = {}

    log_event(event_type, data)


if __name__ == "__main__":
    main()
