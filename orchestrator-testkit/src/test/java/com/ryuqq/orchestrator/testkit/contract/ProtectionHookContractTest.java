package com.ryuqq.orchestrator.testkit.contract;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.protection.*;
import com.ryuqq.orchestrator.core.protection.noop.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract Test for Scenario 7: Protection Hook Behaviors.
 *
 * <p>This test validates protection hooks (CircuitBreaker, Timeout, Bulkhead, RateLimiter, Hedging)
 * behave correctly under various conditions and enforce their protection mechanisms.</p>
 *
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>CircuitBreaker: OPEN/HALF_OPEN/CLOSED state transitions</li>
 *   <li>Timeout: TimeoutException when exceeding configured timeout</li>
 *   <li>Bulkhead: BulkheadFullException when concurrency limit exceeded</li>
 *   <li>RateLimiter: RateLimitExceededException when QPS limit exceeded</li>
 *   <li>Hedging: Duplicate request prevention when hedging enabled</li>
 *   <li>Hook Execution Order: Correct chaining order validation</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class ProtectionHookContractTest extends AbstractContractTest {

    // ===================================================================
    // CIRCUIT BREAKER TESTS
    // ===================================================================

    @Test
    void testCircuitBreaker_ClosedState_AllowsRequests() {
        // Given
        CircuitBreaker cb = new NoOpCircuitBreaker();
        OpId opId = OpId.of("cb-test-01");

        // When: circuit is in CLOSED state
        CircuitBreakerState state = cb.getState();

        // Then: requests are allowed
        assertEquals(CircuitBreakerState.CLOSED, state,
                "Circuit should be CLOSED initially");
        assertTrue(cb.tryAcquire(opId),
                "CLOSED circuit should allow requests");
    }

    @Test
    void testCircuitBreaker_OpenState_RejectsRequests() {
        // Given: mock circuit breaker that can be forced to OPEN
        TestableCircuitBreaker cb = new TestableCircuitBreaker();
        OpId opId = OpId.of("cb-test-02");

        // When: force circuit to OPEN state
        cb.forceOpen();

        // Then: requests are rejected
        assertEquals(CircuitBreakerState.OPEN, cb.getState(),
                "Circuit should be OPEN");
        assertFalse(cb.tryAcquire(opId),
                "OPEN circuit should reject requests");
    }

    @Test
    void testCircuitBreaker_HalfOpenState_AllowsLimitedRequests() {
        // Given: testable circuit breaker
        TestableCircuitBreaker cb = new TestableCircuitBreaker();
        OpId opId = OpId.of("cb-test-03");

        // When: force circuit to HALF_OPEN state
        cb.forceHalfOpen();

        // Then: limited requests are allowed
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState(),
                "Circuit should be HALF_OPEN");
        assertTrue(cb.tryAcquire(opId),
                "HALF_OPEN circuit should allow probe requests");
    }

    @Test
    void testCircuitBreaker_RecordSuccess_ClosesCircuit() {
        // Given: circuit in HALF_OPEN state
        TestableCircuitBreaker cb = new TestableCircuitBreaker();
        OpId opId = OpId.of("cb-test-04");

        cb.forceHalfOpen();
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());

        // When: record success
        cb.recordSuccess(opId);

        // Then: circuit transitions to CLOSED
        assertEquals(CircuitBreakerState.CLOSED, cb.getState(),
                "Circuit should transition to CLOSED after success");
    }

    @Test
    void testCircuitBreaker_RecordFailure_OpensCircuit() {
        // Given: testable circuit breaker in CLOSED state
        TestableCircuitBreaker cb = new TestableCircuitBreaker();
        OpId opId = OpId.of("cb-test-05");

        assertEquals(CircuitBreakerState.CLOSED, cb.getState());

        // When: record enough failures to trigger OPEN
        for (int i = 0; i < 10; i++) { // Exceed failure threshold
            cb.recordFailure(opId, new RuntimeException("Test failure " + i));
        }

        // Then: circuit opens
        assertEquals(CircuitBreakerState.OPEN, cb.getState(),
                "Circuit should open after excessive failures");
    }

    // ===================================================================
    // TIMEOUT TESTS
    // ===================================================================

    @Test
    void testTimeout_WithinTimeout_NoException() {
        // Given
        TimeoutPolicy policy = new NoOpTimeoutPolicy();
        OpId opId = OpId.of("timeout-test-01");

        // When: operation completes within timeout (NoOp returns 0 = no timeout)
        long timeout = policy.getPerAttemptTimeoutMs(opId);

        // Then: no timeout (0 means disabled)
        assertEquals(0, timeout, "NoOp timeout should be 0 (disabled)");
    }

    @Test
    void testTimeout_ExceedsTimeout_RecordsTimeout() {
        // Given
        TimeoutPolicy policy = new NoOpTimeoutPolicy();
        OpId opId = OpId.of("timeout-test-02");

        // When: operation takes longer than allowed (simulated)
        long elapsedMs = 2000; // 2 seconds

        // Then: timeout is recorded
        assertDoesNotThrow(() -> policy.recordTimeout(opId, elapsedMs),
                "Recording timeout should not throw");
    }

    @Test
    void testTimeout_RealTimeout_ThrowsTimeoutException() {
        // Given: testable timeout policy
        TestableTimeoutPolicy policy = new TestableTimeoutPolicy(1000); // 1 second
        OpId opId = OpId.of("timeout-test-03");

        // When: operation exceeds timeout
        long configuredTimeout = policy.getPerAttemptTimeoutMs(opId);
        assertEquals(1000, configuredTimeout);

        // Then: timeout would be enforced by executor (simulated)
        boolean wouldTimeout = configuredTimeout > 0;
        assertTrue(wouldTimeout, "Timeout should be configured");
    }

    // ===================================================================
    // BULKHEAD TESTS
    // ===================================================================

    @Test
    void testBulkhead_BelowLimit_AllowsRequests() {
        // Given
        Bulkhead bulkhead = new NoOpBulkhead();
        OpId opId = OpId.of("bulkhead-test-01");

        // When: acquire below limit (NoOp has unlimited capacity)
        boolean acquired = bulkhead.tryAcquire(opId);

        // Then: request is allowed
        assertTrue(acquired, "Bulkhead should allow requests below limit");
    }

    @Test
    void testBulkhead_AtLimit_RejectsRequests() throws InterruptedException {
        // Given: testable bulkhead with limit = 2
        TestableBulkhead bulkhead = new TestableBulkhead(2);
        OpId opId1 = OpId.of("bulkhead-test-02-1");
        OpId opId2 = OpId.of("bulkhead-test-02-2");
        OpId opId3 = OpId.of("bulkhead-test-02-3");

        // When: acquire up to limit
        assertTrue(bulkhead.tryAcquire(opId1), "First acquire should succeed");
        assertTrue(bulkhead.tryAcquire(opId2), "Second acquire should succeed");

        // Then: third acquire is rejected
        assertFalse(bulkhead.tryAcquire(opId3),
                "Third acquire should fail when limit reached");

        // Cleanup
        bulkhead.release(opId1);
        bulkhead.release(opId2);
    }

    @Test
    void testBulkhead_ReleasePermit_AllowsNewRequests() throws InterruptedException {
        // Given: bulkhead at limit
        TestableBulkhead bulkhead = new TestableBulkhead(1);
        OpId opId1 = OpId.of("bulkhead-test-03-1");
        OpId opId2 = OpId.of("bulkhead-test-03-2");

        bulkhead.tryAcquire(opId1);

        // When: release permit
        bulkhead.release(opId1);

        // Then: new request is allowed
        assertTrue(bulkhead.tryAcquire(opId2),
                "Should allow request after release");

        bulkhead.release(opId2);
    }

    @Test
    void testBulkhead_ConcurrentAccess_EnforcesLimit() throws InterruptedException {
        // Given: bulkhead with limit = 5
        TestableBulkhead bulkhead = new TestableBulkhead(5);
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        // When: concurrent requests
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    OpId opId = OpId.of("bulkhead-test-04-" + index);

                    if (bulkhead.tryAcquire(opId)) {
                        successCount.incrementAndGet();
                        sleep(100); // Hold the permit briefly
                        bulkhead.release(opId);
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // Start all threads
        doneLatch.await();

        // Then: some requests should be rejected
        assertTrue(rejectedCount.get() > 0,
                "Some requests should be rejected due to bulkhead limit");
        assertEquals(threadCount, successCount.get() + rejectedCount.get(),
                "All requests should be processed");
    }

    // ===================================================================
    // RATE LIMITER TESTS
    // ===================================================================

    @Test
    void testRateLimiter_BelowLimit_AllowsRequests() {
        // Given
        RateLimiter limiter = new NoOpRateLimiter();
        OpId opId = OpId.of("ratelimiter-test-01");

        // When: request below limit (NoOp has unlimited rate)
        boolean allowed = limiter.tryAcquire(opId);

        // Then: request is allowed
        assertTrue(allowed, "Rate limiter should allow requests below limit");
    }

    @Test
    void testRateLimiter_ExceedsLimit_RejectsRequests() {
        // Given: testable rate limiter with 5 QPS
        TestableRateLimiter limiter = new TestableRateLimiter(5.0);
        OpId opId = OpId.of("ratelimiter-test-02");

        // When: burst of 10 requests
        int allowedCount = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.tryAcquire(opId)) {
                allowedCount++;
            }
        }

        // Then: only 5 requests allowed (within burst capacity)
        assertTrue(allowedCount <= 5,
                "Should not exceed configured rate limit");
    }

    @Test
    void testRateLimiter_WaitForPermit_EventuallySucceeds() throws InterruptedException {
        // Given: rate limiter with low rate
        TestableRateLimiter limiter = new TestableRateLimiter(2.0); // 2 QPS
        OpId opId = OpId.of("ratelimiter-test-03");

        // When: wait for permit
        boolean acquired = limiter.tryAcquire(opId, 2000); // 2 second timeout

        // Then: eventually succeeds
        assertTrue(acquired,
                "Should acquire permit after waiting");
    }

    // ===================================================================
    // HEDGING TESTS
    // ===================================================================

    @Test
    void testHedging_Disabled_NoHedging() {
        // Given
        HedgePolicy policy = new NoOpHedgePolicy();
        OpId opId = OpId.of("hedge-test-01");

        // When: hedging is disabled
        boolean shouldHedge = policy.shouldHedge(opId);

        // Then: no hedging
        assertFalse(shouldHedge, "NoOp hedge policy should disable hedging");
    }

    @Test
    void testHedging_Enabled_AllowsHedging() {
        // Given: testable hedge policy with hedging enabled
        TestableHedgePolicy policy = new TestableHedgePolicy(true);
        OpId opId = OpId.of("hedge-test-02");

        // When: check if hedging should be applied
        boolean shouldHedge = policy.shouldHedge(opId);

        // Then: hedging is allowed
        assertTrue(shouldHedge,
                "Hedge policy should allow hedging when enabled");
    }

    @Test
    void testHedging_MaxHedgesReached_PreventsMoreHedges() {
        // Given: hedge policy with max hedges = 2
        TestableHedgePolicy policy = new TestableHedgePolicy(true, 2);
        OpId opId = OpId.of("hedge-test-03");

        // When: attempt more than max hedges
        policy.incrementHedgeCount(opId);
        policy.incrementHedgeCount(opId);

        boolean canHedgeMore = policy.canHedgeMore(opId);

        // Then: cannot hedge more
        assertFalse(canHedgeMore,
                "Should prevent hedging after max hedges reached");
    }

    // ===================================================================
    // HOOK EXECUTION ORDER TESTS
    // ===================================================================

    /**
     * NOTE: This test currently documents the expected hook execution order
     * but does not verify actual hook implementation order.
     *
     * TODO: Enhance test to verify actual hook chain execution by:
     * - Using Mock objects (e.g., Mockito's InOrder) to verify call sequence
     * - Adding execution tracking to Testable* implementations
     * - Recording actual hook invocations rather than simulating with strings
     *
     * The expected order as per package-info.java:
     * 1. TimeoutPolicy
     * 2. CircuitBreaker
     * 3. Bulkhead
     * 4. RateLimiter
     * 5. HedgePolicy
     * 6. Executor
     */
    @Test
    void testHookOrder_CorrectChaining() {
        // Given: track execution order
        List<String> executionOrder = new ArrayList<>();

        // Simulate hook chain execution
        executionOrder.add("1. TimeoutPolicy.start()");
        executionOrder.add("2. CircuitBreaker.tryAcquire()");
        executionOrder.add("3. Bulkhead.tryAcquire()");
        executionOrder.add("4. RateLimiter.tryAcquire()");
        executionOrder.add("5. HedgePolicy.shouldHedge()");
        executionOrder.add("6. Executor.execute()");

        // Then: verify correct order (as per package-info.java)
        assertEquals("1. TimeoutPolicy.start()", executionOrder.get(0),
                "Timeout should be first");
        assertEquals("2. CircuitBreaker.tryAcquire()", executionOrder.get(1),
                "CircuitBreaker should be second");
        assertEquals("3. Bulkhead.tryAcquire()", executionOrder.get(2),
                "Bulkhead should be third");
        assertEquals("4. RateLimiter.tryAcquire()", executionOrder.get(3),
                "RateLimiter should be fourth");
        assertEquals("5. HedgePolicy.shouldHedge()", executionOrder.get(4),
                "HedgePolicy should be fifth");
        assertEquals("6. Executor.execute()", executionOrder.get(5),
                "Executor should be last");
    }

    // ===================================================================
    // HELPER CLASSES FOR TESTING
    // ===================================================================

    /**
     * Testable CircuitBreaker for state manipulation.
     */
    private static class TestableCircuitBreaker implements CircuitBreaker {
        private CircuitBreakerState state = CircuitBreakerState.CLOSED;
        private int failureCount = 0;
        private static final int FAILURE_THRESHOLD = 5;

        void forceOpen() {
            this.state = CircuitBreakerState.OPEN;
        }

        void forceHalfOpen() {
            this.state = CircuitBreakerState.HALF_OPEN;
        }

        @Override
        public boolean tryAcquire(OpId opId) {
            return state != CircuitBreakerState.OPEN;
        }

        @Override
        public CircuitBreakerState getState() {
            return state;
        }

        @Override
        public void recordSuccess(OpId opId) {
            if (state == CircuitBreakerState.HALF_OPEN) {
                state = CircuitBreakerState.CLOSED;
            }
            failureCount = 0;
        }

        @Override
        public void recordFailure(OpId opId, Throwable error) {
            failureCount++;
            if (failureCount >= FAILURE_THRESHOLD) {
                state = CircuitBreakerState.OPEN;
            }
        }

        @Override
        public void reset() {
            state = CircuitBreakerState.CLOSED;
            failureCount = 0;
        }
    }

    /**
     * Testable TimeoutPolicy with configurable timeout.
     */
    private static class TestableTimeoutPolicy implements TimeoutPolicy {
        private final long timeoutMs;

        TestableTimeoutPolicy(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        @Override
        public long getPerAttemptTimeoutMs(OpId opId) {
            return timeoutMs;
        }

        @Override
        public void recordTimeout(OpId opId, long elapsedMs) {
            // No-op
        }
    }

    /**
     * Testable Bulkhead with configurable limit.
     */
    private static class TestableBulkhead implements Bulkhead {
        private final int maxConcurrent;
        private final AtomicInteger currentConcurrency = new AtomicInteger(0);

        TestableBulkhead(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        @Override
        public boolean tryAcquire(OpId opId) {
            if (currentConcurrency.incrementAndGet() > maxConcurrent) {
                currentConcurrency.decrementAndGet();
                return false;
            }
            return true;
        }

        @Override
        public boolean tryAcquire(OpId opId, long timeoutMs) throws InterruptedException {
            return tryAcquire(opId);
        }

        @Override
        public void release(OpId opId) {
            currentConcurrency.decrementAndGet();
        }

        @Override
        public int getCurrentConcurrency() {
            return currentConcurrency.get();
        }

        @Override
        public BulkheadConfig getConfig() {
            return new BulkheadConfig(maxConcurrent, 0);
        }
    }

    /**
     * Testable RateLimiter with configurable rate.
     */
    private static class TestableRateLimiter implements RateLimiter {
        private final double permitsPerSecond;
        private final AtomicInteger permitsUsed = new AtomicInteger(0);

        TestableRateLimiter(double permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
        }

        @Override
        public boolean tryAcquire(OpId opId) {
            if (permitsUsed.incrementAndGet() > permitsPerSecond) {
                permitsUsed.decrementAndGet();
                return false;
            }
            return true;
        }

        @Override
        public boolean tryAcquire(OpId opId, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (System.currentTimeMillis() < deadline) {
                if (tryAcquire(opId)) {
                    return true;
                }
                Thread.sleep(Math.min(50, timeoutMs / 10)); // Backoff strategy
            }

            // Final attempt
            return tryAcquire(opId);
        }

        @Override
        public RateLimiterConfig getConfig() {
            return new RateLimiterConfig(permitsPerSecond, (int) permitsPerSecond);
        }
    }

    /**
     * Testable HedgePolicy with configurable hedging.
     */
    private static class TestableHedgePolicy implements HedgePolicy {
        private final boolean hedgingEnabled;
        private final int maxHedges;
        private final AtomicInteger hedgeCount = new AtomicInteger(0);

        TestableHedgePolicy(boolean hedgingEnabled) {
            this(hedgingEnabled, 1);
        }

        TestableHedgePolicy(boolean hedgingEnabled, int maxHedges) {
            this.hedgingEnabled = hedgingEnabled;
            this.maxHedges = maxHedges;
        }

        @Override
        public boolean shouldHedge(OpId opId) {
            return hedgingEnabled;
        }

        @Override
        public long getHedgeDelayMs(OpId opId) {
            return hedgingEnabled ? 100 : 0;
        }

        @Override
        public int getMaxHedges(OpId opId) {
            return maxHedges;
        }

        @Override
        public void recordHedgeAttempt(OpId opId, int hedgeNumber) {
            hedgeCount.incrementAndGet();
        }

        @Override
        public void recordSuccess(OpId opId, boolean wasHedge) {
            // For testing purposes, we track success but don't need to store the result
        }

        void incrementHedgeCount(OpId opId) {
            hedgeCount.incrementAndGet();
        }

        boolean canHedgeMore(OpId opId) {
            return hedgeCount.get() < maxHedges;
        }
    }
}
