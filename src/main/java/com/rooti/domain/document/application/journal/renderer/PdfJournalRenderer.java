package com.rooti.domain.document.application.journal.renderer;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.domain.document.application.PdfGenerator;
import com.rooti.domain.document.application.journal.JournalDocument;
import com.rooti.domain.document.application.journal.JournalDocument.Header;
import com.rooti.domain.document.application.journal.JournalDocument.LeaveEntry;
import com.rooti.domain.document.application.journal.JournalDocument.NextPlan;
import com.rooti.domain.document.application.journal.JournalDocument.ProcessRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PDF 렌더러 — 회사가 종이로 받아오던 paper-form 과 1:1 매칭되는 A4 한 페이지 HTML 을 빌드해
 * openhtmltopdf 로 PDF 화 합니다.
 *
 * <p>레이아웃 책임만 가지며 데이터 가공은 {@link com.rooti.domain.document.application.journal.JournalDocumentAssembler}
 * 가 모두 끝낸 상태로 받아옵니다. 텍스트 escape 외에는 어떤 도메인 로직도 들어 있지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class PdfJournalRenderer implements JournalRenderer {

    private final PdfGenerator pdfGenerator;

    @Override
    public JournalFormat format() {
        return JournalFormat.PDF;
    }

    @Override
    public byte[] render(JournalDocument doc) {
        return pdfGenerator.renderHtmlToPdf(buildHtml(doc));
    }

    // ----- HTML template ----------------------------------------------------
    private String buildHtml(JournalDocument doc) {
        Header h = doc.header();
        StringBuilder sb = new StringBuilder(8 * 1024);
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        appendStyle(sb);
        sb.append("</head><body>");

        // Title
        sb.append("<h1 class='title'>").append(esc(h.companyName())).append(" 근무일지</h1>");

        // Info table
        sb.append("<table class='j'><colgroup>")
                .append("<col style='width:15%'><col style='width:38%'>")
                .append("<col style='width:15%'><col style='width:32%'>")
                .append("</colgroup><tbody>")
                .append("<tr>")
                .append("<th class='label'>일자</th>")
                .append("<td class='c'>").append(esc(h.dateLabelKor())).append("</td>")
                .append("<th class='label'>업무명</th>")
                .append("<td class='c'>").append(esc(h.jobStandardName())).append("</td>")
                .append("</tr><tr>")
                .append("<th class='label'>직급</th>")
                .append("<td class='c'>").append(esc(h.workerRank())).append("</td>")
                .append("<th class='label'>성명</th>")
                .append("<td class='c'>").append(esc(h.workerName())).append("</td>")
                .append("</tr></tbody></table>");

        // Leaves (optional)
        if (!doc.leaves().isEmpty()) appendLeaves(sb, doc.leaves());

        // Process table
        appendProcessTable(sb, doc.rows());

        // 특이사항
        sb.append("<table class='j'><tbody>")
                .append("<tr><th colspan='2'>특이사항</th></tr>")
                .append("<tr><td class='box' colspan='2'>").append(esc(doc.remarks())).append("</td></tr>")
                .append("</tbody></table>");

        // 익일업무계획
        appendNextPlans(sb, doc.nextPlans());

        sb.append("</body></html>");
        return sb.toString();
    }

    private static void appendStyle(StringBuilder sb) {
        sb.append("<style>")
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
                .append("table.j th.leave { background:#fef3c7; }")
                .append("table.j td.c { text-align:center; }")
                .append("table.j td.num { text-align:center; font-variant-numeric:tabular-nums; }")
                .append("table.j td.box { height:84px; vertical-align:top; }")
                .append("</style>");
    }

    private static void appendLeaves(StringBuilder sb, java.util.List<LeaveEntry> leaves) {
        sb.append("<table class='j'><colgroup>")
                .append("<col style='width:15%'><col style='width:35%'>")
                .append("<col style='width:12%'><col style='width:38%'>")
                .append("</colgroup><thead>")
                .append("<tr><th colspan='4' class='leave'>휴가 (이 날 근무가 휴가로 처리되었습니다)</th></tr>")
                .append("<tr><th>구분</th><th>기간</th><th>일수</th><th>사유</th></tr>")
                .append("</thead><tbody>");
        for (LeaveEntry l : leaves) {
            sb.append("<tr>")
                    .append("<td class='c'><strong>").append(esc(l.type().label())).append("</strong></td>")
                    .append("<td class='num'>").append(esc(l.rangeLabel())).append("</td>")
                    .append("<td class='num'>").append(l.days()).append("일</td>")
                    .append("<td>").append(esc(l.reason())).append("</td>")
                    .append("</tr>");
        }
        sb.append("</tbody></table>");
    }

    private static void appendProcessTable(StringBuilder sb, java.util.List<ProcessRow> rows) {
        sb.append("<table class='j'><colgroup>")
                .append("<col style='width:22%'><col style='width:46%'>")
                .append("<col style='width:12%'><col style='width:20%'>")
                .append("</colgroup><thead><tr>")
                .append("<th>업무시간</th><th>업무내용</th><th>시간</th><th>비고</th>")
                .append("</tr></thead><tbody>");
        if (rows.isEmpty()) {
            sb.append("<tr><td class='c' colspan='4' style='color:#777;padding:14px'>")
                    .append("기록된 작업이 없습니다.")
                    .append("</td></tr>");
        } else {
            for (ProcessRow r : rows) {
                sb.append("<tr>")
                        .append("<td class='num'>")
                        .append(esc(r.startTime())).append(" - ").append(esc(r.endTime()))
                        .append("</td>")
                        .append("<td class='c'>").append(esc(r.label())).append("</td>")
                        .append("<td class='num'>").append(r.durationMinutes()).append("분</td>")
                        .append("<td>").append(esc(r.note())).append("</td>")
                        .append("</tr>");
            }
        }
        sb.append("</tbody></table>");
    }

    private static void appendNextPlans(StringBuilder sb, java.util.List<NextPlan> plans) {
        sb.append("<table class='j'><colgroup>")
                .append("<col style='width:38%'><col style='width:20%'><col style='width:42%'>")
                .append("</colgroup><thead><tr>")
                .append("<th>익일업무계획</th><th>예상시간</th><th>지시사항</th>")
                .append("</tr></thead><tbody>");
        for (NextPlan p : plans) {
            sb.append("<tr>")
                    .append("<td class='c'>").append(esc(p.task())).append("</td>")
                    .append("<td class='c'>")
                    .append(p.expectedMinutes() == null ? "" : p.expectedMinutes() + "분")
                    .append("</td>")
                    .append("<td>").append(esc(p.instruction())).append("</td>")
                    .append("</tr>");
        }
        sb.append("</tbody></table>");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
