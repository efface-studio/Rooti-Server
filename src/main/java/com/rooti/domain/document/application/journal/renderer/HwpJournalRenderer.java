package com.rooti.domain.document.application.journal.renderer;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.domain.document.application.journal.JournalDocument;
import com.rooti.domain.document.application.journal.JournalDocument.Header;
import com.rooti.domain.document.application.journal.JournalDocument.LeaveEntry;
import com.rooti.domain.document.application.journal.JournalDocument.ProcessRow;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * HWP 렌더러 — 한컴 SDK / hwpx 라이브러리 도입 전까지의 임시 구현체.
 *
 * <p>한컴 워드프로세서가 plain-text / RTF 모드로도 .hwp 확장자 파일을 열어 편집할 수 있다는 점을
 * 이용해, 같은 데이터를 BOX-DRAW 박스 그림 형식의 plain-text 로 출력해 .hwp 로 저장합니다.
 * hwpx 정식 렌더러로 갈아끼울 때 본 클래스의 {@link #render(JournalDocument)} 본문만 교체하면
 * 인터페이스 호환성은 그대로 유지됩니다.
 */
@Component
public class HwpJournalRenderer implements JournalRenderer {

    @Override
    public JournalFormat format() {
        return JournalFormat.HWP;
    }

    @Override
    public byte[] render(JournalDocument doc) {
        Header h = doc.header();
        StringBuilder sb = new StringBuilder(2048);

        sb.append("                ").append(h.companyName()).append(" 근무일지\n\n");
        sb.append("일자  : ").append(h.dateLabelKor()).append("\n");
        sb.append("업무명: ").append(h.jobStandardName()).append("\n");
        sb.append("직급  : ").append(h.workerRank()).append("\n");
        sb.append("성명  : ").append(h.workerName()).append("\n\n");

        if (!doc.leaves().isEmpty()) {
            sb.append("[ 휴가 — 이 날 근무가 휴가로 처리되었습니다 ]\n");
            for (LeaveEntry l : doc.leaves()) {
                sb.append(
                        String.format(
                                "  - %s | %s | %d일 | %s%n",
                                l.type().label(), l.rangeLabel(), l.days(), nz(l.reason())));
            }
            sb.append("\n");
        }

        sb.append("┌─────────────────────┬──────────────────────┬──────┬────────────┐\n");
        sb.append("│       업무시간      │       업무내용       │ 시간 │    비고    │\n");
        sb.append("├─────────────────────┼──────────────────────┼──────┼────────────┤\n");
        if (doc.rows().isEmpty()) {
            sb.append("│  기록된 작업이 없습니다.                                              │\n");
        } else {
            for (ProcessRow r : doc.rows()) {
                sb.append(
                        String.format(
                                "│ %-19s │ %-20s │ %4d │ %-10s │%n",
                                r.startTime() + " - " + r.endTime(),
                                trunc(r.label(), 20),
                                r.durationMinutes(),
                                trunc(nz(r.note()), 10)));
            }
        }
        sb.append("└─────────────────────┴──────────────────────┴──────┴────────────┘\n\n");

        sb.append("[특이사항]\n").append(nz(doc.remarks())).append("\n\n");
        sb.append("[익일업무계획]\n");
        for (var p : doc.nextPlans()) {
            sb.append(
                    String.format(
                            "  - %s | %s | %s%n",
                            nz(p.task()),
                            p.expectedMinutes() == null ? "" : p.expectedMinutes() + "분",
                            nz(p.instruction())));
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String trunc(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }
}
