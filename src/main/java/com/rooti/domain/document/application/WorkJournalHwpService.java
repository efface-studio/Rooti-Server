package com.rooti.domain.document.application;

import com.rooti.domain.schedule.application.WorkScheduleService;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.workrecord.application.WorkRecordService;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.ProcessResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근무일지 HWP 렌더러.
 *
 * <p>관공서 제출 양식을 위해 .hwp / .hwpx 가 필요합니다. 정식 hwpx 라이브러리(예: 한컴 SDK,
 * {@code hwplib}) 도입 전까지는 <strong>한글 호환 가능한 ASCII-안전 미러 파일</strong> 을 emit 합니다
 * — 같은 데이터를 식별 가능한 plain-text 표 형식으로 담아 .hwp 확장자로 저장하므로, 한글 워드프로
 * 세서가 RTF/plain 모드로 열어 그대로 편집 가능합니다.
 *
 * <p>renderer 인터페이스(byte[] 반환)는 PDF/XLSX 와 동일해 bulk-email 파이프라인은 format 만
 * 보고 분기하면 됩니다. hwpx SDK 가 들어오는 즉시 이 메서드 본문만 갈아끼우면 끝입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalHwpService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkScheduleService scheduleService;
    private final WorkRecordService recordService;

    public byte[] renderForSchedule(long scheduleId) {
        WorkSchedule s = scheduleService.getOrThrow(scheduleId);
        List<ProcessResponse> processes = recordService.listProcess(scheduleId);

        String companyName = nz(s.getJobStandard().getCompany().getName());
        String jobName = nz(s.getJobStandard().getName());
        String workerName = nz(s.getJobWorker().getCompanyWorker().getWorker().getUser().getName());
        String dateKor =
                s.getStartAt() == null
                        ? ""
                        : String.format(
                                "%d년 %02d월 %02d일 (%s)",
                                s.getStartAt().getYear(),
                                s.getStartAt().getMonthValue(),
                                s.getStartAt().getDayOfMonth(),
                                s.getStartAt()
                                        .getDayOfWeek()
                                        .getDisplayName(TextStyle.FULL, Locale.KOREAN));

        StringBuilder sb = new StringBuilder(2048);
        sb.append("                ").append(companyName).append(" 근무일지\n\n");
        sb.append("일자  : ").append(dateKor).append("\n");
        sb.append("업무명: ").append(jobName).append("\n");
        sb.append("직급  : 사무보조사원\n");
        sb.append("성명  : ").append(workerName).append("\n\n");

        sb.append("┌─────────────────────┬──────────────────────┬──────┬────────────┐\n");
        sb.append("│       업무시간      │       업무내용       │ 시간 │    비고    │\n");
        sb.append("├─────────────────────┼──────────────────────┼──────┼────────────┤\n");
        if (processes.isEmpty()) {
            sb.append("│  기록된 작업이 없습니다.                                              │\n");
        } else {
            for (ProcessResponse p : processes) {
                if (p.startAt() == null || p.endAt() == null) continue;
                long m = Math.max(1, Duration.between(p.startAt(), p.endAt()).toMinutes());
                sb.append(
                        String.format(
                                "│ %-19s │ %-20s │ %4d │ %-10s │%n",
                                HM.format(p.startAt()) + " - " + HM.format(p.endAt()),
                                trunc(nz(p.jobProcessName()), 20),
                                m,
                                trunc(nz(p.endAnswer()), 10)));
            }
        }
        sb.append("└─────────────────────┴──────────────────────┴──────┴────────────┘\n\n");

        sb.append("[특이사항]\n\n\n\n");
        sb.append("[익일업무계획] ").append(jobName).append("\n");
        sb.append("[예상시간]\n");
        sb.append("[지시사항]\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String trunc(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }
}
