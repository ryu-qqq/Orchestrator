package com.ryuqq.orchestrator.adapter.runner;

import com.ryuqq.orchestrator.application.orchestrator.OperationHandle;
import com.ryuqq.orchestrator.core.contract.Command;
import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.executor.Executor;
import com.ryuqq.orchestrator.core.model.*;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;  // doAnswer, doNothing, when 등 모두 포함

/**
 * InlineFastPathRunner 멀티스레드 안전성 테스트.
 *
 * <p>Stateless 설계의 thread-safety를 검증합니다:</p>
 * <ul>
 *   <li>동시 다발적인 submit 호출</li>
 *   <li>Race condition 및 데이터 경합 검증</li>
 *   <li>상태 공유 없음 검증</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class InlineFastPathRunnerConcurrentTest {

    @Mock
    private Executor executor;

    private InlineFastPathRunner runner;
    private Command testCommand;

    @BeforeEach
    void setUp() {
        runner = new InlineFastPathRunner(executor);
        testCommand = new Command(
            Domain.of("TEST_DOMAIN"),
            EventType.of("CREATE"),
            BizKey.of("biz-123"),
            IdemKey.of("idem-456"),
            Payload.of("{\"data\": \"test\"}")
        );
    }

    @Test
    void submit_10개_스레드_동시_호출_시_모두_정상_처리() throws InterruptedException {
        // given
        int threadCount = 10;
        long timeBudgetMs = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<OperationHandle>> futures = new ArrayList<>();

        // Executor 모킹: any()를 사용하여 어떤 OpId도 허용
        doNothing().when(executor).execute(any());
        when(executor.getState(any())).thenReturn(OperationState.COMPLETED);
        when(executor.getOutcome(any())).thenAnswer(invocation -> {
            OpId opId = invocation.getArgument(0);
            return new Ok(opId, "Success-" + opId.getValue());
        });

        // when - 10개 스레드에서 동시에 submit 호출
        for (int i = 0; i < threadCount; i++) {
            Future<OperationHandle> future = executorService.submit(() -> {
                latch.countDown();
                try {
                    latch.await();  // 모든 스레드가 준비될 때까지 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return runner.submit(testCommand, timeBudgetMs);
            });
            futures.add(future);
        }

        // then - 모든 호출이 성공적으로 완료
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        for (Future<OperationHandle> future : futures) {
            try {
                OperationHandle handle = future.get();
                assertThat(handle.isCompletedFast()).isTrue();
                assertThat(handle.getResponseBodyOrNull()).isNotNull();
            } catch (ExecutionException e) {
                throw new AssertionError("Thread execution failed", e);
            }
        }
    }

    @RepeatedTest(3)  // Race condition 검증을 위해 3회 반복
    void submit_50개_스레드_동시_호출_테스트() throws InterruptedException {
        // given
        int threadCount = 50;
        long timeBudgetMs = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);

        // Executor 모킹: any()를 사용하여 모든 OpId 허용
        doNothing().when(executor).execute(any());
        when(executor.getState(any())).thenReturn(OperationState.COMPLETED);
        when(executor.getOutcome(any())).thenAnswer(invocation -> {
            OpId opId = invocation.getArgument(0);
            return new Ok(opId, "Success");
        });

        // when - 50개 스레드에서 동시에 submit 호출
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드가 동시 시작
                    OperationHandle handle = runner.submit(testCommand, timeBudgetMs);
                    if (handle.isCompletedFast()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 예외 발생 시 실패로 처리
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 시작
        completionLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then - 모든 요청이 성공
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    @Test
    void submit_다양한_timeBudget으로_동시_호출() throws InterruptedException, ExecutionException, TimeoutException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Long> timeBudgets = List.of(50L, 100L, 200L, 500L, 1000L, 2000L, 3000L, 4000L, 5000L);
        List<Future<OperationHandle>> futures = new ArrayList<>();

        // Executor 모킹: any()를 사용하여 모든 OpId 허용
        doNothing().when(executor).execute(any());
        when(executor.getState(any())).thenReturn(OperationState.COMPLETED);
        when(executor.getOutcome(any())).thenAnswer(invocation -> {
            OpId opId = invocation.getArgument(0);
            return new Ok(opId, "Success");
        });

        // when - 각기 다른 timeBudget으로 동시 호출
        for (Long timeBudget : timeBudgets) {
            Future<OperationHandle> future = executorService.submit(
                () -> runner.submit(testCommand, timeBudget)
            );
            futures.add(future);
        }

        // then - 모든 호출이 성공 (timeBudget이 달라도 독립적 처리)
        for (Future<OperationHandle> future : futures) {
            OperationHandle handle = future.get(10, TimeUnit.SECONDS);
            assertThat(handle.isCompletedFast()).isTrue();
        }

        executorService.shutdown();
    }

    @Test
    void submit_인터럽트_발생_시_RuntimeException_전파() {
        // given
        long timeBudgetMs = 200;

        // Executor가 계속 IN_PROGRESS 반환 (타임아웃까지 폴링)
        doAnswer(invocation -> {
            Envelope envelope = invocation.getArgument(0, Envelope.class);
            OpId opId = envelope.opId();
            when(executor.getState(opId)).thenReturn(OperationState.IN_PROGRESS);
            return null;
        }).when(executor).execute(any());

        // when - 실행 중 스레드 인터럽트
        Thread testThread = new Thread(() -> {
            try {
                // 약간의 딜레이 후 인터럽트 받을 준비
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try {
                Thread.currentThread().interrupt();  // 스스로 인터럽트
                runner.submit(testCommand, timeBudgetMs);
            } catch (RuntimeException e) {
                // 인터럽트 발생 시 RuntimeException 전파 확인
                assertThat(e.getMessage()).contains("Polling interrupted");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            }
        });

        testThread.start();
        try {
            testThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // then - 스레드가 정상 종료됨
        assertThat(testThread.isAlive()).isFalse();
    }

    @Test
    void submit_Stateless_설계_검증_서로_다른_인스턴스_독립성() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        InlineFastPathRunner runner1 = new InlineFastPathRunner(executor, 10);
        InlineFastPathRunner runner2 = new InlineFastPathRunner(executor, 20);
        long timeBudgetMs = 200;

        // Executor 모킹: any()를 사용하여 모든 OpId 허용
        doNothing().when(executor).execute(any());
        when(executor.getState(any())).thenReturn(OperationState.COMPLETED);
        when(executor.getOutcome(any())).thenAnswer(invocation -> {
            OpId opId = invocation.getArgument(0);
            return new Ok(opId, "Success");
        });

        // when - 서로 다른 인스턴스에서 동시 호출
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Future<OperationHandle> future1 = executorService.submit(() -> runner1.submit(testCommand, timeBudgetMs));
        Future<OperationHandle> future2 = executorService.submit(() -> runner2.submit(testCommand, timeBudgetMs));

        // then - 두 인스턴스 모두 독립적으로 정상 동작
        OperationHandle handle1 = future1.get(5, TimeUnit.SECONDS);
        OperationHandle handle2 = future2.get(5, TimeUnit.SECONDS);

        assertThat(handle1.isCompletedFast()).isTrue();
        assertThat(handle2.isCompletedFast()).isTrue();
        assertThat(handle1.getOpId()).isNotEqualTo(handle2.getOpId());  // 서로 다른 OpId 생성

        executorService.shutdown();
    }
}
