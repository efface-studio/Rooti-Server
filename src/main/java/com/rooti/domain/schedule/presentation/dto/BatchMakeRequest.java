package com.rooti.domain.schedule.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "BatchMakeRequest")
public record BatchMakeRequest(
        @NotNull Long jobStandardId,
        @NotNull LocalDate date,
        Long companyChargerId,
        List<@NotNull Long> jobWorkerIds) {}
