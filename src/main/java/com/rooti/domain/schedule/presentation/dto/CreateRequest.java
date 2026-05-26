package com.rooti.domain.schedule.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(name = "CreateScheduleRequest")
public record CreateRequest(
        @NotNull Long jobWorkerId,
        Long companyChargerId,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean makeWorkDoc) {}
