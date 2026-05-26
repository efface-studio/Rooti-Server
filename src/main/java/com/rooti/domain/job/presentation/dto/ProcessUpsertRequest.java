package com.rooti.domain.job.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(name = "ProcessUpsertRequest")
public record ProcessUpsertRequest(
        Long id,
        @NotBlank @Size(max = 200) String name,
        @PositiveOrZero int sequence,
        String videoPath,
        String startMessage,
        String endMessage,
        Map<String, Object> context,
        @PositiveOrZero int processTimeSeconds) {}
