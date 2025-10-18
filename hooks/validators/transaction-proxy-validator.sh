#!/bin/bash
# hooks/validators/transaction-proxy-validator.sh
#
# Validates Spring @Transactional proxy limitations
# Related to Issue #27: Spring Proxy Limitation Validation Rules
# https://github.com/ryu-qqq/claude-spring-standards/issues/27

set -e

echo "🔍 Validating Spring @Transactional proxy patterns..."

VIOLATIONS_FOUND=false

# ========================================
# Check 1: Private methods with @Transactional
# ========================================
echo "  → Checking for private @Transactional methods..."

PRIVATE_TRANSACTIONAL=$(find application/src/main/java -name "*.java" -type f -exec \
    awk '
    # Track visibility modifier
    /private.*@Transactional|@Transactional.*private/ {
        if (/private.*@Transactional/) {
            print FILENAME ":" NR ":private @Transactional:" $0
        } else if (/@Transactional.*private/) {
            print FILENAME ":" NR ":@Transactional private:" $0
        }
    }
    # Track if we are inside a private method with @Transactional above it
    /@Transactional/ { tx_anno = 1; tx_line = NR }
    tx_anno && /private.*[a-zA-Z_][a-zA-Z0-9_]*\s*\(/ {
        print FILENAME ":" tx_line ":private @Transactional:" $0
        tx_anno = 0
    }
    /public|protected|private/ && !/@Transactional/ { tx_anno = 0 }
    ' {} \; 2>/dev/null || echo "")

if [ -n "$PRIVATE_TRANSACTIONAL" ]; then
    VIOLATIONS_FOUND=true
    echo ""
    echo "❌ ERROR: Private methods with @Transactional detected:"
    echo "$PRIVATE_TRANSACTIONAL" | while IFS=: read -r file line type content; do
        echo "   $file:$line"
        echo "   Code: $(echo "$content" | xargs)"
        echo ""
    done
    echo "   💡 Spring AOP proxies cannot intercept private methods"
    echo "   💡 Solution: Extract to a separate @Service/@Component bean with public method"
    echo ""
fi

# ========================================
# Check 2: Final methods with @Transactional
# ========================================
echo "  → Checking for final @Transactional methods..."

FINAL_TRANSACTIONAL=$(find application/src/main/java -name "*.java" -type f -exec \
    awk '
    /final.*@Transactional|@Transactional.*final/ {
        if (/public.*final.*@Transactional/) {
            print FILENAME ":" NR ":final @Transactional:" $0
        }
    }
    /@Transactional/ { tx_anno = 1; tx_line = NR }
    tx_anno && /public.*final.*[a-zA-Z_][a-zA-Z0-9_]*\s*\(/ {
        print FILENAME ":" tx_line ":final @Transactional:" $0
        tx_anno = 0
    }
    ' {} \; 2>/dev/null || echo "")

if [ -n "$FINAL_TRANSACTIONAL" ]; then
    VIOLATIONS_FOUND=true
    echo ""
    echo "❌ ERROR: Final methods with @Transactional detected:"
    echo "$FINAL_TRANSACTIONAL" | while IFS=: read -r file line type content; do
        echo "   $file:$line"
        echo "   Code: $(echo "$content" | xargs)"
        echo ""
    done
    echo "   💡 CGLIB proxies cannot override final methods"
    echo "   💡 Solution: Remove 'final' modifier from @Transactional methods"
    echo ""
fi

# ========================================
# Check 3: Internal method calls (heuristic)
# ========================================
echo "  → Checking for potential internal @Transactional method calls..."

INTERNAL_CALLS=$(find application/src/main/java -name "*.java" -type f | while read -r file; do
    # Check if file has @Transactional
    if ! grep -q "@Transactional" "$file"; then
        continue
    fi

    # Look for this.method() patterns in @Transactional methods
    awk '
    /@Transactional/ {
        in_transactional = 1
        method_start = NR
        brace_count = 0
        method_content = ""
    }
    in_transactional {
        method_content = method_content $0 "\n"
        if (/{/) {
            gsub(/[^{]/, "", $0)
            brace_count += length($0)
        }
        if (/}/) {
            gsub(/[^}]/, "", $0)
            brace_count -= length($0)
            if (brace_count == 0) {
                # Check for this.method() calls
                if (match(method_content, /this\.[a-zA-Z_][a-zA-Z0-9_]*\s*\(/)) {
                    print "POTENTIAL:" FILENAME ":" method_start ":this.method() call"
                }
                in_transactional = 0
                method_content = ""
            }
        }
    }
    ' "$file"
done 2>/dev/null || echo "")

if [ -n "$INTERNAL_CALLS" ]; then
    echo ""
    echo "⚠️  WARNING: Potential internal method calls in @Transactional methods:"
    echo "$INTERNAL_CALLS" | while IFS=: read -r marker file line issue; do
        if [ "$marker" = "POTENTIAL" ]; then
            echo "   $file:$line"
            echo "   Issue: $issue"
        fi
    done
    echo ""
    echo "   ℹ️  Internal calls (this.method()) bypass Spring AOP proxy"
    echo "   💡 If calling another @Transactional method, extract to separate bean"
    echo "   💡 Transaction propagation (REQUIRES_NEW, etc.) won't work on internal calls"
    echo ""
    # This is a warning, not a hard failure
fi

# ========================================
# Check 4: Final classes with @Transactional
# ========================================
echo "  → Checking for final classes with @Transactional methods..."

FINAL_CLASSES=$(find application/src/main/java -name "*.java" -type f -exec \
    awk '
    /^[[:space:]]*final[[:space:]]+class/ {
        class_is_final = 1
        class_line = NR
        class_name = $0
    }
    class_is_final && /@Transactional/ {
        print FILENAME ":" class_line ":final class with @Transactional"
        class_is_final = 0
    }
    /^[[:space:]]*class[[:space:]]/ && !/final/ {
        class_is_final = 0
    }
    ' {} \; 2>/dev/null || echo "")

if [ -n "$FINAL_CLASSES" ]; then
    VIOLATIONS_FOUND=true
    echo ""
    echo "❌ ERROR: Final classes containing @Transactional methods detected:"
    echo "$FINAL_CLASSES" | while IFS=: read -r file line issue; do
        echo "   $file:$line"
    done
    echo ""
    echo "   💡 CGLIB cannot create subclass proxies for final classes"
    echo "   💡 Solution: Remove 'final' modifier from classes with @Transactional methods"
    echo ""
fi

# ========================================
# Summary
# ========================================

if [ "$VIOLATIONS_FOUND" = true ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "❌ Spring @Transactional proxy validation FAILED"
    echo ""
    echo "📖 Background:"
    echo "   Spring uses AOP proxies to implement @Transactional behavior"
    echo "   • JDK Dynamic Proxy: Interface-based (default for interfaces)"
    echo "   • CGLIB Proxy: Subclass-based (for concrete classes)"
    echo ""
    echo "   Limitations:"
    echo "   • Cannot proxy private methods (not visible to subclass)"
    echo "   • Cannot proxy final methods (cannot override)"
    echo "   • Cannot proxy final classes (cannot extend)"
    echo "   • Internal calls (this.method()) bypass proxy"
    echo ""
    echo "💡 Solution Patterns:"
    echo ""
    echo "   Pattern 1: Extract to Separate Bean"
    echo "   // ❌ Bad: Internal call bypasses proxy"
    echo "   @Service"
    echo "   class ServiceA {"
    echo "       @Transactional"
    echo "       public void method1() {"
    echo "           this.method2();  // Proxy bypassed!"
    echo "       }"
    echo "       @Transactional(propagation = REQUIRES_NEW)"
    echo "       private void method2() { }  // Won't work"
    echo "   }"
    echo ""
    echo "   // ✅ Good: Separate bean with public method"
    echo "   @Service"
    echo "   class ServiceA {"
    echo "       private final ServiceB serviceB;"
    echo "       @Transactional"
    echo "       public void method1() {"
    echo "           serviceB.method2();  // Proxy works!"
    echo "       }"
    echo "   }"
    echo "   @Service"
    echo "   class ServiceB {"
    echo "       @Transactional(propagation = REQUIRES_NEW)"
    echo "       public void method2() { }  // Works correctly"
    echo "   }"
    echo ""
    echo "   Pattern 2: Remove Restrictive Modifiers"
    echo "   • Remove 'private' → Make 'public' or 'protected'"
    echo "   • Remove 'final' from methods and classes"
    echo ""
    echo "📚 Reference:"
    echo "   • Issue #27: https://github.com/ryu-qqq/claude-spring-standards/issues/27"
    echo "   • Spring AOP Proxies: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html"
    echo "   • docs/CODING_STANDARDS.md - Spring Proxy Limitations section"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit 1
fi

echo "✅ Spring @Transactional proxy validation passed"
exit 0
