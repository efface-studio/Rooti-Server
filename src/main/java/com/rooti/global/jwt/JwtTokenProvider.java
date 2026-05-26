package com.rooti.global.jwt;

import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.security.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issuance and validation of JWT access/refresh tokens.
 *
 * <p>HS256 with a server-side secret. The secret <strong>must</strong> be at least 512 bits — jjwt
 * refuses to start otherwise. Tokens carry: subject = user-id, claims {@code roles}, {@code type}.
 *
 * <p>Refresh tokens are persisted in Redis (see {@link com.rooti.domain.auth.infrastructure.RefreshTokenStore})
 * so that "logout everywhere" and forced revocation are O(1).
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_USERNAME = "usn";

    public enum TokenType {
        ACCESS,
        REFRESH
    }

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenProvider(SecurityProperties props) {
        byte[] keyBytes = Decoders.BASE64.decode(props.jwt().secret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = props.jwt().issuer();
        this.accessTtl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
        this.refreshTtl = Duration.ofDays(props.jwt().refreshTtlDays());
    }

    // ---------------------------------------------------------------------
    //  Issuance
    // ---------------------------------------------------------------------
    public String createAccessToken(long userId, String username, List<String> roles) {
        return create(TokenType.ACCESS, userId, username, roles, accessTtl);
    }

    public String createRefreshToken(long userId, String username, List<String> roles) {
        return create(TokenType.REFRESH, userId, username, roles, refreshTtl);
    }

    private String create(
            TokenType type, long userId, String username, List<String> roles, Duration ttl) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TYPE, type.name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttl.toMillis()))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    // ---------------------------------------------------------------------
    //  Verification
    // ---------------------------------------------------------------------
    public JwtPayload parse(String token, TokenType expected) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            Claims c = jws.getPayload();

            String typ = c.get(CLAIM_TYPE, String.class);
            if (typ == null || !typ.equals(expected.name())) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "Unexpected token type");
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) c.get(CLAIM_ROLES);
            return new JwtPayload(
                    Long.parseLong(c.getSubject()),
                    c.get(CLAIM_USERNAME, String.class),
                    roles == null ? List.of() : roles);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED, "Token expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "Invalid token", e);
        }
    }
}
