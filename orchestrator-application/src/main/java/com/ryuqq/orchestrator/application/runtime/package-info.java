/**
 * Runtime 인터페이스 및 관련 컴포넌트.
 *
 * <p>이 패키지는 비동기 작업 처리를 위한 Runtime 인터페이스를 제공합니다.</p>
 *
 * <p><strong>핵심 컴포넌트:</strong></p>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.application.runtime.Runtime} - 메시지 큐 폴링 및 작업 처리 인터페이스</li>
 * </ul>
 *
 * <p><strong>구현체:</strong></p>
 * <p>구현체는 adapter-runner 모듈의 {@code QueueWorkerRunner}에서 제공됩니다.</p>
 *
 * @since 1.0.0
 */
package com.ryuqq.orchestrator.application.runtime;
