package com.rooti.domain.document.presentation;

import com.rooti.domain.document.application.WorkJournalPdfService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-journals")
@RequiredArgsConstructor
@Tag(name = "WorkJournalPdf")
@SecurityRequirement(name = "bearerAuth")
public class WorkJournalPdfController {

    private final WorkJournalPdfService journalService;

    @GetMapping("/{scheduleId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable long scheduleId) {
        byte[] pdf = journalService.renderForSchedule(scheduleId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"work-journal-" + scheduleId + ".pdf\"")
                .body(pdf);
    }
}
