/**
 * Executor Domain Service - Operation 실행 관리.
 *
 * <p>이 패키지는 Orchestrator Core의 도메인 서비스로,
 * Operation의 비동기 실행, 상태 관리, 결과 조회를 담당합니다.</p>
 *
 * <h2>핵심 인터페이스</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.core.executor.Executor} - Operation 실행자</li>
 * </ul>
 *
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li><strong>비블로킹 실행:</strong> execute()는 즉시 반환</li>
 *   <li><strong>Thread-Safe:</strong> 동시 상태 조회 지원</li>
 *   <li><strong>상태 기반:</strong> OperationState에 따라 Outcome 조회 가능</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
package com.ryuqq.orchestrator.core.executor;
