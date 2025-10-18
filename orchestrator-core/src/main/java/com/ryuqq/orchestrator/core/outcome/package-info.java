/**
 * Operation execution outcome package.
 *
 * <p>This package defines the sealed interface hierarchy for Operation results,
 * providing compile-time exhaustive pattern matching.</p>
 *
 * <h2>Sealed Interface</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.core.outcome.Outcome} - Sealed interface (permits Ok, Retry, Fail)</li>
 * </ul>
 *
 * <h2>Outcome Cases</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.core.outcome.Ok} - Successful completion</li>
 *   <li>{@link com.ryuqq.orchestrator.core.outcome.Retry} - Temporary failure (retryable)</li>
 *   <li>{@link com.ryuqq.orchestrator.core.outcome.Fail} - Permanent failure (non-retryable)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * String result = switch (outcome) {
 *     case Ok ok -> "Success: " + ok.message();
 *     case Retry retry -> "Retry after " + retry.nextRetryAfterMillis() + "ms";
 *     case Fail fail -> "Failed: " + fail.errorCode();
 *     // Compiler enforces exhaustiveness (no default needed)
 * };
 * </pre>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Type Safety:</strong> Sealed interface ensures all cases are known at compile-time</li>
 *   <li><strong>Exhaustiveness:</strong> Pattern matching must handle all cases</li>
 *   <li><strong>Clarity:</strong> Each outcome type clearly indicates success, retry, or failure</li>
 * </ul>
 *
 * @since 1.0.0
 * @author Orchestrator Team
 */
package com.ryuqq.orchestrator.core.outcome;
