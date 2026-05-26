package com.rooti.domain.job.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AssignJobWorkerRequest")
public record AssignRequest(@NotNull Long companyWorkerId, @NotNull Long jobStandardId) {}
