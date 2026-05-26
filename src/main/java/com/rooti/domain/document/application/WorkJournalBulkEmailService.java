package com.rooti.domain.document.application;

import com.rooti.domain.company.application.CompanyService;
import com.rooti.domain.company.domain.Company;
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
 * Bundles every work-journal for a given company + date into a single ZIP
 * and emails it to the requested recipient (typically the company charger).
 *
 * <p>The journal of each worker is rendered into the format the admin picked
 * (PDF / HWP / XLSX) — same data, same layout, different canvas. The ZIP entry
 * filename follows the convention used on paper: {@code yyyy-MM-dd~yyyy-MM-dd_
 * {company}_{worker}_근무일지.{ext}}.
 *
 * <p>Email channel is abstracted behind {@link EmailSender}. In production we
 * wire {@code ResendEmailSender}; locally / in tests {@code LoggingEmailSender}
 * is picked up as a dry-run fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalBulkEmailService {

    private final WorkScheduleRepository scheduleRepository;
    private final CompanyService companyService;
    private final WorkJournalPdfService pdfService;
    private final WorkJournalHwpService hwpService;
    private final WorkJournalXlsxService xlsxService;
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

        var from = date.atStartOfDay();
        var to = date.plusDays(1).atStartOfDay();
        List<WorkSchedule> matched =
                scheduleRepository.findAll().stream()
                        .filter(s -> s.getJobStandard().getCompany().getId().equals(company.getId()))
                        .filter(s -> !s.getStartAt().isBefore(from) && s.getStartAt().isBefore(to))
                        .toList();

        if (matched.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.WORK_SCHEDULE_NOT_FOUND, "해당 회사의 그 날짜 일정이 없습니다.");
        }

        JournalFormat fmt = format == null ? JournalFormat.PDF : format;
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

    /** Routes to the right renderer based on the requested format. */
    private byte[] renderOne(long scheduleId, JournalFormat format) {
        return switch (format) {
            case PDF -> pdfService.renderForSchedule(scheduleId);
            case HWP -> hwpService.renderForSchedule(scheduleId);
            case XLSX -> xlsxService.renderForSchedule(scheduleId);
        };
    }

    private byte[] buildZip(
            List<WorkSchedule> schedules, LocalDate date, JournalFormat format, String companyName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (WorkSchedule s : schedules) {
                byte[] body = renderOne(s.getId(), format);
                String workerName =
                        s.getJobWorker().getCompanyWorker().getWorker().getUser().getName();
                // 회사가 종이로 받아오던 파일명 규칙 그대로:
                //   yyyy-MM-dd~yyyy-MM-dd_{회사}_{근로자}_근무일지.{ext}
                String entryName =
                        String.format(
                                "%s~%s_%s_%s_근무일지.%s",
                                date,
                                date,
                                companyName.replaceAll("\\s+", ""),
                                workerName.replaceAll("\\s+", ""),
                                format.extension());
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
