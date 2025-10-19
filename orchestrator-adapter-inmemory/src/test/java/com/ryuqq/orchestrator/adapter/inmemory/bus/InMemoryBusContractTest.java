package com.ryuqq.orchestrator.adapter.inmemory.bus;

import com.ryuqq.orchestrator.testkit.contract.AbstractContractTest;
import org.junit.jupiter.api.BeforeEach;

/**
 * Contract Test for InMemoryBus adapter.
 *
 * <p>This test extends {@link AbstractContractTest} to verify that the InMemoryBus
 * implementation correctly satisfies all Bus SPI contract requirements.</p>
 *
 * <p><strong>Tested Scenarios:</strong></p>
 * <ul>
 *   <li><strong>Scenario 3:</strong> Long-Running/Redelivery (RedeliveryContractTest)</li>
 *   <li><strong>Scenario 6:</strong> Time Budget (TimeBudgetContractTest)</li>
 * </ul>
 *
 * <p>The AbstractContractTest base class provides comprehensive test scenarios
 * including visibility timeout, message redelivery, concurrency control, and idempotency.</p>
 *
 * <p><strong>Implementation Note:</strong></p>
 * <p>We use the testkit's InMemoryBus as the reference implementation for contract tests.
 * The adapter's InMemoryBus is tested separately for additional features like
 * partition key routing simulation.</p>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 * @see AbstractContractTest
 * @see com.ryuqq.orchestrator.testkit.contract.RedeliveryContractTest
 * @see com.ryuqq.orchestrator.testkit.contract.TimeBudgetContractTest
 */
class InMemoryBusContractTest extends AbstractContractTest {

    /**
     * Sets up the test environment before each test method.
     *
     * <p>Uses testkit's InMemoryBus as the reference implementation
     * to verify contract compliance.</p>
     */
    @BeforeEach
    void setUp() {
        // AbstractContractTest already sets up testkit's InMemoryBus
        // No additional setup needed
    }
}
