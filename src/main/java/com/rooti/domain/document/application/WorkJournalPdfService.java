package com.rooti.domain.document.application;

import com.rooti.domain.schedule.application.WorkScheduleService;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.workrecord.application.WorkRecordService;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.ProcessResponse;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.RecordResponse;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders the daily work-journal PDF (legacy {@code /works/journal/pdf}).
 *
 * <p>Note this is intentionally template-string-based: low ceremony for a single document, and
 * easy to evolve when the design team tweaks copy. If we add a second document layout, switch to
 * Thymeleaf templates under {@code src/main/resources/templates/pdf/}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalPdfService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WorkScheduleService scheduleService;
    private final WorkRecordService recordService;
    private final PdfGenerator pdfGenerator;

    public byte[] renderForSchedule(long scheduleId) {
        WorkSchedule s = scheduleService.getOrThrow(scheduleId);
        List<RecordResponse> records = recordService.list(scheduleId);
        List<ProcessResponse> processes = recordService.listProcess(scheduleId);
        return pdfGenerator.renderHtmlToPdf(buildHtml(s, records, processes));
    }

    private String buildHtml(
            WorkSchedule s, List<RecordResponse> records, List<ProcessResponse> processes) {
        StringBuilder html = new StringBuilder();
        html.append(
                "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                        + "<style>"
                        + "@page { size: A4; margin: 18mm; }"
                        + "body { font-family: 'NanumGothic','Malgun Gothic',sans-serif; font-size:11pt; color:#1f2937; }"
                        + "h1 { font-size: 18pt; border-bottom: 2px solid #111827; padding-bottom: 8px; }"
                        + "table { width: 100%; border-collapse: collapse; margin-top: 14px; }"
                        + "th, td { border: 1px solid #d1d5db; padding: 8px; text-align: left; vertical-align: top; }"
                        + "th { background: #f3f4f6; }"
                        + ".muted { color:#6b7280; font-size: 10pt; }"
                        + "</style></head><body>");

        html.append("<h1>근무일지</h1>");
        html.append("<p class='muted'>")
                .append("회사 : ").append(safe(s.getJobStandard().getCompany().getName()))
                .append(" / 업무 : ").append(safe(s.getJobStandard().getName()))
                .append("<br/>")
                .append("근로자 : ").append(safe(s.getJobWorker().getCompanyWorker().getWorker().getUser().getName()))
                .append("<br/>")
                .append("시작 : ").append(s.getStartAt() == null ? "" : TS.format(s.getStartAt()))
                .append(" / 종료 : ").append(s.getEndAt() == null ? "(미종료)" : TS.format(s.getEndAt()))
                .append("</p>");

        html.append("<h3>근무 기록</h3>");
        html.append("<table><tr><th>구분</th><th>시작</th><th>종료</th></tr>");
        for (RecordResponse r : records) {
            html.append("<tr><td>").append(r.type()).append("</td>")
                    .append("<td>").append(r.startAt() == null ? "" : TS.format(r.startAt())).append("</td>")
                    .append("<td>").append(r.endAt() == null ? "" : TS.format(r.endAt())).append("</td></tr>");
        }
        html.append("</table>");

        html.append("<h3>업무 프로세스 기록</h3>");
        html.append(
                "<table><tr><th>단계</th><th>시작</th><th>시작상태</th><th>종료</th><th>종료상태</th><th>비고</th></tr>");
        for (ProcessResponse p : processes) {
            html.append("<tr>")
                    .append("<td>").append(safe(p.jobProcessName())).append("</td>")
                    .append("<td>").append(p.startAt() == null ? "" : TS.format(p.startAt())).append("</td>")
                    .append("<td>").append(p.startCondition() == null ? "" : describe(p.startCondition())).append("</td>")
                    .append("<td>").append(p.endAt() == null ? "" : TS.format(p.endAt())).append("</td>")
                    .append("<td>").append(p.endCondition() == null ? "" : describe(p.endCondition())).append("</td>")
                    .append("<td>").append(safe(p.endAnswer())).append("</td>")
                    .append("</tr>");
        }
        html.append("</table>");
        html.append("</body></html>");
        return html.toString();
    }

    private String describe(short cond) {
        return switch (cond) {
            case 1 -> "정상";
            case 0 -> "이상";
            default -> "기타";
        };
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("<", "&lt;").replace(">", "&gt;");
    }
}
