package com.rooti.domain.auth.application;

import com.rooti.domain.auth.infrastructure.RefreshTokenStore;
import com.rooti.domain.auth.presentation.dto.AuthDtos.CaregiverSignupRequest;
import com.rooti.domain.auth.presentation.dto.AuthDtos.LoginRequest;
import com.rooti.domain.auth.presentation.dto.AuthDtos.MeResponse;
import com.rooti.domain.auth.presentation.dto.AuthDtos.RefreshRequest;
import com.rooti.domain.auth.presentation.dto.AuthDtos.TokenResponse;
import com.rooti.domain.caregiver.domain.Caregiver;
import com.rooti.domain.caregiver.infrastructure.CaregiverRepository;
import com.rooti.domain.user.domain.User;
import com.rooti.domain.user.domain.UserRole;
import com.rooti.domain.user.infrastructure.UserRepository;
import com.rooti.domain.worker.domain.ChallengedWorker;
import com.rooti.domain.worker.infrastructure.ChallengedWorkerRepository;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.jwt.JwtPayload;
import com.rooti.global.jwt.JwtTokenProvider;
import com.rooti.global.jwt.JwtTokenProvider.TokenType;
import com.rooti.global.security.PrincipalDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth use cases: login, refresh, logout, signup-as-caregiver, fetch "me".
 *
 * <p>{@code AuthenticationManager} is intentionally NOT used here — the call path for our mobile
 * app is "username/password JSON → token JSON" with no session involvement, so doing the password
 * compare manually keeps the flow obvious and removes a Spring Security indirection.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final ChallengedWorkerRepository challengedWorkerRepository;
    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    // ---------------------------------------------------------------------
    //  Login
    // ---------------------------------------------------------------------
    public TokenResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByUsername(request.username())
                        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // Persist FCM token on the right role-specific table
        if (request.fcmToken() != null && !request.fcmToken().isBlank()) {
            persistFcmToken(user, request.fcmToken());
        }

        user.markLoggedIn();
        return issueTokens(user);
    }

    private void persistFcmToken(User user, String fcmToken) {
        if (user.getRole() == UserRole.WORKER) {
            challengedWorkerRepository
                    .findByUserId(user.getId())
                    .ifPresent(w -> w.updateFcmToken(fcmToken));
        }
        // Caregivers / chargers also expose FCM in their respective domains;
        // we treat the absence of a mapping as "no device-bound notification" which is fine.
    }

    // ---------------------------------------------------------------------
    //  Refresh
    // ---------------------------------------------------------------------
    public TokenResponse refresh(RefreshRequest request) {
        JwtPayload payload = tokenProvider.parse(request.refreshToken(), TokenType.REFRESH);
        if (!refreshTokenStore.matches(payload.userId(), request.refreshToken())) {
            // Either expired, revoked, or replay → treat as not-found explicitly
            throw new BusinessException(ErrorCode.AUTH_REFRESH_NOT_FOUND);
        }
        User user =
                userRepository
                        .findById(payload.userId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return issueTokens(user);
    }

    public void logout(long userId) {
        refreshTokenStore.remove(userId);
    }

    private TokenResponse issueTokens(User user) {
        List<String> roles = List.of(user.getRole().name());
        String access = tokenProvider.createAccessToken(user.getId(), user.getUsername(), roles);
        String refresh = tokenProvider.createRefreshToken(user.getId(), user.getUsername(), roles);
        refreshTokenStore.save(user.getId(), refresh, tokenProvider.getRefreshTtl());
        return new TokenResponse(
                access,
                refresh,
                tokenProvider.getAccessTtl().getSeconds(),
                tokenProvider.getRefreshTtl().getSeconds(),
                "Bearer");
    }

    // ---------------------------------------------------------------------
    //  Caregiver self-signup
    // ---------------------------------------------------------------------
    public TokenResponse signupAsCaregiver(CaregiverSignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USER_USERNAME_DUPLICATED);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED);
        }

        User user =
                User.builder()
                        .username(request.username())
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .name(request.name())
                        .phoneNumber(request.phoneNumber())
                        .role(UserRole.CAREGIVER)
                        .enabled(true)
                        .build();
        userRepository.save(user);

        Caregiver caregiver = Caregiver.of(user);
        caregiverRepository.save(caregiver);

        return issueTokens(user);
    }

    // ---------------------------------------------------------------------
    //  Me
    // ---------------------------------------------------------------------
    @Transactional(readOnly = true)
    public MeResponse me(PrincipalDetails principal) {
        User user =
                userRepository
                        .findById(principal.userId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getName(),
                user.getPhoneNumber(),
                user.getRole());
    }
}
