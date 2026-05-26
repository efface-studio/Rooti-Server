package com.rooti.domain.schedule.application.event;

import java.time.LocalDateTime;

/**
 * 한 근로자의 근무 스케줄이 종료(close) 되었을 때 발행되는 도메인 이벤트.
 *
 * <p>스케줄 도메인이 다른 도메인(알림 / 근무일지 / 정산 등) 을 직접 호출하면 양방향 의존이 생겨
 * 경계가 흐려집니다. 그 대신 close 트랜잭션 commit 직후 이 이벤트를 발행하고, 관심 있는 도메인이
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 으로 비동기 처리합니다. 우아한형제들 / 카카오 /
 * 당근 등 큰 회사 기술 블로그가 공통적으로 권장하는 패턴입니다.
 *
 * <p>현재 등록된 리스너는 한 건이지만(아래 참조), 알림·정산 등이 추후 합류해도 스케줄 도메인의
 * 코드는 변경되지 않습니다.
 *
 * @see com.rooti.domain.schedule.application.event.WorkScheduleClosedAuditLogger
 */
public record WorkScheduleClosedEvent(
        long scheduleId, long workerId, long jobStandardId, LocalDateTime closedAt) {}
