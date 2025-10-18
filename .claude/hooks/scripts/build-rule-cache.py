#!/usr/bin/env python3

"""
=================================================================
Rule Cache Builder (Python)
Purpose: Convert docs/coding_convention/**/*.md to JSON cache
Usage: python3 build-rule-cache.py
Output: .claude/cache/rules/*.json + index.json
=================================================================
"""

import json
import os
import re
from datetime import datetime
from pathlib import Path
from collections import defaultdict

# 색상 정의
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
NC = '\033[0m'  # No Color

# 경로 설정
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent.parent
DOCS_DIR = PROJECT_ROOT / "docs" / "coding_convention"
CACHE_DIR = PROJECT_ROOT / ".claude" / "cache" / "rules"
INDEX_FILE = CACHE_DIR / "index.json"


def extract_layer(filepath: Path) -> str:
    """파일 경로에서 레이어 추출"""
    path_str = str(filepath)

    layer_mapping = {
        "02-domain-layer": "domain",
        "03-application-layer": "application",
        "01-adapter-rest-api-layer": "adapter-rest",
        "04-persistence-layer": "adapter-persistence",
        "05-testing": "testing",
        "06-java21-patterns": "java21",
        "07-enterprise-patterns": "enterprise",
        "08-error-handling": "error-handling"
    }

    for key, value in layer_mapping.items():
        if key in path_str:
            return value

    return "general"


def extract_keywords_from_filename(filepath: Path) -> list[str]:
    """파일명에서 키워드 추출"""
    basename = filepath.stem  # .md 제거

    # 숫자 prefix 제거 (01_, 02_ 등)
    basename = re.sub(r'^[0-9]+_', '', basename)

    # 하이픈을 공백으로 변환하여 단어 분리
    words = basename.replace('-', ' ').split()

    return [w.lower() for w in words if w]


def extract_keywords_from_title(filepath: Path) -> list[str]:
    """제목(# Heading)에서 키워드 추출"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                if line.startswith('# '):
                    title = line[2:].strip().lower()

                    # 괄호 내용 제거, 특수문자 정리
                    title = re.sub(r'\([^)]*\)', '', title)
                    title = re.sub(r'[—–-]', ' ', title)
                    title = re.sub(r'[^\w\s]', '', title)

                    return [w for w in title.split() if w]

    except Exception as e:
        print(f"  {YELLOW}⚠  Warning:{NC} Failed to extract title from {filepath}: {e}")

    return []


def extract_prohibited_patterns(filepath: Path) -> list[str]:
    """금지 패턴 추출 (❌, 금지 등) - 모든 패턴 추출"""
    patterns = []

    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                # ❌로 시작하는 라인
                if re.match(r'^\s*-\s*❌', line):
                    pattern = re.sub(r'^\s*-\s*', '', line).strip()
                    patterns.append(pattern)

    except Exception as e:
        print(f"  {YELLOW}⚠  Warning:{NC} Failed to extract prohibited patterns: {e}")

    return patterns


def extract_allowed_patterns(filepath: Path) -> list[str]:
    """허용 패턴 추출 (✅) - 모든 패턴 추출"""
    patterns = []

    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                # ✅로 시작하는 라인
                if re.match(r'^\s*-\s*✅', line):
                    pattern = re.sub(r'^\s*-\s*', '', line).strip()
                    patterns.append(pattern)

    except Exception as e:
        print(f"  {YELLOW}⚠  Warning:{NC} Failed to extract allowed patterns: {e}")

    return patterns


def extract_anti_patterns(prohibited: list[str]) -> list[str]:
    """금지 패턴에서 코드 블록 추출 (`code` 형태)"""
    anti_patterns = []

    for pattern in prohibited:
        # `...` 형태 추출
        matches = re.findall(r'`([^`]+)`', pattern)
        anti_patterns.extend(matches)

    return anti_patterns


def determine_priority(filepath: Path) -> str:
    """우선순위 결정 (critical, high, medium, low)"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read().lower()

        # Critical: Zero-tolerance, 금지, 절대
        if re.search(r'(zero-tolerance|절대|금지|critical)', content):
            return "critical"

        # High: 필수, 중요, important
        if re.search(r'(필수|중요|important|must)', content):
            return "high"

        # Medium: 권장, recommended
        if re.search(r'(권장|recommended|should)', content):
            return "medium"

    except Exception as e:
        print(f"  {YELLOW}⚠  Warning:{NC} Failed to determine priority: {e}")

    return "low"


def estimate_tokens(filepath: Path) -> int:
    """토큰 수 추정 (단어 수 * 1.3)"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            word_count = len(content.split())
            return int(word_count * 1.3)
    except Exception:
        return 0


def generate_json(filepath: Path, rule_id: str, layer: str, priority: str) -> dict:
    """JSON 데이터 생성"""
    # 메타데이터 추출
    filename_keywords = extract_keywords_from_filename(filepath)
    title_keywords = extract_keywords_from_title(filepath)
    prohibited = extract_prohibited_patterns(filepath)
    allowed = extract_allowed_patterns(filepath)
    anti_patterns = extract_anti_patterns(prohibited)
    token_estimate = estimate_tokens(filepath)

    # 상대 경로
    relative_path = str(filepath.relative_to(PROJECT_ROOT))

    return {
        "id": rule_id,
        "sourceFile": str(filepath),
        "metadata": {
            "keywords": {
                "primary": filename_keywords,
                "secondary": title_keywords,
                "anti": anti_patterns
            },
            "layer": layer,
            "priority": priority,
            "tokenEstimate": token_estimate
        },
        "rules": {
            "prohibited": prohibited,
            "allowed": allowed
        },
        "documentation": {
            "path": relative_path,
            "summary": f"Auto-generated from {filepath.name}"
        }
    }


def main():
    print(f"{BLUE}=================================================={NC}")
    print(f"{BLUE}Rule Cache Builder{NC}")
    print(f"{BLUE}=================================================={NC}")
    print()

    # Cache 디렉토리 생성
    CACHE_DIR.mkdir(parents=True, exist_ok=True)

    # 카운터
    total_files = 0
    success_count = 0
    skip_count = 0

    # Index 데이터 구조
    keyword_index = defaultdict(list)
    layer_index = defaultdict(list)

    print(f"{YELLOW}Processing markdown files...{NC}")
    print()

    # docs/coding_convention/**/*.md 파일 순회
    md_files = sorted(DOCS_DIR.rglob("*.md"))

    for filepath in md_files:
        total_files += 1

        # rule_id 생성
        relative_path = filepath.relative_to(DOCS_DIR)
        rule_id = str(relative_path.with_suffix('')).replace('/', '-').replace(os.sep, '-')

        # 숫자 prefix 제거
        rule_id = re.sub(r'-[0-9]+-', '-', rule_id)
        rule_id = re.sub(r'^[0-9]+-', '', rule_id)

        # README, OVERVIEW 등 스킵
        if re.search(r'(readme|overview|roadmap)', filepath.name, re.IGNORECASE):
            print(f"  {YELLOW}⏭  Skipped:{NC} {relative_path} (non-rule document)")
            skip_count += 1
            continue

        # 레이어, 우선순위 추출
        layer = extract_layer(filepath)
        priority = determine_priority(filepath)

        # JSON 생성
        json_data = generate_json(filepath, rule_id, layer, priority)

        # JSON 파일 저장
        output_file = CACHE_DIR / f"{rule_id}.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(json_data, f, indent=2, ensure_ascii=False)

        print(f"  {GREEN}✅ Generated:{NC} {rule_id}.json (layer: {layer}, priority: {priority})")
        success_count += 1

        # Index 업데이트
        layer_index[layer].append(rule_id)

        for keyword in json_data["metadata"]["keywords"]["primary"]:
            keyword_index[keyword].append(rule_id)

    # Index 파일 생성
    print()
    print(f"{YELLOW}Building index...{NC}")

    index_data = {
        "version": "1.0.0",
        "buildDate": datetime.utcnow().isoformat() + "Z",
        "totalRules": success_count,
        "keywordIndex": dict(keyword_index),
        "layerIndex": {
            "domain": layer_index.get("domain", []),
            "application": layer_index.get("application", []),
            "adapter-rest": layer_index.get("adapter-rest", []),
            "adapter-persistence": layer_index.get("adapter-persistence", []),
            "testing": layer_index.get("testing", []),
            "java21": layer_index.get("java21", []),
            "enterprise": layer_index.get("enterprise", []),
            "error-handling": layer_index.get("error-handling", [])
        }
    }

    with open(INDEX_FILE, 'w', encoding='utf-8') as f:
        json.dump(index_data, f, indent=2, ensure_ascii=False)

    # 요약 출력
    print()
    print(f"{BLUE}=================================================={NC}")
    print(f"{GREEN}✅ Rule Cache Build Complete!{NC}")
    print(f"{BLUE}=================================================={NC}")
    print()
    print(f"  Total Files: {YELLOW}{total_files}{NC}")
    print(f"  Generated:   {GREEN}{success_count}{NC}")
    print(f"  Skipped:     {YELLOW}{skip_count}{NC}")
    print()
    print(f"  Output: {BLUE}{CACHE_DIR}/{NC}")
    print(f"  Index:  {BLUE}{INDEX_FILE}{NC}")
    print()
    print(f"{GREEN}You can now use these rules in hooks and slash commands!{NC}")
    print()


if __name__ == "__main__":
    main()
