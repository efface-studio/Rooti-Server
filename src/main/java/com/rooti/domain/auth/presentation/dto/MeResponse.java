package com.rooti.domain.auth.presentation.dto;

import com.rooti.domain.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MeResponse")
public record MeResponse(
        long id,
        String username,
        String email,
        String name,
        String phoneNumber,
        UserRole role) {}
