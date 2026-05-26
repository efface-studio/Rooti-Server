package com.rooti.domain.auth.application;

import com.rooti.domain.auth.infrastructure.RefreshTokenStore;
import com.rooti.domain.auth.presentation.dto.CaregiverSignupRequest;
import com.rooti.domain.auth.presentation.dto.LoginRequest;
import com.rooti.domain.auth.presentation.dto.MeResponse;
import com.rooti.domain.auth.presentation.dto.RefreshRequest;
import com.rooti.domain.auth.presentation.dto.TokenResponse;
import com.rooti.domain.caregiver.domain.Caregiver;
import com.rooti.domain.caregiver.infrastructure.CaregiverRepository;
import com.rooti.domain.user.domain.User;
import com.rooti.domain.user.domain.UserRole;
import com.rooti.domain.user.infrastructure.UserRepository;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.jwt.JwtPayload;
import com.rooti.global.jwt.JwtTokenProvider;
import com.rooti.global.jwt.JwtTokenProvider.TokenType;
import com.rooti.global.security.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 (login / refresh / logout / signup) 시나리오의 오케스트레이션.
 *
 * <p>실제 동작은 작은 컴포넌트들에 위임합니다:
 *
 * <ul>
 *   <li>{@link TokenIssuer} — access + refresh 토큰 쌍을 만들어 store 에 저장
 *   <li>{@link FcmTokenSync} — 로그인 시 함께 들어온 FCM 디바이스 토큰을 역할별 테이블에 기록
 * </ul>
 *
 * 본 클래스에는 "비밀번호 확인 / refresh 정합성 검증 / 회원 생성" 같은 시나리오 흐름만 남으며,
 * 토큰 발급의 디테일(만료 시간, redis 저장 등) 은 노출되지 않습니다.
 *
 * <p>{@code AuthenticationManager} 는 일부러 쓰지 않습니다 — 모바일 앱의 호출 경로가 "username
 * + password JSON → token JSON" 이라 세션을 끼우지 않으면 흐름이 더 직관적입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenIssuer tokenIssuer;
    private final FcmTokenSync fcmTokenSync;

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

        if (request.fcmToken() != null && !request.fcmToken().isBlank()) {
            fcmTokenSync.persist(user, request.fcmToken());
        }

        user.markLoggedIn();
        return tokenIssuer.issueFor(user);
    }

    // ---------------------------------------------------------------------
    //  Refresh / logout
    // ---------------------------------------------------------------------
    public TokenResponse refresh(RefreshRequest request) {
        JwtPayload payload = tokenProvider.parse(request.refreshToken(), TokenType.REFRESH);
        if (!refreshTokenStore.matches(payload.userId(), request.refreshToken())) {
            // 만료 / 폐기 / 재생 — 어느 쪽이든 not-found 로 동등 처리해 정보를 흘리지 않습니다.
            throw new BusinessException(ErrorCode.AUTH_REFRESH_NOT_FOUND);
        }
        User user =
                userRepository
                        .findById(payload.userId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return tokenIssuer.issueFor(user);
    }

    public void logout(long userId) {
        refreshTokenStore.remove(userId);
    }

    // ---------------------------------------------------------------------
    //  Caregiver self-signup — 회원 생성 후 곧장 토큰을 돌려줍니다.
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
        caregiverRepository.save(Caregiver.of(user));
        return tokenIssuer.issueFor(user);
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
