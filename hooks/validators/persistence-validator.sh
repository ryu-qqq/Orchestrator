#!/bin/bash
# ========================================
# Persistence Adapter Validator
# ========================================
# Enforces persistence layer rules:
# - NO JPA relationship annotations
# - NO setter methods in entities
# - NO public constructors in entities
# - NO @Transactional in adapters
# ========================================

VIOLATION_FOUND=0

# Colors
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_error() {
    echo -e "${RED}❌ PERSISTENCE VIOLATION: $1${NC}"
    VIOLATION_FOUND=1
}

log_warning() {
    echo -e "${YELLOW}⚠️  PERSISTENCE WARNING: $1${NC}"
}

# ========================================
# Check Each File
# ========================================

for file in "$@"; do
    # Skip non-Java files
    [[ $file != *.java ]] && continue

    # Rule 1: Check for forbidden JPA relationship annotations
    # Filter out comments to avoid false positives
    MATCHES=$(grep -n -E "@(OneToMany|ManyToOne|OneToOne|ManyToMany)" "$file" | grep -v ':\s*//' | grep -v ':\s*\*')
    if [ -n "$MATCHES" ]; then
        log_error "$file contains JPA relationship annotations (FORBIDDEN)"
        log_error "   Use Long foreign key fields instead (userId, orderId, etc.)"
        echo "$MATCHES"
    fi

    # Rule 2: Check for setter methods in Entity classes
    if [[ $file == *"Entity.java" ]]; then
        if grep -qE "public void set[A-Z]" "$file"; then
            log_error "$file contains setter methods (FORBIDDEN in entities)"
            log_error "   Entities must be immutable after creation"
            grep -n "public void set[A-Z]" "$file"
        fi
    fi

    # Rule 3: Check for public constructors in Entity classes
    if [[ $file == *"Entity.java" ]]; then
        if grep -qE "^\s*public\s+\w+Entity\s*\(" "$file"; then
            log_error "$file contains public constructor (FORBIDDEN)"
            log_error "   Use protected constructor for JPA and static factory methods"
            grep -n "^\s*public\s+\w+Entity\s*\(" "$file"
        fi
    fi

    # Rule 4: Check for @Transactional in adapters
    # Filter out comments to avoid false positives
    MATCHES=$(grep -n "@Transactional" "$file" | grep -v ':\s*//' | grep -v ':\s*\*')
    if [ -n "$MATCHES" ]; then
        log_error "$file contains @Transactional (FORBIDDEN in adapters)"
        log_error "   Transaction management belongs in Application layer"
        echo "$MATCHES"
    fi

    # Rule 5: Check for business logic keywords (warning)
    if [[ $file == *"Entity.java" ]]; then
        if grep -qE "(calculate|validate|process|execute|perform)" "$file"; then
            log_warning "$file may contain business logic (should be in Domain layer)"
        fi
    fi

    # Rule 6: Check for missing Mapper classes
    if [[ $file == *"PersistenceAdapter.java" ]]; then
        if ! grep -q "Mapper" "$file"; then
            log_warning "$file should use Mapper for Entity ↔ Domain conversion"
        fi
    fi
done

# ========================================
# Result
# ========================================

if [ $VIOLATION_FOUND -eq 1 ]; then
    echo ""
    echo "PERSISTENCE ADAPTER POLICY:"
    echo "- NO JPA relationships (@OneToMany, @ManyToOne, etc.)"
    echo "- Use Long foreign key fields only (userId, orderId)"
    echo "- NO setter methods in entities (immutable after creation)"
    echo "- NO public constructors (use static factory methods)"
    echo "- NO @Transactional (belongs in Application layer)"
    echo "- See: CODING_STANDARDS.md - Persistence Adapter section"
    exit 1
fi

exit 0
