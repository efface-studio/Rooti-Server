package com.rooti.domain.document.application.journal;

import com.rooti.domain.document.application.journal.JournalDocument.LeaveEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 근로자 ID + 날짜로 그 날짜에 겹치는 <strong>승인된 휴가</strong>를 돌려주는 출처.
 *
 * <p>일지 어셈블러는 이 인터페이스만 알면 되고, 실제 휴가 도메인이 서버에 들어오기 전까지는
 * {@link NoopLeaveProvider} 가 빈 리스트를 돌려줍니다. Leave 엔티티가 도입되면 새 구현체
 * (예: {@code JpaLeaveProvider}) 를 빈으로 노출하기만 하면 어셈블러 / 렌더러는 변경 없이 휴가가
 * 일지에 반영됩니다.
 */
public interface LeaveProvider {

    /**
     * @param workerId 근로자 PK
     * @param date 일지 대상일
     * @return 해당 일자에 시작·종료 구간이 겹치는 승인된 휴가들. 빈 리스트일 수 있습니다.
     */
    List<LeaveEntry> approvedLeavesOn(long workerId, LocalDate date);

    /** 기본 구현 — 휴가 도메인이 서버에 들어오기 전까지 빈 리스트를 돌려줍니다. */
    @Component
    class NoopLeaveProvider implements LeaveProvider {
        @Override
        public List<LeaveEntry> approvedLeavesOn(long workerId, LocalDate date) {
            return List.of();
        }
    }
}
