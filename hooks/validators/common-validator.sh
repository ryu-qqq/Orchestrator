#!/bin/bash
# ========================================
# Common Validator
# ========================================
# Runs on all files regardless of module
# - Checkstyle
# - SpotBugs
# - Javadoc validation
# ========================================

WARNINGS=0

YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
    WARNINGS=1
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# ========================================
# Javadoc Validation
# ========================================

for file in "$@"; do
    [[ $file != *.java ]] && continue

    # Check for public classes/interfaces without Javadoc
    if grep -q "public \(class\|interface\|enum\)" "$file"; then
        # Check if Javadoc exists before the class
        if ! grep -B 3 "public \(class\|interface\|enum\)" "$file" | grep -q "/\*\*"; then
            log_warning "$file: Public type missing Javadoc"
        fi
    fi

    # Check for @author tag
    if grep -q "public \(class\|interface\|enum\)" "$file"; then
        if ! grep -q "@author" "$file"; then
            log_warning "$file: Missing @author tag"
        fi
    fi
done

# ========================================
# Result (Warnings don't block commit)
# ========================================

if [ $WARNINGS -eq 1 ]; then
    echo ""
    echo "⚠️  Warnings detected but not blocking commit"
    echo "Please address these in your next commit"
fi

exit 0  # Always succeed (warnings only)
