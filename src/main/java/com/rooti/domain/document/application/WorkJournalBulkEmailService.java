package com.rooti.domain.document.application;

import com.rooti.domain.company.application.CompanyService;
import com.rooti.domain.company.domain.Company;
import com.rooti.domain.document.application.journal.JournalEmailComposer;
import com.rooti.domain.document.application.journal.JournalEmailComposer.Composition;
import com.rooti.domain.document.application.journal.JournalZipPackager;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.schedule.infrastructure.WorkScheduleRepository;
import com.rooti.global.email.EmailSender;
import com.rooti.global.email.EmailSender.Attachment;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회사 + 날짜로 그날까지의 근무일지를 ZIP 으로 묶어 담당자에게 메일 발송하는 오케스트레이션.
 *
 * <p>이 클래스에는 시나리오 흐름만 남고, 구체 동작은 협력 컴포넌트에 위임합니다:
 *
 * <ul>
 *   <li>{@link JournalZipPackager} — 스케줄들을 한 ZIP 으로 묶음
 *   <li>{@link JournalEmailComposer} — 제목 / HTML 본문 / 첨부 파일명 작성
 *   <li>{@link EmailSender} — 실제 송신 (운영: {@code ResendEmailSender} / 로컬: {@code LoggingEmailSender})
 * </ul>
 *
 * 본 파일에는 더 이상 HTML 빌딩, 형식별 분기, ZIP I/O 가 보이지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkJournalBulkEmailService {

    private final WorkScheduleRepository scheduleRepository;
    private final CompanyService companyService;
    private final JournalZipPackager zipPackager;
    private final JournalEmailComposer emailComposer;
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

        byte[] zip = zipPackager.pack(matched, date, fmt, company.getName());
        Composition mail = emailComposer.compose(company.getName(), date, matched.size(), fmt, company.getId());
        boolean delivered = asyncDeliver(recipientEmail, mail, zip);

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

    /** 회사 + 일자에 해당하는 스케줄들을 추려옵니다. 시작 시각이 그 날 0시~다음 날 0시 사이여야 합니다. */
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
     * 발송 자체는 비동기로 처리합니다 (현 구현은 EmailSender 가 동기 전송이라도 caller 응답
     * 지연을 막기 위해 @Async 를 유지). 실패 시 false 를 돌려주고 예외는 삼킵니다 — 이미 ZIP 은
     * 생성됐고, 발송 실패는 별도 알림 채널에서 다룹니다.
     */
    @Async
    boolean asyncDeliver(String to, Composition mail, byte[] zip) {
        Attachment att = new Attachment(mail.attachmentFilename(), "application/zip", zip);
        try {
            return emailSender.send(to, mail.subject(), mail.htmlBody(), List.of(att));
        } catch (Exception e) {
            log.error("[bulk-journal-email] failed to send to={}", to, e);
            return false;
        }
    }
}
