/**
 * Operation state machine package.
 *
 * <p>This package implements the state transition rules for Operation lifecycle,
 * ensuring data integrity through strict invariants.</p>
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.core.statemachine.OperationState} - Operation lifecycle states (enum)</li>
 *   <li>{@link com.ryuqq.orchestrator.core.statemachine.StateTransition} - State transition validation and execution</li>
 * </ul>
 *
 * <h2>State Transition Rules</h2>
 * <pre>
 * PENDING → IN_PROGRESS (accept)
 * IN_PROGRESS → COMPLETED (success)
 * IN_PROGRESS → FAILED (failure)
 *
 * Forbidden:
 * - COMPLETED → * (terminal state)
 * - FAILED → * (terminal state)
 * - Backward transitions (e.g., COMPLETED → IN_PROGRESS)
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * OperationState state = OperationState.PENDING;
 * state = StateTransition.transition(state, OperationState.IN_PROGRESS);
 * state = StateTransition.transition(state, OperationState.COMPLETED);
 *
 * // This will throw IllegalStateException
 * StateTransition.validate(state, OperationState.IN_PROGRESS);
 * </pre>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Invariants:</strong> Terminal states cannot transition to any other state</li>
 *   <li><strong>Validation:</strong> All transitions are validated before execution</li>
 *   <li><strong>Fail-Fast:</strong> Invalid transitions throw IllegalStateException immediately</li>
 * </ul>
 *
 * @since 1.0.0
 * @author Orchestrator Team
 */
package com.ryuqq.orchestrator.core.statemachine;
