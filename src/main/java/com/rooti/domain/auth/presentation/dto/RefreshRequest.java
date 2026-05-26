package com.rooti.domain.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest")
public record RefreshRequest(@NotBlank String refreshToken) {}
