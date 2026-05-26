package com.rooti.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rooti.domain.auth.infrastructure.RefreshTokenStore;
import com.rooti.domain.auth.presentation.dto.AuthDtos.LoginRequest;
import com.rooti.domain.caregiver.infrastructure.CaregiverRepository;
import com.rooti.domain.user.domain.User;
import com.rooti.domain.user.domain.UserRole;
import com.rooti.domain.user.infrastructure.UserRepository;
import com.rooti.domain.worker.infrastructure.ChallengedWorkerRepository;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.jwt.JwtTokenProvider;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock ChallengedWorkerRepository challengedWorkerRepository;
    @Mock CaregiverRepository caregiverRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock RefreshTokenStore refreshTokenStore;

    @InjectMocks AuthService authService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser =
                User.builder()
                        .username("admin")
                        .email("admin@rooti.io")
                        .passwordHash("$encoded$")
                        .name("Root")
                        .role(UserRole.ADMIN)
                        .enabled(true)
                        .build();

        when(tokenProvider.getAccessTtl()).thenReturn(Duration.ofMinutes(15));
        when(tokenProvider.getRefreshTtl()).thenReturn(Duration.ofDays(14));
        when(tokenProvider.createAccessToken(anyLong(), anyString(), any()))
                .thenReturn("access-jwt");
        when(tokenProvider.createRefreshToken(anyLong(), anyString(), any()))
                .thenReturn("refresh-jwt");
    }

    @Test
    @DisplayName("Successful login issues both tokens and persists the refresh side-band")
    void login_success() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches(eq("pw"), anyString())).thenReturn(true);

        var res = authService.login(new LoginRequest("admin", "pw", null));

        assertThat(res.accessToken()).isEqualTo("access-jwt");
        assertThat(res.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        verify(refreshTokenStore, times(1)).save(anyLong(), eq("refresh-jwt"), any());
    }

    @Test
    @DisplayName("Wrong password maps to AUTH_INVALID_CREDENTIALS, NOT a generic 500")
    void login_wrong_password() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "nope", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("Unknown username also maps to AUTH_INVALID_CREDENTIALS (no user-existence oracle)")
    void login_unknown_user() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "pw", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("Disabled account is rejected before password check")
    void login_disabled_account() {
        User disabled =
                User.builder()
                        .username("x")
                        .passwordHash("$x$")
                        .name("X")
                        .role(UserRole.ADMIN)
                        .enabled(false)
                        .build();
        when(userRepository.findByUsername("x")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.login(new LoginRequest("x", "pw", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED);
    }

    @Test
    @DisplayName("Logout removes the stored refresh token")
    void logout_clears_refresh() {
        authService.logout(123L);
        verify(refreshTokenStore).remove(123L);
    }

    @Test
    @DisplayName("Roles claim in the issued token mirrors the user's role")
    void roles_claim_matches() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        authService.login(new LoginRequest("admin", "pw", null));

        verify(tokenProvider).createAccessToken(anyLong(), eq("admin"), eq(List.of("ADMIN")));
    }
}
