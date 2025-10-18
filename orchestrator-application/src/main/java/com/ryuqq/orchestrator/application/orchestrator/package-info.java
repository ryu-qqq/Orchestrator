/**
 * Orchestrator Application Layer - Operation 실행 조정 API.
 *
 * <p>이 패키지는 Orchestrator Core SDK의 핵심 애플리케이션 레이어로,
 * 클라이언트 요청을 수락하고 Fast-Path 동기/비동기 분기를 담당합니다.</p>
 *
 * <h2>핵심 인터페이스</h2>
 * <ul>
 *   <li>{@link com.ryuqq.orchestrator.application.orchestrator.Orchestrator} - Operation 실행 조정자</li>
 *   <li>{@link com.ryuqq.orchestrator.application.orchestrator.OperationHandle} - 실행 결과 핸들</li>
 * </ul>
 *
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li><strong>헥사고날 아키텍처:</strong> 포트(인터페이스)와 어댑터 분리</li>
 *   <li><strong>의존성 역전:</strong> 구현체는 adapter-runner 모듈에 위치</li>
 *   <li><strong>불변성:</strong> OperationHandle은 불변 객체</li>
 * </ul>
 *
 * @author Orchestrator Team
 * @since 1.0.0
 */
package com.ryuqq.orchestrator.application.orchestrator;
