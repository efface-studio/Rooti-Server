package com.rooti.domain.document.presentation.dto;

import com.rooti.domain.document.domain.CaregiverDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "DocumentResponse")
public record DocumentResponse(
        long id,
        long relationId,
        long typeId,
        String typeName,
        String filename,
        String downloadUrl,
        Long size,
        String contentType,
        LocalDateTime createdAt) {

    public static DocumentResponse from(CaregiverDocument d, String downloadUrl) {
        return new DocumentResponse(
                d.getId(),
                d.getRelation().getId(),
                d.getType().getId(),
                d.getType().getName(),
                d.getFilename(),
                downloadUrl,
                d.getFileSize(),
                d.getContentType(),
                d.getCreatedAt());
    }
}
