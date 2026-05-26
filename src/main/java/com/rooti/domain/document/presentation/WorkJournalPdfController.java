package com.rooti.domain.document.presentation;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.domain.document.application.WorkJournalHwpService;
import com.rooti.domain.document.application.WorkJournalPdfService;
import com.rooti.domain.document.application.WorkJournalXlsxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단일 근무일지 파일 다운로드.
 *
 * <p>format 쿼리 파라미터로 PDF / HWP / XLSX 중 골라 받습니다. 호환을 위해 기존 {@code /pdf} 경로는
 * 그대로 두되 내부적으로 PDF 렌더러로 위임하고, 새 {@code /file?format=...} 경로는 세 형식 모두를
 * 처리합니다. 같은 데이터를 다른 그릇에 담을 뿐이므로 어느 경로로 받든 내용은 동일합니다.
 */
@RestController
@RequestMapping("/api/v1/work-journals")
@RequiredArgsConstructor
@Tag(name = "WorkJournalPdf")
@SecurityRequirement(name = "bearerAuth")
public class WorkJournalPdfController {

    private final WorkJournalPdfService pdfService;
    private final WorkJournalHwpService hwpService;
    private final WorkJournalXlsxService xlsxService;

    @GetMapping("/{scheduleId}/pdf")
    @Operation(summary = "근무일지를 PDF 로 받기 (legacy 호환 경로)")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable long scheduleId) {
        return respond(scheduleId, JournalFormat.PDF);
    }

    @GetMapping("/{scheduleId}/file")
    @Operation(summary = "근무일지를 PDF/HWP/XLSX 중 선택해서 받기")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable long scheduleId,
            @Parameter(description = "PDF | HWP | XLSX")
                    @RequestParam(value = "format", defaultValue = "PDF")
                    JournalFormat format) {
        return respond(scheduleId, format);
    }

    private ResponseEntity<byte[]> respond(long scheduleId, JournalFormat format) {
        byte[] body =
                switch (format) {
                    case PDF -> pdfService.renderForSchedule(scheduleId);
                    case HWP -> hwpService.renderForSchedule(scheduleId);
                    case XLSX -> xlsxService.renderForSchedule(scheduleId);
                };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"work-journal-"
                                + scheduleId
                                + "."
                                + format.extension()
                                + "\"")
                .body(body);
    }
}
