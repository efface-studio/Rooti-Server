package com.rooti.domain.auth.presentation;

import com.rooti.domain.auth.application.AuthService;
import com.rooti.domain.auth.presentation.dto.AuthDtos.CaregiverSignupRequest;
import com.rooti.domain.auth.presentation.dto.AuthDtos.LoginRequest;
import com.rooti.domain.auth.presentation.dto.AuthDtos.MeResponse;
import com.rooti.domain.auth.presentation.dto.AuthDtos.RefreshRequest;
import com.rooti.domain.auth.presentation.dto.AuthDtos.TokenResponse;
import com.rooti.global.response.ApiResponse;
import com.rooti.global.security.CurrentUser;
import com.rooti.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication & account bootstrap")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login (all roles)")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange refresh token for a new access token")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout (invalidates the refresh token)")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> logout(@CurrentUser PrincipalDetails me) {
        authService.logout(me.userId());
        return ApiResponse.ok();
    }

    @PostMapping("/caregivers/signup")
    @Operation(summary = "Self-signup for a caregiver account")
    public ApiResponse<TokenResponse> signupCaregiver(
            @Valid @RequestBody CaregiverSignupRequest request) {
        return ApiResponse.ok(authService.signupAsCaregiver(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Current principal info")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<MeResponse> me(@CurrentUser PrincipalDetails me) {
        return ApiResponse.ok(authService.me(me));
    }
}
