#!/bin/bash
# ========================================
# Application Module Validator
# ========================================
# Enforces application layer rules:
# - CAN depend on domain
# - CANNOT depend on adapters
# - NO Lombok
# - Service layer patterns
# ========================================

VIOLATION_FOUND=0

RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_error() {
    echo -e "${RED}❌ APPLICATION VIOLATION: $1${NC}"
    VIOLATION_FOUND=1
}

log_warning() {
    echo -e "${YELLOW}⚠️  APPLICATION WARNING: $1${NC}"
}

# ========================================
# Check Each File
# ========================================

for file in "$@"; do
    [[ $file != *.java ]] && continue

    # Check for adapter imports (FORBIDDEN)
    if grep -q "import com\.company\.template\.adapter\." "$file"; then
        log_error "$file imports from adapter layer (boundary violation)"
        grep -n "import com\.company\.template\.adapter\." "$file"
    fi

    # Check for Lombok
    if grep -q "import lombok\." "$file"; then
        log_error "$file contains Lombok import (STRICTLY PROHIBITED)"
        grep -n "import lombok\." "$file"
    fi

    # Check for JPA usage (should be in adapter)
    # Filter out comments to avoid false positives
    MATCHES=$(grep -n -E "@\(Entity|Table|Repository\)" "$file" | grep -v -E ':\s*(//|\*)')
    if [ -n "$MATCHES" ]; then
        log_error "$file contains JPA annotations (JPA belongs in adapter-out-persistence)"
        echo "$MATCHES"
    fi

    # Check use case naming convention
    if [[ $file == */usecase/* ]] && [[ ! $file =~ UseCase\.java$ ]]; then
        log_warning "$file in usecase package should end with 'UseCase'"
    fi

    # Check for proper @Transactional usage
    if grep -q "@Transactional" "$file"; then
        # Verify it's on a service class
        if ! grep -q "class.*Service" "$file"; then
            log_warning "$file uses @Transactional outside of service class"
        fi
    fi
done

# ========================================
# Result
# ========================================

if [ $VIOLATION_FOUND -eq 1 ]; then
    echo ""
    echo "APPLICATION LAYER POLICY:"
    echo "- Application can depend on domain only"
    echo "- NO adapter dependencies"
    echo "- NO Lombok"
    echo "- Use ports (interfaces) for adapter communication"
    exit 1
fi

exit 0
