package com.rooti.domain.document.application.journal;

import com.rooti.domain.document.application.JournalFormat;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 일지 묶음 메일의 제목 / HTML 본문 / 첨부 파일명을 만들어 주는 작은 헬퍼.
 *
 * <p>발송 로직({@code WorkJournalBulkEmailService}) 에서 HTML 문자열 빌딩이 한 줄도 보이지
 * 않도록 분리합니다. 디자인 / 카피 / 첨부 파일명 같은 표현 결정은 이 클래스 한 곳만 손대면 됩니다.
 */
@Component
public class JournalEmailComposer {

    public Composition compose(
            String companyName, LocalDate date, int journalCount, JournalFormat format, long companyId) {
        return new Composition(
                buildSubject(companyName, date, journalCount, format),
                buildHtml(companyName, date, journalCount, format),
                buildAttachmentFilename(date, format, companyId));
    }

    public record Composition(String subject, String htmlBody, String attachmentFilename) {}

    private String buildSubject(String companyName, LocalDate date, int count, JournalFormat format) {
        return String.format(
                "[Rooti] %s · %s 근무일지 묶음 (%s · %d건)",
                companyName, date, format.name(), count);
    }

    private String buildHtml(String companyName, LocalDate date, int count, JournalFormat format) {
        return ""
                + "<div style=\"font-family:Pretendard,Apple SD Gothic Neo,sans-serif;line-height:1.6\">"
                + "<h2 style=\"margin:0 0 12px\">근무일지 묶음 도착</h2>"
                + "<p><strong>" + escape(companyName) + "</strong> 의 <strong>" + date
                + "</strong> 근무일지 <strong>" + count + "건</strong> 을 <strong>"
                + format.name() + "</strong> 형식으로 첨부했습니다.</p>"
                + "<p style=\"color:#666;font-size:12px;margin-top:24px\">— Rooti 자동 발송</p>"
                + "</div>";
    }

    private String buildAttachmentFilename(LocalDate date, JournalFormat format, long companyId) {
        return String.format(
                "work-journals-%s-%s-%s.zip",
                companyId, date, format.name().toLowerCase());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
