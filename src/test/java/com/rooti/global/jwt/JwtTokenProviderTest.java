package com.rooti.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.security.SecurityProperties;
import com.rooti.global.security.SecurityProperties.Cors;
import com.rooti.global.security.SecurityProperties.Jwt;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        // ≥512 bits as required by jjwt HS256
        String secret =
                Base64.getEncoder()
                        .encodeToString(
                                "test-secret-key-must-be-at-least-512-bits-long-blah-blah-blah-blah-blah-blah"
                                        .getBytes());
        Jwt jwt = new Jwt(secret, 15, 14, "rooti", "Authorization", "Bearer ");
        Cors cors = new Cors(List.of(), List.of(), List.of(), List.of(), false, 0);
        SecurityProperties props = new SecurityProperties(jwt, cors, List.of());
        provider = new JwtTokenProvider(props);
    }

    @Test
    @DisplayName("issue → parse round-trip preserves the claims")
    void roundtrip_access() {
        String token = provider.createAccessToken(42L, "alice", List.of("ADMIN"));
        JwtPayload payload = provider.parse(token, JwtTokenProvider.TokenType.ACCESS);

        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.username()).isEqualTo("alice");
        assertThat(payload.roles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("parsing an access token as REFRESH should be rejected")
    void wrong_token_type_rejected() {
        String access = provider.createAccessToken(1L, "u", List.of());
        assertThatThrownBy(() -> provider.parse(access, JwtTokenProvider.TokenType.REFRESH))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("tampering with the token signature surfaces AUTH_TOKEN_INVALID")
    void invalid_signature() {
        String token = provider.createAccessToken(1L, "u", List.of("ADMIN"));
        // Mutate the very last char to break the signature.
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> provider.parse(tampered, JwtTokenProvider.TokenType.ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("garbage input is mapped to AUTH_TOKEN_INVALID, not a generic RuntimeException")
    void garbage_input() {
        assertThatThrownBy(() -> provider.parse("not-a-jwt", JwtTokenProvider.TokenType.ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }
}
