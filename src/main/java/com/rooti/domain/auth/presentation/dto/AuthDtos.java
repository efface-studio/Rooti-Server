package com.rooti.domain.auth.presentation.dto;

import com.rooti.domain.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Container for auth request/response DTOs. Kept together because they're small and co-evolve. */
public final class AuthDtos {

    private AuthDtos() {}

    @Schema(name = "LoginRequest")
    public record LoginRequest(
            @NotBlank @Size(max = 150) String username,
            @NotBlank @Size(max = 100) String password,
            @Schema(description = "Optional FCM device token") String fcmToken) {}

    @Schema(name = "TokenResponse")
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long accessTtlSeconds,
            long refreshTtlSeconds,
            String tokenType) {}

    @Schema(name = "RefreshRequest")
    public record RefreshRequest(@NotBlank String refreshToken) {}

    @Schema(name = "CaregiverSignupRequest")
    public record CaregiverSignupRequest(
            @NotBlank @Size(max = 150) String username,
            @Email @NotBlank @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 32) String phoneNumber) {}

    @Schema(name = "MeResponse")
    public record MeResponse(
            long id,
            String username,
            String email,
            String name,
            String phoneNumber,
            UserRole role) {}
}
