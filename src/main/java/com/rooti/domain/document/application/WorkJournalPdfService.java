package com.rooti.domain.document.application;

import com.rooti.domain.schedule.application.WorkScheduleService;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.workrecord.application.WorkRecordService;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.ProcessResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders the daily work-journal PDF in the exact layout the company has been using on paper.
 *
 * <p>Layout (matches the user-supplied sample {@code 청주 바른기준치과 근무일지.pdf} 1:1):
 *
 * <ol>
 *   <li>Centered title: {@code {companyName} 근무일지}
 *   <li>Info table (일자 / 업무명 / 직급 / 성명)
 *   <li>Process table (업무시간 / 업무내용 / 시간 / 비고) — one row per WorkProcessRecord
 *   <li>특이사항 free-form box
 *   <li>익일업무계획 / 예상시간 / 지시사항 footer table
 * </ol>
 *
 * <p>The HTML uses tabular numerics + Korean web-safe fonts (NanumGothic / Malgun Gothic /
 * Pretendard fallback chain) so the openhtmltopdf output is consistent between Linux servers and
 * Hancom-installed Windows clients. Inline CSS is intentional — openhtmltopdf does not load
 * external stylesheets.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalPdfService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkScheduleService scheduleService;
    private final WorkRecordService recordService;
    private final PdfGenerator pdfGenerator;

    public byte[] renderForSchedule(long scheduleId) {
        WorkSchedule s = scheduleService.getOrThrow(scheduleId);
        List<ProcessResponse> processes = recordService.listProcess(scheduleId);
        return pdfGenerator.renderHtmlToPdf(buildHtml(s, processes));
    }

    private String buildHtml(WorkSchedule s, List<ProcessResponse> processes) {
        String companyName = safe(s.getJobStandard().getCompany().getName());
        String jobName = safe(s.getJobStandard().getName());
        String workerName = safe(s.getJobWorker().getCompanyWorker().getWorker().getUser().getName());
        // 직급 정보가 도메인에 따로 없으면 기본값(사무보조사원)을 채워둡니다 — 추후 hire 정보가
        // 들어오면 그 값을 우선 사용하도록 갈아끼웁니다.
        String workerRank = "사무보조사원";
        String dateKor = formatDateKor(s.getStartAt());

        StringBuilder html = new StringBuilder(8 * 1024);
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<style>")
                .append("@page { size: A4; margin: 18mm; }")
                .append("body { font-family:'NanumGothic','Pretendard','Malgun Gothic',sans-serif;")
                .append(" font-size:11pt; color:#111; line-height:1.55; }")
                .append("h1.title { text-align:center; font-size:18pt; font-weight:700;")
                .append(" letter-spacing:-0.01em; margin:0 0 18px; }")
                .append("table.j { width:100%; border-collapse:collapse; border:1px solid #222; }")
                .append("table.j + table.j { border-top:none; }")
                .append("table.j th, table.j td { border:1px solid #222; padding:6px 8px; font-size:10.5pt; }")
                .append("table.j th { background:#e9edf2; font-weight:700; text-align:center; }")
                .append("table.j th.label { background:#f2f4f7; }")
                .append("table.j td.c { text-align:center; }")
                .append("table.j td.num { text-align:center; font-variant-numeric:tabular-nums; }")
                .append("table.j td.box { height:84px; vertical-align:top; }")
                .append("</style></head><body>");

        // Title
        html.append("<h1 class='title'>").append(companyName).append(" 근무일지</h1>");

        // Info table
        html.append("<table class='j'><colgroup>")
                .append("<col style='width:15%'><col style='width:38%'>")
                .append("<col style='width:15%'><col style='width:32%'>")
                .append("</colgroup><tbody>")
                .append("<tr><th class='label'>일자</th><td class='c'>").append(dateKor).append("</td>")
                .append("<th class='label'>업무명</th><td class='c'>").append(jobName).append("</td></tr>")
                .append("<tr><th class='label'>직급</th><td class='c'>").append(workerRank).append("</td>")
                .append("<th class='label'>성명</th><td class='c'>").append(workerName).append("</td></tr>")
                .append("</tbody></table>");

        // Process table
        html.append("<table class='j'><colgroup>")
                .append("<col style='width:22%'><col style='width:46%'>")
                .append("<col style='width:12%'><col style='width:20%'>")
                .append("</colgroup><thead><tr>")
                .append("<th>업무시간</th><th>업무내용</th><th>시간</th><th>비고</th>")
                .append("</tr></thead><tbody>");
        if (processes.isEmpty()) {
            html.append("<tr><td class='c' colspan='4' style='color:#777;padding:14px'>")
                    .append("기록된 작업이 없습니다.")
                    .append("</td></tr>");
        } else {
            for (ProcessResponse p : processes) {
                if (p.startAt() == null || p.endAt() == null) continue;
                long minutes = Math.max(1, Duration.between(p.startAt(), p.endAt()).toMinutes());
                html.append("<tr>")
                        .append("<td class='num'>")
                        .append(HM.format(p.startAt())).append(" - ").append(HM.format(p.endAt()))
                        .append("</td>")
                        .append("<td class='c'>").append(safe(p.jobProcessName())).append("</td>")
                        .append("<td class='num'>").append(minutes).append("분</td>")
                        .append("<td>").append(safe(p.endAnswer())).append("</td>")
                        .append("</tr>");
            }
        }
        html.append("</tbody></table>");

        // 특이사항
        html.append("<table class='j'><tbody>")
                .append("<tr><th colspan='2'>특이사항</th></tr>")
                .append("<tr><td class='box' colspan='2'></td></tr>")
                .append("</tbody></table>");

        // 익일업무계획
        html.append("<table class='j'><colgroup>")
                .append("<col style='width:38%'><col style='width:20%'><col style='width:42%'>")
                .append("</colgroup><thead><tr>")
                .append("<th>익일업무계획</th><th>예상시간</th><th>지시사항</th>")
                .append("</tr></thead><tbody>")
                .append("<tr><td class='c'>").append(jobName).append("</td>")
                .append("<td class='c'></td><td></td></tr>")
                .append("</tbody></table>");

        html.append("</body></html>");
        return html.toString();
    }

    private String formatDateKor(LocalDateTime dt) {
        if (dt == null) return "";
        String dow = dt.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
        return String.format(
                "%d년 %02d월 %02d일 (%s)",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(), dow);
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
