package com.rooti.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Standard success envelope.
 *
 * <pre>{@code
 * { "success": true, "data": <T>, "timestamp": "2026-05-26T14:00:00+09:00" }
 * }</pre>
 *
 * <p>Errors use {@link org.springframework.http.ProblemDetail} (RFC 7807) instead — do not retro-fit
 * an "error" branch into this record.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Generic success envelope")
public record ApiResponse<T>(
        @Schema(example = "true") boolean success, T data, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, OffsetDateTime.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, OffsetDateTime.now());
    }
}
