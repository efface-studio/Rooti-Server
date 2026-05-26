package com.rooti.domain.board.presentation.dto;

import com.rooti.domain.board.domain.CaregiverBoard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "BoardResponse")
public record Response(
        long id,
        String title,
        String body,
        String authorName,
        long authorId,
        boolean published,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static Response from(CaregiverBoard b) {
        return new Response(
                b.getId(),
                b.getTitle(),
                b.getBody(),
                b.getAuthor().getName(),
                b.getAuthor().getId(),
                b.isPublished(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }
}
