package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.contract.Command;
import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.model.*;
import com.ryuqq.orchestrator.core.spi.WriteAheadState;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for Contract Tests.
 *
 * <p>This class provides common test infrastructure including in-memory SPI implementations
 * and helper methods for test scenarios.</p>
 *
 * <p><strong>Test Infrastructure:</strong></p>
 * <ul>
 *   <li>InMemoryStore: Persistent storage simulation</li>
 *   <li>InMemoryBus: Message queue simulation</li>
 *   <li>InMemoryIdempotencyManager: Idempotency key management</li>
 * </ul>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * public class MyContractTest extends AbstractContractTest {
 *     {@literal @}Test
 *     void testScenario() {
 *         Envelope envelope = createTestEnvelope("BIZ-001", "IDEM-001");
 *         OpId opId = envelope.opId();
 *
 *         store.setState(opId, OperationState.IN_PROGRESS);
 *         // ... test logic ...
 *
 *         assertOperationState(opId, OperationState.COMPLETED);
 *     }
 * }
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
public abstract class AbstractContractTest {

    protected InMemoryStore store;
    protected InMemoryBus bus;
    protected InMemoryIdempotencyManager idempotencyManager;

    /**
     * Sets up test fixtures before each test.
     *
     * <p>Creates fresh instances of all SPI implementations.</p>
     */
    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        bus = new InMemoryBus();
        idempotencyManager = new InMemoryIdempotencyManager();
    }

    /**
     * Cleans up test fixtures after each test.
     *
     * <p>Clears all in-memory state to prevent test interference.</p>
     */
    @AfterEach
    void tearDown() {
        if (store != null) {
            store.clear();
        }
        if (bus != null) {
            bus.clear();
        }
        if (idempotencyManager != null) {
            idempotencyManager.clear();
        }
    }

    /**
     * Creates a test envelope with specified business key and idempotency key.
     *
     * @param bizKeyValue the business key value (e.g., "ORDER-123")
     * @param idemKeyValue the idempotency key value (e.g., "idem-abc")
     * @return a new envelope with generated OpId
     */
    protected Envelope createTestEnvelope(String bizKeyValue, String idemKeyValue) {
        Domain domain = Domain.of("TEST");
        EventType eventType = EventType.of("CREATE");
        BizKey bizKey = BizKey.of(bizKeyValue);
        IdemKey idemKey = IdemKey.of(idemKeyValue);

        Command command = new Command(domain, eventType, bizKey, idemKey, null);
        OpId opId = OpId.of(java.util.UUID.randomUUID().toString());

        return new Envelope(opId, command, System.currentTimeMillis());
    }

    /**
     * Creates a test envelope with payload.
     *
     * @param bizKeyValue the business key value
     * @param idemKeyValue the idempotency key value
     * @param payloadJson the payload JSON string
     * @return a new envelope with payload
     */
    protected Envelope createTestEnvelopeWithPayload(String bizKeyValue, String idemKeyValue, String payloadJson) {
        Domain domain = Domain.of("TEST");
        EventType eventType = EventType.of("CREATE");
        BizKey bizKey = BizKey.of(bizKeyValue);
        IdemKey idemKey = IdemKey.of(idemKeyValue);
        Payload payload = Payload.of(payloadJson);

        Command command = new Command(domain, eventType, bizKey, idemKey, payload);
        OpId opId = OpId.of(java.util.UUID.randomUUID().toString());

        return new Envelope(opId, command, System.currentTimeMillis());
    }

    /**
     * Asserts that the operation is in the expected state.
     *
     * @param opId the operation ID
     * @param expectedState the expected operation state
     */
    protected void assertOperationState(OpId opId, OperationState expectedState) {
        OperationState actualState = store.getState(opId);
        assertEquals(expectedState, actualState,
                String.format("Expected operation state %s but was %s for opId: %s",
                        expectedState, actualState, opId));
    }

    /**
     * Asserts that the WAL entry is in the expected state.
     *
     * @param opId the operation ID
     * @param expectedState the expected WAL state
     */
    protected void assertWALState(OpId opId, WriteAheadState expectedState) {
        WriteAheadState actualState = store.getWALState(opId);
        assertEquals(expectedState, actualState,
                String.format("Expected WAL state %s but was %s for opId: %s",
                        expectedState, actualState, opId));
    }

    /**
     * Asserts that no WAL entry exists for the given operation.
     *
     * @param opId the operation ID
     */
    protected void assertNoWALEntry(OpId opId) {
        WriteAheadState state = store.getWALState(opId);
        assertNull(state,
                String.format("Expected no WAL entry for opId %s but found state: %s",
                        opId, state));
    }

    /**
     * Asserts that the operation does not exist in the store.
     *
     * @param opId the operation ID
     */
    protected void assertOperationNotExists(OpId opId) {
        assertThrows(IllegalStateException.class, () -> store.getState(opId),
                String.format("Expected no operation for opId %s but it exists", opId));
    }

    /**
     * Asserts that two IdempotencyKeys map to the same OpId.
     *
     * @param key1 the first idempotency key
     * @param key2 the second idempotency key
     */
    protected void assertSameOpId(IdempotencyKey key1, IdempotencyKey key2) {
        OpId opId1 = idempotencyManager.find(key1);
        OpId opId2 = idempotencyManager.find(key2);
        assertNotNull(opId1, "OpId for key1 should not be null");
        assertNotNull(opId2, "OpId for key2 should not be null");
        assertEquals(opId1, opId2,
                String.format("Expected same OpId for keys %s and %s but got %s and %s",
                        key1, key2, opId1, opId2));
    }

    /**
     * Creates an IdempotencyKey from a string value.
     *
     * @param idemKeyValue the idempotency key value
     * @return an IdempotencyKey instance
     */
    protected IdempotencyKey createIdempotencyKey(String idemKeyValue) {
        Domain domain = Domain.of("TEST");
        EventType eventType = EventType.of("CREATE");
        BizKey bizKey = BizKey.of("BIZ-" + idemKeyValue);
        IdemKey idemKey = IdemKey.of(idemKeyValue);
        return new IdempotencyKey(domain, eventType, bizKey, idemKey);
    }

    /**
     * Sleeps for the specified duration. Used for timing-sensitive tests.
     *
     * @param millis milliseconds to sleep
     */
    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sleep interrupted", e);
        }
    }
}
