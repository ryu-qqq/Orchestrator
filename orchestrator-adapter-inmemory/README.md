# Orchestrator Adapter InMemory

> In-memory reference implementation of Orchestrator adapters for testing and educational purposes

## Overview

This module provides thread-safe, in-memory implementations of the Orchestrator SPI (Service Provider Interface) for:
- **Store**: Operation state and write-ahead log management
- **Bus**: Message queue with delayed delivery and DLQ support
- **IdempotencyManager**: Idempotency key to OpId mapping
- **Protection**: Circuit breaker, rate limiter, and bulkhead (샘플 구현 예정)

## Purpose

1. **Reference Implementation**: Demonstrates how to implement Orchestrator adapters correctly
2. **Contract Test Validation**: Used in testkit for validating adapter implementations
3. **Educational Resource**: Helps developers understand the SPI contracts
4. **Local Development**: Enables testing without external dependencies (DB, message broker)

## Architecture

### Store SPI Implementation

**Data Structures:**
- `ConcurrentHashMap<OpId, OperationEntity>` - Operation state management
- `CopyOnWriteArrayList<WALEntry>` - Write-ahead log with occurred_at ordering
- `ConcurrentHashMap<OpId, Envelope>` - Original command envelopes

**Key Features:**
- Thread-safe concurrent operations
- Atomic writeAhead + finalize operations
- WAL state tracking (PENDING → COMPLETED)
- scanWA() for recovery scenarios
- scanInProgress() for timeout detection

### IdempotencyManager SPI Implementation

**Data Structures:**
- `ConcurrentHashMap<IdempotencyKey, OpId>` - Idempotency mapping

**Key Features:**
- Atomic get-or-create using `computeIfAbsent()`
- Thread-safe concurrent access
- UUID-based OpId generation

## Usage

### As a Dependency

```gradle
dependencies {
    testImplementation project(':orchestrator-adapter-inmemory')
}
```

### In Contract Tests

```java
public class MyStoreContractTest extends AbstractContractTest {
    @BeforeEach
    void setUp() {
        this.store = new com.ryuqq.orchestrator.testkit.contract.InMemoryStore();
        this.idempotencyManager = new com.ryuqq.orchestrator.testkit.contract.InMemoryIdempotencyManager();
    }
}
```

### Direct Usage

```java
// Create SPI implementations
Store store = new InMemoryStore();
IdempotencyManager idempotencyManager = new InMemoryIdempotencyManager();

// Use in application
OpId opId = OpId.of(UUID.randomUUID().toString());
Outcome outcome = Ok.of(opId);

store.writeAhead(opId, outcome);
store.finalize(opId, OperationState.COMPLETED);
```

## Contract Tests Passed

This implementation passes all Contract Tests:

✅ **Scenario 1**: S1 Atomicity (AtomicityContractTest)
✅ **Scenario 2**: State Transitions (StateTransitionContractTest)
✅ **Scenario 4**: Idempotency (IdempotencyContractTest)
✅ **Scenario 5**: Recovery (RecoveryContractTest)
✅ **Time Budget**: Time budget validation (TimeBudgetContractTest)
✅ **Redelivery**: Redelivery mechanisms (RedeliveryContractTest)
✅ **Protection**: Protection hooks (ProtectionHookContractTest)

## Performance Characteristics

### Thread Safety
- All operations are thread-safe using concurrent data structures
- No manual locking required for basic operations
- `finalize()` uses synchronized for atomic state transitions

### Memory Efficiency
- In-memory only (no disk persistence)
- CopyOnWriteArrayList for WAL (iteration-optimized)
- ConcurrentHashMap for O(1) lookups

### Scalability
- Suitable for 1000+ operations in tests
- Not suitable for production workloads
- No built-in memory limits or eviction policies

## Limitations

⚠️ **Not for Production Use:**
- No actual ACID transaction support
- Data lost on process restart
- No persistence layer
- Limited to memory capacity

⚠️ **Transaction Simulation:**
- ThreadLocal-based transaction context (placeholder)
- No distributed transaction support
- No rollback on exceptions (manual cleanup required)

⚠️ **Concurrency:**
- Thread-safe within single JVM only
- Not suitable for distributed systems
- No cross-process synchronization

## Implementation Details

### Operation Entity Structure

```java
class OperationEntity {
    OpId opId;
    OperationState state;  // PENDING, IN_PROGRESS, COMPLETED, FAILED
    int version;           // Optimistic locking version
}
```

### WAL Entry Structure

```java
class WALEntry {
    OpId opId;
    Outcome outcome;       // Ok, Retry, Fail
    WriteAheadState state; // PENDING, COMPLETED
    long occurredAt;       // Timestamp for ordering
}
```

### Idempotency Key Mapping

```java
IdempotencyKey {
    Domain domain;
    EventType eventType;
    BizKey bizKey;
    IdemKey idemKey;
} → OpId
```

## Testing

### Run Contract Tests

```bash
./gradlew :orchestrator-adapter-inmemory:test
```

### Test Coverage

Target: ≥ 85% code coverage

```bash
./gradlew :orchestrator-adapter-inmemory:jacocoTestReport
open orchestrator-adapter-inmemory/build/reports/jacoco/test/html/index.html
```

## Future Enhancements (OR-14, OR-15)

- **InMemoryBus**: Message queue with delayed delivery and DLQ (OR-14)
- **Sample Protection Adapters**: Circuit breaker, rate limiter, bulkhead (OR-15)
- **Performance Benchmarks**: 1000 TPS target validation
- **Concurrency Tests**: 100-thread stress testing
- **Memory Leak Tests**: 1M operations processing validation

## Contributing

This is a reference implementation. For production adapters, see:
- `orchestrator-adapter-persistence` (JPA-based Store)
- `orchestrator-adapter-messaging` (Kafka/SQS-based Bus)

## License

TBD

## See Also

- [Core SDK Documentation](../orchestrator-core/README.md)
- [TestKit Documentation](../orchestrator-testkit/README.md)
- [Quick Start Guide](../docs/guides/01-quick-start.md)
- [Adapter Implementation Guide](../docs/guides/02-adapter-implementation.md)
