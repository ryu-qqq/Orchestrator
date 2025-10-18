#!/bin/bash
# hooks/validators/transaction-boundary-validator.sh
#
# Validates that @Transactional methods do not call external APIs
# Related to Issue #28: Transaction Boundary Validation for External API Calls
# https://github.com/ryu-qqq/claude-spring-standards/issues/28

set -e

echo "🔍 Validating @Transactional methods for external API calls..."

# External API Port patterns to detect
EXTERNAL_PORT_PATTERNS=(
    "GeneratePresignedUrl.*Port"
    "S3.*Port"
    "SendMessage.*Port"
    "PublishEvent.*Port"
    "ExternalApi.*Port"
    "HttpClient.*Port"
)

# AWS SDK patterns
AWS_SDK_PATTERNS=(
    "s3Client\."
    "sqsClient\."
    "snsClient\."
    "software\.amazon\.awssdk"
)

# HTTP Client patterns
HTTP_CLIENT_PATTERNS=(
    "restTemplate\."
    "webClient\."
    "HttpClient"
    "FeignClient"
)

VIOLATIONS_FOUND=false
VIOLATION_DETAILS=""

# Find all Java files in application layer
JAVA_FILES=$(find application/src/main/java -name "*.java" -type f 2>/dev/null || echo "")

if [ -z "$JAVA_FILES" ]; then
    echo "⚠️  No Java files found in application/src/main/java"
    exit 0
fi

# Process each Java file
while IFS= read -r file; do
    # Skip if file doesn't contain @Transactional
    if ! grep -q "@Transactional" "$file"; then
        continue
    fi

    # Extract methods with @Transactional and check for external API calls
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
                # Check for external Port calls
                if (match(method_content, /[a-zA-Z_][a-zA-Z0-9_]*Port\.(generate|upload|send|publish)/)) {
                    print "VIOLATION:PORT:" FILENAME ":" method_start
                }
                # Check for AWS SDK calls
                if (match(method_content, /(s3Client|sqsClient|snsClient|software\.amazon\.awssdk)/)) {
                    print "VIOLATION:AWS:" FILENAME ":" method_start
                }
                # Check for HTTP client calls
                if (match(method_content, /(restTemplate|webClient|HttpClient|FeignClient)/)) {
                    print "VIOLATION:HTTP:" FILENAME ":" method_start
                }
                in_transactional = 0
                method_content = ""
            }
        }
    }
    ' "$file"
done <<< "$JAVA_FILES" | while IFS=: read -r type violation_type filename line_number; do
    if [ "$type" = "VIOLATION" ]; then
        VIOLATIONS_FOUND=true

        case "$violation_type" in
            PORT)
                echo "❌ ERROR: External API Port call detected in @Transactional method"
                echo "   File: $filename:$line_number"
                echo "   💡 External Port calls (S3, SQS, HTTP) should be outside @Transactional"
                ;;
            AWS)
                echo "❌ ERROR: AWS SDK call detected in @Transactional method"
                echo "   File: $filename:$line_number"
                echo "   💡 AWS SDK calls should be outside @Transactional to prevent DB connection lock"
                ;;
            HTTP)
                echo "❌ ERROR: HTTP Client call detected in @Transactional method"
                echo "   File: $filename:$line_number"
                echo "   💡 HTTP calls should be outside @Transactional (200-1000ms latency)"
                ;;
        esac
        echo ""
    fi
done

# Check result
if [ "$VIOLATIONS_FOUND" = true ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "❌ Transaction boundary validation FAILED"
    echo ""
    echo "📖 Background:"
    echo "   External API calls inside @Transactional methods cause:"
    echo "   • DB connection held for 100-500ms during API response wait"
    echo "   • Connection pool exhaustion under high load"
    echo "   • Transaction timeout risks"
    echo ""
    echo "💡 Solution Pattern:"
    echo "   1. Remove @Transactional from the method with external API calls"
    echo "   2. Move external API call outside transaction"
    echo "   3. Create separate @Transactional method for DB operations only"
    echo ""
    echo "   Example:"
    echo "   // ❌ Bad"
    echo "   @Transactional"
    echo "   public Result process() {"
    echo "       S3Result s3 = s3Port.upload();  // External API in transaction!"
    echo "       return repository.save(entity);"
    echo "   }"
    echo ""
    echo "   // ✅ Good"
    echo "   public Result process() {  // No @Transactional"
    echo "       S3Result s3 = s3Port.upload();  // External API outside"
    echo "       return persistenceService.save(entity);  // Separate transaction"
    echo "   }"
    echo ""
    echo "📚 Reference:"
    echo "   • Issue #28: https://github.com/ryu-qqq/claude-spring-standards/issues/28"
    echo "   • docs/CODING_STANDARDS.md - Transaction Boundaries section"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit 1
fi

echo "✅ Transaction boundary validation passed"
exit 0
