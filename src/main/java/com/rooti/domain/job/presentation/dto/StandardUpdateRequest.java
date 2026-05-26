package com.rooti.domain.job.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.Map;

@Schema(name = "JobStandardUpdateRequest")
public record StandardUpdateRequest(
        @Size(max = 200) String name,
        LocalTime routineStartTime,
        @PositiveOrZero Integer standardWorkTimeSeconds,
        @PositiveOrZero Integer standardRestTimeSeconds,
        String startMessage,
        String endMessage,
        Map<String, Object> context,
        Boolean forJournal) {}
