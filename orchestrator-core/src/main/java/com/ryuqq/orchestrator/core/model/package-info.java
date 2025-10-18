/**
 * Core domain model package containing Value Objects and composite keys.
 *
 * <p>This package defines immutable value objects that form the foundation
 * of the Orchestrator SDK type system:</p>
 *
 * <h2>Value Objects</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.core.model.OpId} - Operation unique identifier</li>
 *   <li>{@link com.ryuqq.orchestrator.core.model.BizKey} - Business entity key</li>
 *   <li>{@link com.ryuqq.orchestrator.core.model.IdemKey} - Idempotency key (client-provided)</li>
 *   <li>{@link com.ryuqq.orchestrator.core.model.Domain} - Business domain identifier</li>
 *   <li>{@link com.ryuqq.orchestrator.core.model.EventType} - Event type identifier</li>
 *   <li>{@link com.ryuqq.orchestrator.core.model.Payload} - Serialized business data</li>
 * </ul>
 *
 * <h2>Composite Keys</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.core.model.IdempotencyKey} - Composite key for idempotency (Domain, EventType, BizKey, IdemKey)</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Immutability:</strong> All value objects are immutable (final fields)</li>
 *   <li><strong>Validation:</strong> Constructor validation ensures data integrity</li>
 *   <li><strong>Pure Java:</strong> No external dependencies (Java 21 only)</li>
 *   <li><strong>Type Safety:</strong> Strong typing prevents misuse</li>
 * </ul>
 *
 * @since 1.0.0
 * @author Orchestrator Team
 */
package com.ryuqq.orchestrator.core.model;
