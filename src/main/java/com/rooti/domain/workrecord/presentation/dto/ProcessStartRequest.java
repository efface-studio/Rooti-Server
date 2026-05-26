package com.rooti.domain.workrecord.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(name = "ProcessStartRequest")
public record ProcessStartRequest(
        @NotNull Long workScheduleId,
        @NotNull Long jobProcessId,
        String type,
        LocalDateTime at,
        Short condition,
        String answer,
        String voicePath,
        Map<String, Object> process) {}
