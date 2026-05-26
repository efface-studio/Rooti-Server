package com.rooti.domain.document.application.journal;

import com.rooti.domain.document.application.journal.JournalDocument.Header;
import com.rooti.domain.document.application.journal.JournalDocument.NextPlan;
import com.rooti.domain.document.application.journal.JournalDocument.ProcessRow;
import com.rooti.domain.schedule.application.WorkScheduleService;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.workrecord.application.WorkRecordService;
import com.rooti.domain.workrecord.presentation.dto.ProcessResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄 ID → 표현-무관 JournalDocument 변환을 책임지는 단일 진입점.
 *
 * <p>도메인 객체(WorkSchedule / WorkProcessRecord / Leave 등) 를 사람이 읽을 수 있는 라벨 +
 * 정규화된 표시 형식으로 한 번만 가공한 뒤, 렌더러들에 깔끔하게 전달합니다. 이 단계가 비어 있으면
 * 렌더러 3개에 동일한 가공 로직이 흩어지게 되므로 — '회사명 한 줄' 만 바꿔도 PDF · HWP · XLSX
 * 모두를 따라가야 하는 상황이 됩니다. 어셈블러를 분리해 그 위험을 차단합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalDocumentAssembler {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkScheduleService scheduleService;
    private final WorkRecordService recordService;
    private final LeaveProvider leaveProvider;

    public JournalDocument assemble(long scheduleId) {
        WorkSchedule schedule = scheduleService.getOrThrow(scheduleId);
        List<ProcessResponse> processes = recordService.listProcess(scheduleId);
        LocalDate date = schedule.getStartAt() == null ? LocalDate.now() : schedule.getStartAt().toLocalDate();

        Header header =
                new Header(
                        schedule.getJobStandard().getCompany().getName(),
                        date,
                        formatDateKor(schedule.getStartAt()),
                        schedule.getJobStandard().getName(),
                        schedule.getJobWorker().getCompanyWorker().getWorker().getUser().getName(),
                        defaultWorkerRank());

        List<ProcessRow> rows =
                processes.stream()
                        .filter(p -> p.startAt() != null && p.endAt() != null)
                        .sorted(Comparator.comparing(ProcessResponse::startAt))
                        .map(JournalDocumentAssembler::toRow)
                        .toList();

        long workerId =
                schedule.getJobWorker().getCompanyWorker().getWorker().getId();
        var leaves = leaveProvider.approvedLeavesOn(workerId, date);

        List<NextPlan> plans =
                List.of(new NextPlan(schedule.getJobStandard().getName(), null, ""));

        return new JournalDocument(header, rows, leaves, /* remarks */ "", plans);
    }

    // ----- helpers ----------------------------------------------------------
    private static ProcessRow toRow(ProcessResponse p) {
        long minutes = Math.max(1, Duration.between(p.startAt(), p.endAt()).toMinutes());
        return new ProcessRow(
                HM.format(p.startAt()),
                HM.format(p.endAt()),
                nullToEmpty(p.jobProcessName()),
                minutes,
                nullToEmpty(p.endAnswer()));
    }

    /**
     * 한국어 일자 표시 — 사용자가 회사에 받아오던 기존 paper-form 의 표기를 그대로 따릅니다
     * (예: {@code "2026년 05월 26일 (화요일)"}).
     */
    private static String formatDateKor(LocalDateTime dt) {
        if (dt == null) return "";
        String dow = dt.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
        return String.format(
                "%d년 %02d월 %02d일 (%s)",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(), dow);
    }

    /**
     * 직급은 현재 도메인에 별도 컬럼이 없어 기본값을 채워둡니다. hire 정보(직급/직책)가 들어오면
     * Worker → hire 를 통해 가져오도록 한 곳만 수정하면 됩니다.
     */
    private static String defaultWorkerRank() {
        return "사무보조사원";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
