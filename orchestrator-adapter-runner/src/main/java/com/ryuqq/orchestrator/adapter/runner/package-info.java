/**
 * Runner Adapter Layer - Orchestrator 구현체.
 *
 * <p>이 패키지는 Orchestrator 인터페이스의 구체적인 구현체들을 포함합니다.</p>
 *
 * <h2>구현체</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.adapter.runner.InlineFastPathRunner} - Fast-Path 동기/비동기 분기 러너</li>
 * </ul>
 *
 * <h2>아키텍처 위치</h2>
 * <pre>
 * adapter-runner (InlineFastPathRunner)
 *   ↓ implements
 * application (Orchestrator interface)
 *   ↓ depends on
 * core (OpId, Command, Envelope, Outcome, OperationState)
 *   ↓ depends on
 * core/executor (Executor interface)
 * </pre>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
package com.ryuqq.orchestrator.adapter.runner;
