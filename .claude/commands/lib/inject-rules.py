#!/usr/bin/env python3

"""
=================================================================
Rule Injector
Purpose: Inject layer-specific rules into Claude context
Usage: python3 inject-rules.py [domain|application|adapter-rest|...]
Output: Markdown-formatted rules for Claude
=================================================================
"""

import json
import sys
from pathlib import Path
from datetime import datetime

# 경로 설정
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent.parent  # 3단계 위로
CACHE_DIR = PROJECT_ROOT / ".claude" / "cache" / "rules"
INDEX_FILE = CACHE_DIR / "index.json"
LOG_FILE = PROJECT_ROOT / ".claude" / "hooks" / "logs" / "hook-execution.jsonl"


def log_event(event_type: str, data: dict):
    """JSON 로그 기록"""
    log_entry = {
        "timestamp": datetime.now().isoformat(),
        "event": event_type,
        **data
    }

    # JSONL 형식으로 append
    with open(LOG_FILE, 'a', encoding='utf-8') as f:
        f.write(json.dumps(log_entry, ensure_ascii=False) + '\n')


def load_index():
    """Index 파일 로드"""
    with open(INDEX_FILE, 'r', encoding='utf-8') as f:
        index = json.load(f)
        log_event("cache_index_loaded", {
            "index_file": str(INDEX_FILE),
            "total_rules": len(index.get('rules', []))
        })
        return index


def load_rule(rule_id):
    """특정 규칙 JSON 로드"""
    rule_file = CACHE_DIR / f"{rule_id}.json"

    if not rule_file.exists():
        return None

    with open(rule_file, 'r', encoding='utf-8') as f:
        return json.load(f)


def inject_layer_rules(layer: str, priority_filter: str = None):
    """
    레이어별 규칙 주입

    Args:
        layer: domain, application, adapter-rest 등
        priority_filter: critical, high, medium, low (optional)
    """
    index = load_index()

    # 레이어에 해당하는 rule_ids 추출
    rule_ids = index.get("layerIndex", {}).get(layer, [])

    if not rule_ids:
        log_event("cache_injection_error", {
            "layer": layer,
            "error": "no_rules_found"
        })
        print(f"⚠️  No rules found for layer: {layer}")
        return

    # 규칙 로드
    rules = []
    loaded_files = []
    for rule_id in rule_ids:
        rule = load_rule(rule_id)
        if rule:
            # Priority 필터링
            if priority_filter:
                if rule["metadata"]["priority"] != priority_filter:
                    continue

            rules.append(rule)
            loaded_files.append(f"{rule_id}.json")

    # 규칙이 없으면 종료
    if not rules:
        log_event("cache_injection_error", {
            "layer": layer,
            "priority_filter": priority_filter,
            "error": "no_matching_rules"
        })
        print(f"⚠️  No rules match priority filter: {priority_filter}")
        return

    # 토큰 예상량 계산
    estimated_tokens = sum(len(json.dumps(r)) for r in rules) // 4

    # 캐시 주입 로그
    log_event("cache_injection", {
        "layer": layer,
        "priority_filter": priority_filter or "all",
        "total_rules_available": len(rule_ids),
        "rules_loaded": len(rules),
        "cache_files": loaded_files,
        "estimated_tokens": estimated_tokens
    })

    # Markdown 출력
    print("---")
    print()
    print(f"## 🎯 {layer.upper()} 레이어 규칙 (자동 주입됨)")
    print()

    # Critical 규칙만 먼저 출력
    critical_rules = [r for r in rules if r["metadata"]["priority"] == "critical"]

    if critical_rules:
        print("### ❌ 금지 규칙 (Zero-Tolerance)")
        print()

        for rule in critical_rules:
            prohibited = rule["rules"].get("prohibited", [])
            if prohibited:
                for item in prohibited[:3]:  # 최대 3개만
                    print(f"- {item}")

        print()

    # 필수 규칙
    print("### ✅ 필수 규칙")
    print()

    for rule in rules:
        allowed = rule["rules"].get("allowed", [])
        if allowed:
            for item in allowed[:3]:  # 최대 3개만
                print(f"- {item}")

    print()

    # 참고 문서 (링크만)
    print("### 📋 상세 문서")
    print()

    for rule in rules[:5]:  # 상위 5개 규칙만
        doc_path = rule["documentation"]["path"]
        summary = rule["documentation"]["summary"]
        print(f"- [{summary}]({doc_path})")

    print()
    print("**이 규칙들은 실시간으로 검증됩니다.**")
    print()
    print("---")
    print()


def main():
    if len(sys.argv) < 2:
        print("Usage: inject-rules.py [layer] [priority_filter]")
        print("  layer: domain, application, adapter-rest, adapter-persistence, etc.")
        print("  priority_filter: critical, high, medium, low (optional)")
        sys.exit(1)

    layer = sys.argv[1]
    priority_filter = sys.argv[2] if len(sys.argv) > 2 else None

    inject_layer_rules(layer, priority_filter)


if __name__ == "__main__":
    main()
