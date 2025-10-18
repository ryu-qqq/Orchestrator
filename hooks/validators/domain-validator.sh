#!/bin/bash
# ========================================
# Domain Module Validator
# ========================================
# Enforces domain purity:
# - NO Spring Framework
# - NO JPA/Hibernate
# - NO Lombok
# - NO Infrastructure concerns
# ========================================

VIOLATION_FOUND=0

# Colors
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_error() {
    echo -e "${RED}❌ DOMAIN VIOLATION: $1${NC}"
    VIOLATION_FOUND=1
}

log_warning() {
    echo -e "${YELLOW}⚠️  DOMAIN WARNING: $1${NC}"
}

# ========================================
# Check Each File
# ========================================

for file in "$@"; do
    # Skip non-Java files
    [[ $file != *.java ]] && continue

    # Check for Spring imports
    if grep -q "import org\.springframework\." "$file"; then
        log_error "$file contains Spring Framework import"
        grep -n "import org\.springframework\." "$file"
    fi

    # Check for JPA imports
    if grep -q "import jakarta\.persistence\." "$file"; then
        log_error "$file contains JPA import"
        grep -n "import jakarta\.persistence\." "$file"
    fi

    # Check for Hibernate imports
    if grep -q "import org\.hibernate\." "$file"; then
        log_error "$file contains Hibernate import"
        grep -n "import org\.hibernate\." "$file"
    fi

    # Check for Lombok imports
    if grep -q "import lombok\." "$file"; then
        log_error "$file contains Lombok import (STRICTLY PROHIBITED)"
        grep -n "import lombok\." "$file"
    fi

    # Check for Spring annotations
    if grep -q "@\(Component\|Service\|Repository\|Controller\|RestController\|Autowired\)" "$file"; then
        log_error "$file contains Spring annotations"
        grep -n "@\(Component\|Service\|Repository\|Controller\|RestController\|Autowired\)" "$file"
    fi

    # Check for JPA annotations
    if grep -q "@\(Entity\|Table\|Id\|Column\|ManyToOne\|OneToMany\)" "$file"; then
        log_error "$file contains JPA annotations"
        grep -n "@\(Entity\|Table\|Id\|Column\|ManyToOne\|OneToMany\)" "$file"
    fi

    # Check for Lombok annotations
    if grep -q "@\(Data\|Builder\|Getter\|Setter\|AllArgsConstructor\|NoArgsConstructor\)" "$file"; then
        log_error "$file contains Lombok annotations (STRICTLY PROHIBITED)"
        grep -n "@\(Data\|Builder\|Getter\|Setter\|AllArgsConstructor\|NoArgsConstructor\)" "$file"
    fi

    # Check for Jackson annotations
    if grep -q "@\(JsonProperty\|JsonIgnore\|JsonFormat\)" "$file"; then
        log_warning "$file contains Jackson annotations (domain should be serialization-agnostic)"
    fi
done

# ========================================
# Result
# ========================================

if [ $VIOLATION_FOUND -eq 1 ]; then
    echo ""
    echo "DOMAIN PURITY POLICY:"
    echo "- Domain must remain pure Java"
    echo "- NO Spring, NO JPA, NO Lombok, NO infrastructure"
    echo "- See: docs/architecture/hexagonal-architecture.md"
    exit 1
fi

exit 0
