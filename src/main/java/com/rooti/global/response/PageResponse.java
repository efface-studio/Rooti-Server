package com.rooti.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Flat pagination DTO – we intentionally do <strong>not</strong> serialize Spring's {@code Page}
 * directly because its JSON shape is unstable across versions and leaks unsorted/pageable fields
 * the frontend has no business knowing about.
 */
@Schema(name = "PageResponse", description = "Pagination envelope")
public record PageResponse<T>(
        List<T> content,
        @Schema(description = "Zero-based page index") int page,
        @Schema(description = "Items per page") int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious());
    }

    public <R> PageResponse<R> map(java.util.function.Function<T, R> mapper) {
        return new PageResponse<>(
                content.stream().map(mapper).toList(),
                page,
                size,
                totalElements,
                totalPages,
                hasNext,
                hasPrevious);
    }
}
