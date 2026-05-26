package com.rooti.domain.document.presentation;

import com.rooti.domain.document.application.JournalFormat;
import com.rooti.domain.document.application.journal.WorkJournalRenderService;
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
 * <p>경로 두 개를 노출합니다:
 *
 * <ul>
 *   <li>{@code GET /api/v1/work-journals/{id}/pdf} — 기존 PDF 단일 경로 (legacy 호환)
 *   <li>{@code GET /api/v1/work-journals/{id}/file?format=PDF|HWP|XLSX} — 다중 형식
 * </ul>
 *
 * 내부적으로 동일한 {@link WorkJournalRenderService} 한 곳을 호출하므로 어느 경로로 받든 내용은
 * 일치합니다. 클래스 이름이 'PdfController' 였던 시절 PDF 외 형식이 들어오면서 어색해진 것을
 * 'DownloadController' 로 정리했습니다.
 */
@RestController
@RequestMapping("/api/v1/work-journals")
@RequiredArgsConstructor
@Tag(name = "WorkJournalDownload")
@SecurityRequirement(name = "bearerAuth")
public class WorkJournalDownloadController {

    private final WorkJournalRenderService renderService;

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
        byte[] body = renderService.render(scheduleId, format);
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
