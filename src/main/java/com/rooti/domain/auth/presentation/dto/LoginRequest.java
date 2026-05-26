package com.rooti.domain.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @NotBlank @Size(max = 150) String username,
        @NotBlank @Size(max = 100) String password,
        @Schema(description = "Optional FCM device token") String fcmToken) {}
