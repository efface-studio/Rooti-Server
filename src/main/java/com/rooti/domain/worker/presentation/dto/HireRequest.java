package com.rooti.domain.worker.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "HireWorkerRequest")
public record HireRequest(@NotNull Long companyId, @NotNull Long workerId) {}
