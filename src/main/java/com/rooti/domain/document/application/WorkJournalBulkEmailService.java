package com.rooti.domain.document.application;

import com.rooti.domain.company.application.CompanyService;
import com.rooti.domain.company.domain.Company;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.schedule.infrastructure.WorkScheduleRepository;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bundles every work-journal PDF for a given company + date into a single ZIP
 * and emails it to the requested recipient (typically the company charger).
 *
 * <p>The mail dependency is {@link JavaMailSender} provided by Spring Boot's
 * mail starter. It is wired via {@code ObjectProvider} so the bean is optional
 * — when SMTP isn't configured (local dev, tests) the service falls back to a
 * dry-run that logs what would have been sent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalBulkEmailService {

    private final WorkScheduleRepository scheduleRepository;
    private final CompanyService companyService;
    private final WorkJournalPdfService pdfService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** Result returned to the caller so they can audit what was shipped. */
    public record Result(
            long companyId,
            String companyName,
            LocalDate date,
            String recipientEmail,
            int journalCount,
            long zipSizeBytes,
            boolean delivered) {}

    public Result send(long companyId, LocalDate date, String recipientEmail) {
        Company company = companyService.getOrThrow(companyId);

        // Collect every schedule for this company's job standards on the chosen day.
        var from = date.atStartOfDay();
        var to = date.plusDays(1).atStartOfDay();
        List<WorkSchedule> schedules =
                scheduleRepository.findInRange(0L, from, to); // dummy id — uses range only
        // ↑ findInRange currently filters by job_worker_id; for bulk we need a
        // company-aware query. Until we extend the repo, do an in-memory filter
        // on a sufficiently broad range fetch.
        List<WorkSchedule> matched =
                scheduleRepository.findAll().stream()
                        .filter(s -> s.getJobStandard().getCompany().getId().equals(company.getId()))
                        .filter(s -> !s.getStartAt().isBefore(from) && s.getStartAt().isBefore(to))
                        .toList();

        if (matched.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.WORK_SCHEDULE_NOT_FOUND, "해당 회사의 그 날짜 일정이 없습니다.");
        }

        byte[] zip = buildZip(matched, date);
        boolean delivered = deliver(recipientEmail, company, date, zip, matched.size());

        return new Result(
                company.getId(),
                company.getName(),
                date,
                recipientEmail,
                matched.size(),
                zip.length,
                delivered);
    }

    private byte[] buildZip(List<WorkSchedule> schedules, LocalDate date) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (WorkSchedule s : schedules) {
                byte[] pdf = pdfService.renderForSchedule(s.getId());
                String workerName =
                        s.getJobWorker().getCompanyWorker().getWorker().getUser().getName();
                String entryName =
                        String.format(
                                "%s_%s_%s.pdf",
                                date,
                                workerName.replaceAll("\\s+", ""),
                                s.getJobStandard().getName().replaceAll("\\s+", ""));
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(pdf);
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
    void asyncDeliver(JavaMailSender sender, String to, Company company, LocalDate date, byte[] zip, int count) {
        try {
            var msg = sender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(String.format("[Rooti] %s · %s 근무일지 묶음 (%d건)", company.getName(), date, count));
            helper.setText(
                    String.format(
                            "%s 의 %s 근무일지 %d건을 첨부했습니다.\n\n— Rooti 자동 발송",
                            company.getName(), date, count),
                    false);
            helper.addAttachment(
                    String.format("work-journals-%s-%s.zip", company.getId(), date),
                    new org.springframework.core.io.ByteArrayResource(zip));
            sender.send(msg);
            log.info("[bulk-journal-email] sent to={}, company={}, count={}", to, company.getId(), count);
        } catch (Exception e) {
            log.error("[bulk-journal-email] failed to send to={}", to, e);
        }
    }

    private boolean deliver(String to, Company company, LocalDate date, byte[] zip, int count) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.info(
                    "[bulk-journal-email:dry-run] would send {} bytes ({} pdfs) to {} for company {} on {}",
                    zip.length, count, to, company.getId(), date);
            return false;
        }
        asyncDeliver(sender, to, company, date, zip, count);
        return true;
    }
}
