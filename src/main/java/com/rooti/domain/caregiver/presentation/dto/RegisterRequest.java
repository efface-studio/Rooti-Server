package com.rooti.domain.caregiver.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "RegisterWorkerRequest")
public record RegisterRequest(@NotNull Long workerId) {}
