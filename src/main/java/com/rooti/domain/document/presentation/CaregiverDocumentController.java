package com.rooti.domain.document.presentation;

import com.rooti.domain.document.application.CaregiverDocumentService;
import com.rooti.domain.document.domain.CaregiverDocument;
import com.rooti.domain.document.infrastructure.StorageService;
import com.rooti.domain.document.presentation.dto.DocumentResponse;
import com.rooti.global.response.ApiResponse;
import com.rooti.global.security.CurrentUser;
import com.rooti.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Document")
@SecurityRequirement(name = "bearerAuth")
public class CaregiverDocumentController {

    private final CaregiverDocumentService documentService;
    private final StorageService storageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> upload(
            @CurrentUser PrincipalDetails me,
            @RequestParam long relationId,
            @RequestParam long typeId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(documentService.upload(me.userId(), relationId, typeId, file));
    }

    @GetMapping("/by-relation/{relationId}")
    public ApiResponse<List<DocumentResponse>> list(@PathVariable long relationId) {
        return ApiResponse.ok(documentService.listByRelation(relationId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            @CurrentUser PrincipalDetails me, @PathVariable long id) {
        CaregiverDocument doc = documentService.loadForDownload(me.userId(), id);
        InputStream is = storageService.open(doc.getFilename());

        String fileNameOnly =
                doc.getFilename().contains("/")
                        ? doc.getFilename().substring(doc.getFilename().lastIndexOf('/') + 1)
                        : doc.getFilename();
        String encoded = URLEncoder.encode(fileNameOnly, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(
                        doc.getContentType() == null
                                ? MediaType.APPLICATION_OCTET_STREAM
                                : MediaType.parseMediaType(doc.getContentType()))
                .body(new InputStreamResource(is));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUser PrincipalDetails me, @PathVariable long id) {
        documentService.delete(me.userId(), id);
        return ApiResponse.ok();
    }
}
