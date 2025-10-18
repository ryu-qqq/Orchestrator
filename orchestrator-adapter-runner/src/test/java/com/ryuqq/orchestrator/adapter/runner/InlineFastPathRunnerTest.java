package com.ryuqq.orchestrator.adapter.runner;

import com.ryuqq.orchestrator.application.orchestrator.OperationHandle;
import com.ryuqq.orchestrator.core.contract.Command;
import com.ryuqq.orchestrator.core.contract.Envelope;
import com.ryuqq.orchestrator.core.executor.Executor;
import com.ryuqq.orchestrator.core.model.*;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.statemachine.OperationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * InlineFastPathRunner 유닛 테스트.
 *
 * <p>Fast-Path 폴링 메커니즘의 핵심 동작을 검증합니다:</p>
 * <ul>
 *   <li>timeBudget 내 완료 시 200 OK 응답</li>
 *   <li>timeBudget 초과 시 202 Accepted 응답</li>
 *   <li>경계값 테스트 (50ms, 5000ms)</li>
 *   <li>입력 유효성 검증</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class InlineFastPathRunnerTest {

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

    // ============================================================
    // 1. Fast-Path 완료 테스트 (200 OK)
    // ============================================================

    @Test
    void submit_작업이_timeBudget_내_완료_시_200_응답과_결과_반환() {
        // given
        long timeBudgetMs = 200;
        ArgumentCaptor<Envelope> envelopeCaptor = ArgumentCaptor.forClass(Envelope.class);

        // Executor가 즉시 COMPLETED 상태를 반환하도록 모킹
        doAnswer(invocation -> {
            Envelope envelope = invocation.getArgument(0, Envelope.class);
            OpId opId = envelope.opId();
            when(executor.getState(opId)).thenReturn(OperationState.COMPLETED);
            when(executor.getOutcome(opId)).thenReturn(new Ok(opId, "Success"));
            return null;
        }).when(executor).execute(any());

        // when
        OperationHandle handle = runner.submit(testCommand, timeBudgetMs);

        // then
        assertThat(handle.isCompletedFast()).isTrue();
        assertThat(handle.getResponseBodyOrNull()).isNotNull();
        assertThat(handle.getResponseBodyOrNull()).isInstanceOf(Ok.class);
        assertThat(handle.getStatusUrlOrNull()).isNull();

        // Executor 호출 검증
        verify(executor).execute(any());
        verify(executor, atLeastOnce()).getState(any());
        verify(executor).getOutcome(any());
    }

    @Test
    void submit_작업이_폴링_2회차에_완료_시_200_응답() {
        // given
        long timeBudgetMs = 200;

        // 첫 번째 폴링: IN_PROGRESS, 두 번째 폴링: COMPLETED
        doNothing().when(executor).execute(any());

        when(executor.getState(any()))
            .thenReturn(OperationState.IN_PROGRESS)  // 첫 번째 폴링
            .thenReturn(OperationState.COMPLETED);    // 두 번째 폴링

        when(executor.getOutcome(any())).thenAnswer(invocation -> {
            OpId opId = invocation.getArgument(0);
            return new Ok(opId, "Success after 2 polls");
        });

        // when
        OperationHandle handle = runner.submit(testCommand, timeBudgetMs);

        // then
        assertThat(handle.isCompletedFast()).isTrue();
        assertThat(handle.getResponseBodyOrNull()).isNotNull();
        assertThat(((Ok) handle.getResponseBodyOrNull()).message()).isEqualTo("Success after 2 polls");

        // 최소 2회 이상 폴링했는지 확인
        verify(executor, atLeast(2)).getState(any());
    }

    @Test
    void submit_즉시_완료_시_폴링_1회만_수행() {
        // given
        long timeBudgetMs = 200;

        // Executor가 execute 후 즉시 COMPLETED 상태 반환
        doAnswer(invocation -> {
            Envelope envelope = invocation.getArgument(0, Envelope.class);
            OpId opId = envelope.opId();
            when(executor.getState(opId)).thenReturn(OperationState.COMPLETED);
            when(executor.getOutcome(opId)).thenReturn(new Ok(opId, "Instant success"));
            return null;
        }).when(executor).execute(any());

        // when
        OperationHandle handle = runner.submit(testCommand, timeBudgetMs);

        // then
        assertThat(handle.isCompletedFast()).isTrue();

        // 정확히 1회만 폴링했는지 확인
        verify(executor, times(1)).getState(any());
    }

    // ============================================================
    // 2. Fast-Path 타임아웃 테스트 (202 Accepted)
    // ============================================================

    @Test
    void submit_작업이_timeBudget_초과_시_202_응답과_statusUrl_반환() {
        // given
        long timeBudgetMs = 50;  // 매우 짧은 timeBudget

        // Executor가 계속 IN_PROGRESS 상태를 반환 (완료되지 않음)
        doNothing().when(executor).execute(any());
        when(executor.getState(any())).thenReturn(OperationState.IN_PROGRESS);

        // when
        OperationHandle handle = runner.submit(testCommand, timeBudgetMs);

        // then
        assertThat(handle.isCompletedFast()).isFalse();
        assertThat(handle.getResponseBodyOrNull()).isNull();
        assertThat(handle.getStatusUrlOrNull()).isNotNull();
        assertThat(handle.getStatusUrlOrNull()).matches("/api/operations/.+/status");

        // Executor는 execute만 호출되고, getOutcome은 호출되지 않음
        verify(executor).execute(any());
        verify(executor, atLeastOnce()).getState(any());
        verify(executor, never()).getOutcome(any());
    }

    @Test
    void submit_작업이_PENDING_상태에서_타임아웃_시_202_응답() {
        // given
        long timeBudgetMs = 50;

        // Executor가 PENDING 상태만 반환 (아직 시작도 안 함)
        doNothing().when(executor).execute(any());
        when(executor.getState(any())).thenReturn(OperationState.PENDING);

        // when
        OperationHandle handle = runner.submit(testCommand, timeBudgetMs);

        // then
        assertThat(handle.isCompletedFast()).isFalse();
        assertThat(handle.getStatusUrlOrNull()).isNotNull();
    }

    @Test
    void submit_statusUrl_형식_검증() {
        // given
        long timeBudgetMs = 50;
        ArgumentCaptor<Envelope> envelopeCaptor = ArgumentCaptor.forClass(Envelope.class);

        // Executor가 계속 IN_PROGRESS
        doNothing().when(executor).execute(envelopeCaptor.capture());
        when(executor.getState(any())).thenReturn(OperationState.IN_PROGRESS);

        // when
        OperationHandle handle = runner.submit(testCommand, timeBudgetMs);

        // then
        OpId capturedOpId = envelopeCaptor.getValue().opId();
        String expectedUrl = "/api/operations/" + capturedOpId.getValue() + "/status";
        assertThat(handle.getStatusUrlOrNull()).isEqualTo(expectedUrl);
    }

    // ============================================================
    // 3. 생성자 테스트
    // ============================================================

    @Test
    void 생성자_executor가_null이면_예외_발생() {
        // when & then
        assertThatThrownBy(() -> new InlineFastPathRunner(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executor cannot be null");
    }

    @Test
    void 생성자_pollingIntervalMs가_0이면_예외_발생() {
        // when & then
        assertThatThrownBy(() -> new InlineFastPathRunner(executor, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pollingIntervalMs must be positive");
    }

    @Test
    void 생성자_pollingIntervalMs가_음수면_예외_발생() {
        // when & then
        assertThatThrownBy(() -> new InlineFastPathRunner(executor, -10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pollingIntervalMs must be positive");
    }

    @Test
    void 생성자_커스텀_pollingIntervalMs로_정상_생성() {
        // when
        InlineFastPathRunner customRunner = new InlineFastPathRunner(executor, 5);

        // then
        assertThat(customRunner).isNotNull();
    }

    // ============================================================
    // 3. 입력 유효성 검증 테스트
    // ============================================================

    @Test
    void submit_command가_null이면_예외_발생() {
        // when & then
        assertThatThrownBy(() -> runner.submit(null, 200))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("command cannot be null");
    }

    @Test
    void submit_timeBudget이_최소값_미만이면_예외_발생() {
        // when & then
        assertThatThrownBy(() -> runner.submit(testCommand, 49))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeBudgetMs must be between 50 and 5000 ms");
    }

    @Test
    void submit_timeBudget이_최대값_초과하면_예외_발생() {
        // when & then
        assertThatThrownBy(() -> runner.submit(testCommand, 5001))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeBudgetMs must be between 50 and 5000 ms");
    }

    @Test
    void submit_timeBudget이_정확히_50ms면_정상_실행() {
        // given
        doAnswer(invocation -> {
            Envelope envelope = invocation.getArgument(0, Envelope.class);
            OpId opId = envelope.opId();
            when(executor.getState(opId)).thenReturn(OperationState.COMPLETED);
            when(executor.getOutcome(opId)).thenReturn(new Ok(opId, "Success"));
            return null;
        }).when(executor).execute(any());

        // when
        OperationHandle handle = runner.submit(testCommand, 50);

        // then
        assertThat(handle).isNotNull();
        verify(executor).execute(any());
    }

    @Test
    void submit_timeBudget이_정확히_5000ms면_정상_실행() {
        // given
        doAnswer(invocation -> {
            Envelope envelope = invocation.getArgument(0, Envelope.class);
            OpId opId = envelope.opId();
            when(executor.getState(opId)).thenReturn(OperationState.COMPLETED);
            when(executor.getOutcome(opId)).thenReturn(new Ok(opId, "Success"));
            return null;
        }).when(executor).execute(any());

        // when
        OperationHandle handle = runner.submit(testCommand, 5000);

        // then
        assertThat(handle).isNotNull();
        verify(executor).execute(any());
    }
}
