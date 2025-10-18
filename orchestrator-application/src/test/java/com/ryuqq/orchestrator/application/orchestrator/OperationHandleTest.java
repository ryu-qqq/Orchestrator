package com.ryuqq.orchestrator.application.orchestrator;

import com.ryuqq.orchestrator.core.model.OpId;
import com.ryuqq.orchestrator.core.outcome.Fail;
import com.ryuqq.orchestrator.core.outcome.Ok;
import com.ryuqq.orchestrator.core.outcome.Outcome;
import com.ryuqq.orchestrator.core.outcome.Retry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OperationHandle 유닛 테스트.
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
class OperationHandleTest {

    @Test
    void completed_핸들_생성_Ok_결과() {
        // given
        OpId opId = OpId.of("test-op-id-ok");
        Outcome outcome = new Ok(opId, "Success message");

        // when
        OperationHandle handle = OperationHandle.completed(opId, outcome);

        // then
        assertThat(handle.getOpId()).isEqualTo(opId);
        assertThat(handle.isCompletedFast()).isTrue();
        assertThat(handle.getResponseBodyOrNull()).isEqualTo(outcome);
        assertThat(handle.getStatusUrlOrNull()).isNull();
    }

    @Test
    void completed_핸들_생성_Retry_결과() {
        // given
        OpId opId = OpId.of("test-op-id-retry");
        Outcome outcome = new Retry("Temporary failure", 1, 5000);

        // when
        OperationHandle handle = OperationHandle.completed(opId, outcome);

        // then
        assertThat(handle.getOpId()).isEqualTo(opId);
        assertThat(handle.isCompletedFast()).isTrue();
        assertThat(handle.getResponseBodyOrNull()).isEqualTo(outcome);
        assertThat(handle.getStatusUrlOrNull()).isNull();
    }

    @Test
    void completed_핸들_생성_Fail_결과() {
        // given
        OpId opId = OpId.of("test-op-id-fail");
        Outcome outcome = Fail.of("ERR_001", "Permanent failure");

        // when
        OperationHandle handle = OperationHandle.completed(opId, outcome);

        // then
        assertThat(handle.getOpId()).isEqualTo(opId);
        assertThat(handle.isCompletedFast()).isTrue();
        assertThat(handle.getResponseBodyOrNull()).isEqualTo(outcome);
        assertThat(handle.getStatusUrlOrNull()).isNull();
    }

    @Test
    void async_핸들_생성() {
        // given
        OpId opId = OpId.of("test-op-id-async");
        String statusUrl = "/api/operations/test-op-id-async/status";

        // when
        OperationHandle handle = OperationHandle.async(opId, statusUrl);

        // then
        assertThat(handle.getOpId()).isEqualTo(opId);
        assertThat(handle.isCompletedFast()).isFalse();
        assertThat(handle.getResponseBodyOrNull()).isNull();
        assertThat(handle.getStatusUrlOrNull()).isEqualTo(statusUrl);
    }

    @Test
    void completed_핸들_생성_실패_null_opId() {
        // given
        OpId tempOpId = OpId.of("temp-id");
        Outcome outcome = new Ok(tempOpId, "Success");

        // when & then
        assertThatThrownBy(() -> OperationHandle.completed(null, outcome))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("opId cannot be null");
    }

    @Test
    void completed_핸들_생성_실패_null_outcome() {
        // given
        OpId opId = OpId.of("test-op-id");

        // when & then
        assertThatThrownBy(() -> OperationHandle.completed(opId, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outcome cannot be null");
    }

    @Test
    void async_핸들_생성_실패_null_opId() {
        // given
        String statusUrl = "/api/operations/123/status";

        // when & then
        assertThatThrownBy(() -> OperationHandle.async(null, statusUrl))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("opId cannot be null");
    }

    @Test
    void async_핸들_생성_실패_null_statusUrl() {
        // given
        OpId opId = OpId.of("test-op-id");

        // when & then
        assertThatThrownBy(() -> OperationHandle.async(opId, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("statusUrl cannot be null");
    }

    @Test
    void async_핸들_생성_실패_blank_statusUrl() {
        // given
        OpId opId = OpId.of("test-op-id");

        // when & then
        assertThatThrownBy(() -> OperationHandle.async(opId, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("statusUrl cannot be null or blank");
    }

    @Test
    void toString_completed() {
        // given
        OpId opId = OpId.of("test-op-id");
        Outcome outcome = new Ok(opId, "Success");
        OperationHandle handle = OperationHandle.completed(opId, outcome);

        // when
        String result = handle.toString();

        // then
        assertThat(result).contains("opId=OpId{test-op-id}");
        assertThat(result).contains("completed=true");
        assertThat(result).contains("outcome=");
    }

    @Test
    void toString_async() {
        // given
        OpId opId = OpId.of("test-op-id");
        String statusUrl = "/api/operations/test-op-id/status";
        OperationHandle handle = OperationHandle.async(opId, statusUrl);

        // when
        String result = handle.toString();

        // then
        assertThat(result).contains("opId=OpId{test-op-id}");
        assertThat(result).contains("completed=false");
        assertThat(result).contains("statusUrl=/api/operations/test-op-id/status");
    }
}
