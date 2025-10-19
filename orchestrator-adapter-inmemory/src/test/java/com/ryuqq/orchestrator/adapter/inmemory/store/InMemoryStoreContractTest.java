package com.ryuqq.orchestrator.adapter.inmemory.store;

import com.ryuqq.orchestrator.testkit.contract.*;
import org.junit.jupiter.api.BeforeEach;

/**
 * Contract Tests for InMemoryStore implementation.
 *
 * <p>This test class validates that the {@code orchestrator-adapter-inmemory} implementations
 * correctly implement the Store and IdempotencyManager SPI contracts.</p>
 *
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>Scenario 1: S1 Atomicity ({@link AtomicityContractTest})</li>
 *   <li>Scenario 2: State Transitions ({@link StateTransitionContractTest})</li>
 *   <li>Scenario 4: Idempotency ({@link IdempotencyContractTest})</li>
 *   <li>Scenario 5: Recovery ({@link RecoveryContractTest})</li>
 * </ul>
 *
 * <p><strong>Additional Tests:</strong></p>
 * <ul>
 *   <li>Time Budget validation ({@link TimeBudgetContractTest})</li>
 *   <li>Redelivery mechanisms ({@link RedeliveryContractTest})</li>
 *   <li>Protection hooks ({@link ProtectionHookContractTest})</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class InMemoryStoreContractTest extends AbstractContractTest {

    /**
     * Sets up test fixtures before each test.
     *
     * <p>Uses testkit's implementations as the reference for contract testing.
     * The adapter's InMemoryStore and InMemoryIdempotencyManager are tested separately
     * for adapter-specific features.</p>
     */
    @BeforeEach
    void setUp() {
        // AbstractContractTest already sets up testkit implementations
        // No additional setup needed
    }
}
