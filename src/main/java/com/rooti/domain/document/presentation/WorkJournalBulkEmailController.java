package com.rooti.domain.document.presentation;

import com.rooti.domain.document.application.WorkJournalBulkEmailService;
import com.rooti.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin operation: assemble every work-journal PDF for a company's chosen day,
 * bundle into one ZIP, and email it to the charger. Used as part of the
 * monthly billing / hand-off workflow.
 */
@RestController
@RequestMapping("/api/v1/work-journals")
@RequiredArgsConstructor
@Tag(name = "WorkJournalBulkEmail")
@SecurityRequirement(name = "bearerAuth")
public class WorkJournalBulkEmailController {

    private final WorkJournalBulkEmailService bulkEmailService;

    public record BulkEmailRequest(
            @NotNull Long companyId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @NotNull @Email String recipientEmail) {}

    @PostMapping("/bulk-email")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    @Operation(summary = "회사 + 날짜로 근무일지 ZIP을 만들어 담당자에게 메일 발송")
    public ApiResponse<WorkJournalBulkEmailService.Result> send(
            @Valid @RequestBody BulkEmailRequest req) {
        return ApiResponse.ok(
                bulkEmailService.send(req.companyId(), req.date(), req.recipientEmail()));
    }
}
