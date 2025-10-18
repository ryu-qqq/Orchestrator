#!/bin/bash
# ========================================
# Dead Code Detector
# ========================================
# Detects method-level unused code:
# - Private methods with no callers
# - Public methods never invoked in project
# - Helper/Utility classes with no usage
# - Methods created "for future" but not used
# ========================================

DEAD_CODE_FOUND=0
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Colors
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_warning() {
    echo -e "${YELLOW}⚠️  DEAD CODE: $1${NC}"
    DEAD_CODE_FOUND=1
}

log_info() {
    echo -e "${YELLOW}💡 $1${NC}"
}

# ========================================
# Helper: Extract Method Names
# ========================================

# Extract all method names from a file
extract_methods() {
    local file="$1"
    # Matches: public/private/protected ReturnType methodName(
    grep -oE "(public|private|protected)\s+\w+\s+\w+\s*\(" "$file" | \
        awk '{print $NF}' | \
        tr -d '(' | \
        sort -u
}

# Extract private method names only
extract_private_methods() {
    local file="$1"
    grep -oE "private\s+\w+\s+\w+\s*\(" "$file" | \
        awk '{print $NF}' | \
        tr -d '(' | \
        sort -u
}

# ========================================
# Check 1: Private Methods with No Callers
# ========================================

check_private_methods() {
    local file="$1"

    while IFS= read -r method; do
        [ -z "$method" ] && continue

        # Count all occurrences of method name in file
        local total_count=$(grep -c "\b$method\b" "$file" || echo 0)

        # Count definition occurrences (should be 1)
        local def_count=$(grep -c "private.*\b$method\s*(" "$file" || echo 0)

        # If only definition exists (no calls), it's dead code
        if [ "$total_count" -le "$def_count" ]; then
            log_warning "$file: Private method '$method()' is never called"
        fi
    done < <(extract_private_methods "$file")
}

# ========================================
# Check 2: Public Methods Never Used in Project
# ========================================

check_public_methods() {
    local file="$1"
    local class_name=$(basename "$file" .java)

    # Extract public methods (excluding constructors, getters, setters)
    local public_methods=$(grep -oE "public\s+\w+\s+\w+\s*\(" "$file" | \
        awk '{print $NF}' | \
        tr -d '(' | \
        grep -v "^get" | \
        grep -v "^set" | \
        grep -v "^is" | \
        grep -v "^has" | \
        grep -v "^$class_name" | \
        sort -u)

    for method in $public_methods; do
        [ -z "$method" ] && continue

        # Search for method usage in entire project (excluding test files)
        local usage_count=$(find "$PROJECT_ROOT" -name "*.java" \
            ! -path "*/test/*" \
            ! -path "*/$file" \
            -exec grep -l "\b$method\b" {} \; 2>/dev/null | wc -l | tr -d ' ')

        # If method not used anywhere except definition file
        if [ "$usage_count" -eq 0 ]; then
            log_warning "$file: Public method '$method()' is never used in project"
        fi
    done
}

# ========================================
# Check 3: Helper/Utility Classes with No References
# ========================================

check_utility_classes() {
    local file="$1"
    local filename=$(basename "$file")
    local class_name="${filename%.java}"

    # Only check files with utility-like names
    if [[ ! "$filename" =~ (Utils|Helper|Util|Utilities|Handler|Manager)\.java$ ]]; then
        return 0
    fi

    # Search for class usage in entire project
    local ref_count=$(find "$PROJECT_ROOT" -name "*.java" \
        ! -path "*/$file" \
        -exec grep -l "\b$class_name\b" {} \; 2>/dev/null | wc -l | tr -d ' ')

    if [ "$ref_count" -eq 0 ]; then
        log_warning "$file: Utility class '$class_name' is never referenced in project"
        log_info "   Utility classes should only exist if explicitly requested"
    fi
}

# ========================================
# Check 4: Methods Created "For Future" Pattern
# ========================================

check_future_methods() {
    local file="$1"

    # Check for TODO comments near method definitions
    local methods_with_todo=$(grep -B 2 -E "(public|private)\s+\w+\s+\w+\s*\(" "$file" | \
        grep -E "(TODO|FIXME|XXX)" | wc -l | tr -d ' ')

    if [ "$methods_with_todo" -gt 0 ]; then
        log_warning "$file: Contains $methods_with_todo method(s) with TODO/FIXME comments"
        log_info "   Methods should be fully implemented when created, not marked for future work"
    fi

    # Check for methods throwing NotImplementedException or similar
    if grep -q "throw new.*NotImplemented" "$file"; then
        log_warning "$file: Contains NotImplementedException - method not actually implemented"
        log_info "   Only write methods that are fully implemented and requested"
    fi
}

# ========================================
# Main Analysis
# ========================================

log_info "Analyzing staged files for dead code..."
echo ""

for file in "$@"; do
    # Skip non-Java files
    [[ $file != *.java ]] && continue

    # Skip test files (tests may have methods called by framework)
    [[ $file == */test/* ]] && continue
    [[ $file == *Test.java ]] && continue

    # Only check files that exist
    [ ! -f "$file" ] && continue

    # Run all checks
    check_private_methods "$file"
    check_public_methods "$file"
    check_utility_classes "$file"
    check_future_methods "$file"
done

# ========================================
# Result (Warning only, doesn't block)
# ========================================

if [ $DEAD_CODE_FOUND -eq 1 ]; then
    echo ""
    echo "┌────────────────────────────────────────────────────────┐"
    echo "│  💡 DEAD CODE POLICY                                   │"
    echo "├────────────────────────────────────────────────────────┤"
    echo "│  Only write code that is:                              │"
    echo "│  1. ✅ Explicitly requested by user                    │"
    echo "│  2. ✅ Actually called/used in project                 │"
    echo "│  3. ✅ Fully implemented (no TODO/NotImplemented)      │"
    echo "│                                                        │"
    echo "│  ❌ Do NOT create:                                     │"
    echo "│  - Methods \"for future use\"                          │"
    echo "│  - Utility classes \"that might be useful\"            │"
    echo "│  - Placeholder methods with TODO                       │"
    echo "└────────────────────────────────────────────────────────┘"
    echo ""
    echo "⚠️  This is a WARNING (commit not blocked)"
    echo "Review flagged code and remove if not explicitly requested"
    echo ""
fi

exit 0  # Always succeed (warnings only)
