package com.rooti.domain.document.application;

import com.rooti.domain.company.application.CompanyService;
import com.rooti.domain.company.domain.Company;
import com.rooti.domain.document.application.journal.WorkJournalRenderService;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.schedule.infrastructure.WorkScheduleRepository;
import com.rooti.global.email.EmailSender;
import com.rooti.global.email.EmailSender.Attachment;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회사 + 날짜로 그날까지의 근무일지를 ZIP 으로 묶어 담당자에게 메일 발송합니다.
 *
 * <p>이 서비스는 <strong>오케스트레이션 책임만</strong> 갖습니다. 일지 한 부의 데이터 추출과 형식별
 * 렌더링은 {@link WorkJournalRenderService} 에 위임하고, 메일 송신은 {@link EmailSender} 추상화
 * 너머로 위임합니다. 결과적으로 본 클래스의 코드는 "후보 스케줄 조회 → ZIP 패키징 → 비동기 발송"
 * 의 흐름만 노출되며, 형식별 분기나 도메인 객체 가공은 보이지 않습니다.
 *
 * <p>운영에서는 {@code ResendEmailSender} 가 주입되고, 로컬/테스트에서는 {@code LoggingEmailSender}
 * 가 fallback 으로 dry-run 출력만 남깁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalBulkEmailService {

    private final WorkScheduleRepository scheduleRepository;
    private final CompanyService companyService;
    private final WorkJournalRenderService renderService;
    private final EmailSender emailSender;

    public record Result(
            long companyId,
            String companyName,
            LocalDate date,
            String recipientEmail,
            JournalFormat format,
            int journalCount,
            long zipSizeBytes,
            boolean delivered) {}

    public Result send(long companyId, LocalDate date, String recipientEmail, JournalFormat format) {
        Company company = companyService.getOrThrow(companyId);
        JournalFormat fmt = format == null ? JournalFormat.PDF : format;

        List<WorkSchedule> matched = schedulesOf(company.getId(), date);
        if (matched.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.WORK_SCHEDULE_NOT_FOUND, "해당 회사의 그 날짜 일정이 없습니다.");
        }

        byte[] zip = buildZip(matched, date, fmt, company.getName());
        boolean delivered =
                asyncDeliver(
                        recipientEmail,
                        company.getName(),
                        date,
                        matched.size(),
                        company.getId(),
                        fmt,
                        zip);

        return new Result(
                company.getId(),
                company.getName(),
                date,
                recipientEmail,
                fmt,
                matched.size(),
                zip.length,
                delivered);
    }

    // ----- helpers ----------------------------------------------------------
    /** 회사 id + 일자 → 그 회사 그 날 시작된 스케줄들. */
    private List<WorkSchedule> schedulesOf(long companyId, LocalDate date) {
        var from = date.atStartOfDay();
        var to = date.plusDays(1).atStartOfDay();
        return scheduleRepository.findAll().stream()
                .filter(s -> s.getJobStandard().getCompany().getId().equals(companyId))
                .filter(s -> s.getStartAt() != null)
                .filter(s -> !s.getStartAt().isBefore(from) && s.getStartAt().isBefore(to))
                .toList();
    }

    /**
     * 스케줄별로 일지를 렌더링해 ZIP 한 개로 묶습니다. 파일명은 회사가 종이로 받아오던 컨벤션
     * 그대로 {@code yyyy-MM-dd~yyyy-MM-dd_{회사}_{근로자}_근무일지.{ext}}.
     */
    private byte[] buildZip(
            List<WorkSchedule> schedules, LocalDate date, JournalFormat format, String companyName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            String company = sanitize(companyName);
            for (WorkSchedule s : schedules) {
                byte[] body = renderService.render(s.getId(), format);
                String worker =
                        sanitize(s.getJobWorker().getCompanyWorker().getWorker().getUser().getName());
                String entryName =
                        String.format(
                                "%s~%s_%s_%s_근무일지.%s",
                                date, date, company, worker, format.extension());
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(body);
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "ZIP 묶음 생성에 실패했습니다.", e);
        }
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    @Async
    boolean asyncDeliver(
            String to,
            String companyName,
            LocalDate date,
            int count,
            long companyId,
            JournalFormat format,
            byte[] zip) {
        String subject =
                String.format(
                        "[Rooti] %s · %s 근무일지 묶음 (%s · %d건)",
                        companyName, date, format.name(), count);
        String html =
                "<div style=\"font-family:Pretendard,Apple SD Gothic Neo,sans-serif;line-height:1.6\">"
                        + "<h2 style=\"margin:0 0 12px\">근무일지 묶음 도착</h2>"
                        + "<p><strong>" + companyName + "</strong> 의 <strong>" + date
                        + "</strong> 근무일지 <strong>" + count + "건</strong> 을 <strong>"
                        + format.name() + "</strong> 형식으로 첨부했습니다.</p>"
                        + "<p style=\"color:#666;font-size:12px;margin-top:24px\">— Rooti 자동 발송</p>"
                        + "</div>";
        Attachment att =
                new Attachment(
                        String.format(
                                "work-journals-%s-%s-%s.zip",
                                companyId, date, format.name().toLowerCase()),
                        "application/zip",
                        zip);
        try {
            return emailSender.send(to, subject, html, List.of(att));
        } catch (Exception e) {
            log.error("[bulk-journal-email] failed to send to={}", to, e);
            return false;
        }
    }
}
