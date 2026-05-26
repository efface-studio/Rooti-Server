package com.rooti.domain.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public final class PushDtos {

    private PushDtos() {}

    @Schema(name = "PushRequest")
    public record PushRequest(
            @NotBlank String token,
            @NotBlank String title,
            String body,
            String deepLink,
            Map<String, Object> extra) {}
}
