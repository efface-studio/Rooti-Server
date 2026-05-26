package com.rooti.domain.schedule.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 스케줄 종료 이벤트의 첫 리스너 — 감사용 로그를 남깁니다.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} 으로 동작하므로 트랜잭션이 실제로
 * 커밋된 뒤에만 실행됩니다. 트랜잭션이 롤백되면 이 리스너는 호출되지 않아, "DB 에는 close 가
 * 안 됐는데 부수 효과만 발생" 하는 일관성 깨짐을 방지합니다.
 *
 * <p>새 리스너(알림 / 정산 / 일지 자동 메일 등) 가 같은 이벤트를 받고 싶다면 똑같이
 * {@code @TransactionalEventListener} 컴포넌트 하나만 추가하면 됩니다. 스케줄 도메인은 모름.
 */
@Slf4j
@Component
public class WorkScheduleClosedAuditLogger {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClosed(WorkScheduleClosedEvent event) {
        log.info(
                "[schedule:closed] scheduleId={} workerId={} jobStandardId={} closedAt={}",
                event.scheduleId(),
                event.workerId(),
                event.jobStandardId(),
                event.closedAt());
    }
}
