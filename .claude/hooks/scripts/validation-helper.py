#!/usr/bin/env python3

"""
=================================================================
Validation Helper (Cache-based)
Purpose: Validate code against cached rules
Usage: python3 validation-helper.py <file_path> <layer>
Output: Validation results (pass/fail)
=================================================================
"""

import json
import sys
import re
from pathlib import Path
from typing import Dict, List, Any, Optional
from datetime import datetime
import time

# 경로 설정
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent.parent  # 3단계 위로 (scripts → hooks → .claude → project root)
CACHE_DIR = PROJECT_ROOT / ".claude" / "cache" / "rules"
INDEX_FILE = CACHE_DIR / "index.json"
LOG_FILE = PROJECT_ROOT / ".claude" / "hooks" / "logs" / "hook-execution.jsonl"


class ValidationResult:
    """검증 결과"""

    def __init__(self, rule_id: str, passed: bool, message: str = ""):
        self.rule_id = rule_id
        self.passed = passed
        self.message = message


def log_event(event_type: str, data: dict):
    """JSON 로그 기록"""
    log_entry = {
        "timestamp": datetime.now().isoformat(),
        "event": event_type,
        **data
    }

    with open(LOG_FILE, 'a', encoding='utf-8') as f:
        f.write(json.dumps(log_entry, ensure_ascii=False) + '\n')


class Validator:
    """캐시 기반 코드 검증기"""

    def __init__(self):
        self.index = self.load_index()
        self.results: List[ValidationResult] = []
        self.validation_start_time = None
        self.checked_rules = []

    def load_index(self) -> Dict[str, Any]:
        """Index 파일 로드"""
        with open(INDEX_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)

    def load_rule(self, rule_id: str) -> Optional[Dict[str, Any]]:
        """특정 규칙 JSON 로드"""
        rule_file = CACHE_DIR / f"{rule_id}.json"

        if not rule_file.exists():
            return None

        with open(rule_file, 'r', encoding='utf-8') as f:
            return json.load(f)

    def remove_comments_and_strings(self, content: str, file_path: str) -> str:
        """주석과 문자열 리터럴 제거 (false positive 방지)"""

        # Java/Kotlin 파일
        if file_path.endswith(('.java', '.kt')):
            # 1. 블록 주석 제거 (/* ... */)
            content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
            # 2. 라인 주석 제거 (// ...)
            content = re.sub(r'//.*?$', '', content, flags=re.MULTILINE)
            # 3. 문자열 리터럴 제거 ("...", '...')
            content = re.sub(r'"(?:\\.|[^"\\])*"', '', content)
            content = re.sub(r"'(?:\\.|[^'\\])*'", '', content)

        # Python 파일
        elif file_path.endswith('.py'):
            # 1. 블록 주석 제거 ("""...""", '''...''')
            content = re.sub(r'""".*?"""', '', content, flags=re.DOTALL)
            content = re.sub(r"'''.*?'''", '', content, flags=re.DOTALL)
            # 2. 라인 주석 제거 (# ...)
            content = re.sub(r'#.*?$', '', content, flags=re.MULTILINE)
            # 3. 문자열 리터럴 제거
            content = re.sub(r'"(?:\\.|[^"\\])*"', '', content)
            content = re.sub(r"'(?:\\.|[^'\\])*'", '', content)

        return content

    def validate_file(self, file_path: str, layer: str) -> List[ValidationResult]:
        """파일 검증 실행"""
        self.validation_start_time = time.time()

        if not Path(file_path).exists():
            log_event("validation_error", {
                "file": file_path,
                "layer": layer,
                "error": "file_not_found"
            })
            return [ValidationResult(
                "file-existence",
                False,
                f"File not found: {file_path}"
            )]

        # 파일 내용 읽기
        with open(file_path, 'r', encoding='utf-8') as f:
            raw_content = f.read()

        file_lines = len(raw_content.splitlines())

        log_event("validation_start", {
            "file": file_path,
            "layer": layer,
            "file_lines": file_lines
        })

        # 주석과 문자열 제거 (false positive 방지)
        content = self.remove_comments_and_strings(raw_content, file_path)

        # 레이어별 규칙 가져오기
        rule_ids = self.index.get("layerIndex", {}).get(layer, [])

        if not rule_ids:
            log_event("validation_warning", {
                "file": file_path,
                "layer": layer,
                "warning": "no_rules_found"
            })
            return self.results

        # Critical 규칙만 검증 (성능 최적화)
        critical_count = 0
        for rule_id in rule_ids:
            rule = self.load_rule(rule_id)

            if rule and rule["metadata"]["priority"] == "critical":
                critical_count += 1
                self.checked_rules.append(rule_id)
                self.validate_rule(content, file_path, rule)

        return self.results

    def validate_rule(self, content: str, file_path: str, rule: Dict[str, Any]):
        """개별 규칙 검증"""

        rule_id = rule["id"]
        metadata = rule["metadata"]

        # Anti-pattern 검증
        anti_keywords = metadata.get("keywords", {}).get("anti", [])

        for anti_pattern in anti_keywords:
            # 패턴을 정규식으로 변환
            # 예: "order.getCustomer().getAddress()" → r"order\.getCustomer\(\)\.getAddress\(\)"
            escaped_pattern = re.escape(anti_pattern)

            # 하지만 너무 엄격하면 안 되므로, 공백/줄바꿈 허용
            flexible_pattern = escaped_pattern.replace(r"\ ", r"\s*")

            if re.search(flexible_pattern, content):
                self.results.append(ValidationResult(
                    rule_id,
                    False,
                    f"Anti-pattern detected: {anti_pattern}"
                ))
                return

        # Prohibited 검증
        prohibited = rule.get("rules", {}).get("prohibited", [])

        for item in prohibited:
            # Markdown 제거 (❌, **, ` 등)
            clean_pattern = re.sub(r'[❌✅`*]', '', item).strip()

            # Annotation 검증 (예: @Data, @Builder)
            if clean_pattern.startswith('@'):
                annotation = clean_pattern.split()[0]  # @Data 추출
                escaped = re.escape(annotation)

                if re.search(escaped, content):
                    self.results.append(ValidationResult(
                        rule_id,
                        False,
                        f"Prohibited annotation: {annotation}"
                    ))
                    return

            # 일반 텍스트 패턴
            elif len(clean_pattern) > 5:  # 너무 짧은 패턴 제외
                escaped = re.escape(clean_pattern)
                flexible = escaped.replace(r"\ ", r"\s*")

                if re.search(flexible, content, re.IGNORECASE):
                    self.results.append(ValidationResult(
                        rule_id,
                        False,
                        f"Prohibited pattern: {clean_pattern}"
                    ))
                    return

        # 통과
        self.results.append(ValidationResult(
            rule_id,
            True,
            ""
        ))

    def print_results(self, file_path: str):
        """검증 결과 출력"""

        # 검증 시간 계산
        validation_time = int((time.time() - self.validation_start_time) * 1000) if self.validation_start_time else 0

        failed_results = [r for r in self.results if not r.passed]
        passed_results = [r for r in self.results if r.passed]

        # JSON 로그: 검증 완료
        log_event("validation_complete", {
            "file": file_path,
            "total_rules": len(self.results),
            "passed": len(passed_results),
            "failed": len(failed_results),
            "validation_time_ms": validation_time,
            "status": "failed" if failed_results else "passed",
            "failed_rules": [{"rule_id": r.rule_id, "message": r.message} for r in failed_results]
        })

        if failed_results:
            print("\n---\n")
            print("⚠️ **Validation Failed**\n")
            print(f"**파일**: `{file_path}`\n")

            for result in failed_results:
                rule = self.load_rule(result.rule_id)

                if rule:
                    print(f"**규칙 위반**: {rule['documentation']['summary']}")
                    print(f"**문제**: {result.message}\n")

                    # 금지된 항목 출력
                    prohibited = rule.get("rules", {}).get("prohibited", [])
                    if prohibited:
                        print("**금지 사항**:")
                        for item in prohibited[:3]:  # 상위 3개만
                            print(f"- {item}")
                        print()

                    # 문서 링크
                    doc_path = rule["documentation"]["path"]
                    print(f"**참고**: `{doc_path}`\n")

            print("💡 코드를 수정한 후 다시 시도하세요.\n")
            print("---\n")
        else:
            print("\n---\n")
            print("✅ **Validation Passed**\n")
            print(f"파일: `{file_path}`\n")
            print("모든 규칙을 준수합니다!\n")
            print("---\n")


def main():
    if len(sys.argv) < 3:
        print("Usage: validation-helper.py <file_path> <layer>")
        print("  file_path: 검증할 파일 경로")
        print("  layer: domain, application, adapter-rest, etc.")
        sys.exit(1)

    file_path = sys.argv[1]
    layer = sys.argv[2]

    # 검증 실행
    validator = Validator()
    validator.validate_file(file_path, layer)
    validator.print_results(file_path)


if __name__ == "__main__":
    main()
