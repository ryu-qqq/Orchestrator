#!/bin/bash
# ========================================
# Adapter-In Validator
# ========================================
# Validates inbound adapters (e.g., REST controllers)
# NO Lombok allowed
# ========================================

VIOLATION_FOUND=0

RED='\033[0;31m'
NC='\033[0m'

log_error() {
    echo -e "${RED}❌ ADAPTER-IN VIOLATION: $1${NC}"
    VIOLATION_FOUND=1
}

for file in "$@"; do
    [[ $file != *.java ]] && continue

    # Check for Lombok
    if grep -q "import lombok\." "$file"; then
        log_error "$file contains Lombok import (STRICTLY PROHIBITED)"
    fi

    # Check for domain logic in controller
    if [[ $file == *Controller.java ]]; then
        # Controllers should delegate to application layer
        if grep -q "new.*Service\|new.*UseCase" "$file"; then
            log_error "$file directly instantiates services (use dependency injection)"
        fi
    fi
done

[ $VIOLATION_FOUND -eq 1 ] && exit 1
exit 0
