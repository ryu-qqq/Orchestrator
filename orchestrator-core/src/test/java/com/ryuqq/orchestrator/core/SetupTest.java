package com.ryuqq.orchestrator.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로젝트 세팅 검증 테스트
 */
class SetupTest {

    @Test
    void projectSetupShouldWork() {
        // Given
        String expected = "Orchestrator Core SDK";

        // When
        String actual = "Orchestrator Core SDK";

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void javaVersionShouldBe21() {
        // Given
        String javaVersion = System.getProperty("java.version");

        // Then
        assertThat(javaVersion).startsWith("21");
    }
}
