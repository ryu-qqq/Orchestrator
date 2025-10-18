#!/bin/bash
# ========================================
# Adapter-Out Validator
# ========================================
# Validates outbound adapters (persistence, external APIs)
# NO Lombok allowed
# ========================================

VIOLATION_FOUND=0

RED='\033[0;31m'
NC='\033[0m'

log_error() {
    echo -e "${RED}❌ ADAPTER-OUT VIOLATION: $1${NC}"
    VIOLATION_FOUND=1
}

for file in "$@"; do
    [[ $file != *.java ]] && continue

    # Check for Lombok
    if grep -q "import lombok\." "$file"; then
        log_error "$file contains Lombok import (STRICTLY PROHIBITED)"
    fi
done

[ $VIOLATION_FOUND -eq 1 ] && exit 1
exit 0
