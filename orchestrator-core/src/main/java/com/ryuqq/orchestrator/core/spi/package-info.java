/**
 * Service Provider Interface (SPI) package.
 *
 * <p>This package defines interfaces that must be implemented by infrastructure adapters
 * to provide concrete functionality for the Core SDK.</p>
 *
 * <h2>SPI Interfaces</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.core.spi.IdempotencyManager} - Idempotency key → OpId mapping management</li>
 * </ul>
 *
 * <h2>Implementation Responsibility</h2>
 * <p>Adapter layers (e.g., orchestrator-adapter-inmemory, orchestrator-adapter-persistence)
 * are responsible for providing concrete implementations of these SPIs.</p>
 *
 * <h2>Idempotency Manager Implementation Guidelines</h2>
 * <ul>
 *   <li><strong>Concurrency Control:</strong> Ensure only one OpId is created for duplicate IdempotencyKeys</li>
 *   <li><strong>Storage:</strong> Choose appropriate storage (in-memory, database, cache)</li>
 *   <li><strong>OpId Generation:</strong> Use UUID, Snowflake ID, or other unique ID strategy</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Hexagonal Architecture:</strong> Core defines interfaces, adapters provide implementations</li>
 *   <li><strong>Dependency Inversion:</strong> Core does not depend on infrastructure</li>
 *   <li><strong>Pluggability:</strong> Multiple implementations can coexist (e.g., InMemory for tests, JPA for production)</li>
 * </ul>
 *
 * @since 1.0.0
 * @author Orchestrator Team
 */
package com.ryuqq.orchestrator.core.spi;
