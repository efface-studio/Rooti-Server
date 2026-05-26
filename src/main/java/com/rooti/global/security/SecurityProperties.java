package com.rooti.global.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the {@code rooti.security.*} property tree.
 *
 * <p>Kept as a record to make the values immutable and self-documenting. The {@link Jwt} settings
 * are passed to {@link com.rooti.global.jwt.JwtTokenProvider}; {@link Cors} settings are consumed
 * by both {@link com.rooti.global.config.WebConfig} and the Spring Security filter chain.
 */
@ConfigurationProperties(prefix = "rooti.security")
public record SecurityProperties(Jwt jwt, Cors cors, List<String> publicPaths) {

    public record Jwt(
            String secret,
            long accessTtlMinutes,
            long refreshTtlDays,
            String issuer,
            String headerName,
            String tokenPrefix) {}

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            List<String> exposedHeaders,
            boolean allowCredentials,
            long maxAge) {}
}
