package com.rooti.domain.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenResponse")
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTtlSeconds,
        long refreshTtlSeconds,
        String tokenType) {}
