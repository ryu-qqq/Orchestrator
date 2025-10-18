#!/bin/bash
# ========================================
# Controller Adapter Validator
# ========================================
# Enforces controller layer rules:
# - NO inner classes in controllers
# - Request/Response must be records
# - Record validation in constructors
# - UseCase dependencies only
# ========================================

VIOLATION_FOUND=0

# Colors
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_error() {
    echo -e "${RED}❌ CONTROLLER VIOLATION: $1${NC}"
    VIOLATION_FOUND=1
}

log_warning() {
    echo -e "${YELLOW}⚠️  CONTROLLER WARNING: $1${NC}"
}

# ========================================
# Check Each File
# ========================================

for file in "$@"; do
    # Skip non-Java files
    [[ $file != *.java ]] && continue

    # Rule 1: Check for inner classes in Controller files
    if [[ $file == *"Controller.java" ]]; then
        if grep -qE "class\s+\w+(Request|Response)" "$file"; then
            log_error "$file contains inner class Request/Response (FORBIDDEN)"
            log_error "   Request/Response DTOs must be in separate files"
            grep -n "class\s\+\w\+\(Request\|Response\)" "$file"
        fi
    fi

    # Rule 2: Check if Request/Response are records
    if [[ $file == *"Request.java" ]] || [[ $file == *"Response.java" ]]; then
        if ! grep -q "public record" "$file"; then
            log_error "$file is not a record (REQUIRED)"
            log_error "   All Request/Response DTOs must be Java records"
        fi
    fi

    # Rule 3: Check for record validation in Request files
    if [[ $file == *"Request.java" ]]; then
        if grep -q "public record" "$file"; then
            # Check if compact constructor exists for validation
            if ! grep -qE "public \w+Request\s*\{" "$file"; then
                log_warning "$file: Consider adding compact constructor validation"
            fi
        fi
    fi

    # Rule 4: Check for business logic in controllers
    if [[ $file == *"Controller.java" ]]; then
        if grep -qE "(calculate|validate|process|execute|perform)" "$file"; then
            log_warning "$file may contain business logic (should be in UseCase/Domain)"
        fi
    fi

    # Rule 5: Check for Repository/Entity dependencies
    if [[ $file == *"Controller.java" ]]; then
        if grep -q "Repository" "$file"; then
            log_error "$file depends on Repository (FORBIDDEN)"
            log_error "   Controllers should depend on UseCase interfaces only"
            grep -n "Repository" "$file"
        fi
        if grep -q "Entity" "$file"; then
            log_error "$file depends on Entity (FORBIDDEN)"
            log_error "   Controllers should work with DTOs only"
            grep -n "Entity" "$file"
        fi
    fi

    # Rule 6: Check for toCommand/from methods in DTOs
    if [[ $file == *"Request.java" ]]; then
        if ! grep -q "toCommand" "$file"; then
            log_warning "$file: Consider adding toCommand() method for conversion"
        fi
    fi

    if [[ $file == *"Response.java" ]]; then
        if ! grep -q "from" "$file"; then
            log_warning "$file: Consider adding static from() method for conversion"
        fi
    fi
done

# ========================================
# Result
# ========================================

if [ $VIOLATION_FOUND -eq 1 ]; then
    echo ""
    echo "CONTROLLER ADAPTER POLICY:"
    echo "- NO inner classes in controllers"
    echo "- Request/Response DTOs must be separate record files"
    echo "- Records should have compact constructor validation"
    echo "- Depend on UseCase interfaces ONLY (NO repositories, NO entities)"
    echo "- Keep controllers thin (orchestration only, NO business logic)"
    echo "- See: CODING_STANDARDS.md - Controller Adapter section"
    exit 1
fi

exit 0
