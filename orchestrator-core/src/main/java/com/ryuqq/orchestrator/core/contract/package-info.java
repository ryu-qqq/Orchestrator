/**
 * Operation execution contract package.
 *
 * <p>This package defines the contracts for Operation execution:</p>
 *
 * <h2>Records</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.core.contract.Command} - Operation execution command</li>
 *   <li>{@link com.ryuqq.orchestrator.core.contract.Envelope} - Command with metadata (OpId, timestamp)</li>
 * </ul>
 *
 * <h2>Purpose</h2>
 * <p>These contracts define the input structure for Operation execution,
 * encapsulating all necessary information for workflow orchestration.</p>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Immutability:</strong> Records are immutable by default</li>
 *   <li><strong>Validation:</strong> Compact constructors enforce invariants</li>
 *   <li><strong>Composition:</strong> Envelope composes Command with execution metadata</li>
 * </ul>
 *
 * @since 1.0.0
 * @author Orchestrator Team
 */
package com.ryuqq.orchestrator.core.contract;
