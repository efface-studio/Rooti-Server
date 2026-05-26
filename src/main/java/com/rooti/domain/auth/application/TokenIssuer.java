package com.rooti.domain.auth.application;

import com.rooti.domain.auth.infrastructure.RefreshTokenStore;
import com.rooti.domain.auth.presentation.dto.TokenResponse;
import com.rooti.domain.user.domain.User;
import com.rooti.global.jwt.JwtTokenProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사용자 한 명에 대해 access + refresh 토큰 쌍을 발급하고, 발급된 refresh 토큰을 redis 에
 * 저장하는 책임만 가집니다.
 *
 * <p>여러 시나리오(로그인 / 토큰 재발급 / 회원가입 직후 자동 로그인) 가 동일한 발급 로직을
 * 공유하므로 {@link com.rooti.domain.auth.application.AuthService} 안의 private 헬퍼로 묻혀
 * 있었던 것을 별도 컴포넌트로 끄집어내, 같은 책임이 여러 곳에서 재사용되도록 했습니다.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 사용자에게 새 토큰 쌍을 발급합니다. refresh 토큰은 store 에 즉시 저장되며 기존 토큰은
     * 덮어쓰여집니다 (한 사용자 ↔ 한 활성 refresh 토큰).
     */
    public TokenResponse issueFor(User user) {
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
}
