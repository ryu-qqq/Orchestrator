package com.ryuqq.orchestrator.adapter.inmemory.store;

import com.ryuqq.orchestrator.testkit.contract.*;
import org.junit.jupiter.api.BeforeEach;

/**
 * Contract Tests for InMemoryStore implementation.
 *
 * <p>This test class validates that the {@code orchestrator-adapter-inmemory} implementations
 * correctly implement the Store, Bus, and IdempotencyManager SPI contracts.</p>
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
 * <p><strong>NOTE:</strong> Currently using testkit's reference implementations
 * as the adapter-inmemory implementations are being developed. This will be updated
 * to use adapter-inmemory's implementations once complete.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class InMemoryStoreContractTest extends AbstractContractTest {

    /**
     * Sets up test fixtures before each test.
     *
     * <p>Creates fresh instances using testkit's InMemoryStore and InMemoryIdempotencyManager
     * for Contract Test execution.</p>
     */
    @BeforeEach
    void setUp() {
        // TODO: Replace with adapter-inmemory implementations when complete
        this.store = new com.ryuqq.orchestrator.testkit.contract.InMemoryStore();
        this.bus = new com.ryuqq.orchestrator.testkit.contract.InMemoryBus();
        this.idempotencyManager = new com.ryuqq.orchestrator.testkit.contract.InMemoryIdempotencyManager();
    }
}
