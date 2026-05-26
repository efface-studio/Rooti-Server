package com.rooti.global.jwt;

import com.rooti.global.exception.BusinessException;
import com.rooti.global.security.PrincipalDetails;
import com.rooti.global.security.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pulls a JWT out of the {@code Authorization} header (or legacy {@code accesstoken} header /
 * {@code accessToken} cookie for back-compat with the Django app), validates it, and writes a
 * {@link PrincipalDetails} into the {@code SecurityContext}.
 *
 * <p>This filter is intentionally permissive on failure — it never short-circuits with a 401
 * itself. Instead, downstream {@code SecurityFilterChain} entry points (the unauthorized handler)
 * decide what to do. That makes the API consistent: missing auth on a protected route → 401,
 * missing auth on a public route → 200.
 *
 * <p>It also seeds a per-request {@code traceId} into MDC so logs across the chain correlate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String LEGACY_HEADER = "accesstoken";
    private static final String LEGACY_COOKIE = "accessToken";

    private final JwtTokenProvider tokenProvider;
    private final SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token)) {
                authenticate(token, request);
            }
            chain.doFilter(request, response);
        } catch (BusinessException ex) {
            // Let it bubble up so AccessDeniedHandler/AuthenticationEntryPoint can write
            // a consistent ProblemDetail response.
            SecurityContextHolder.clearContext();
            request.setAttribute("rooti.auth.error", ex);
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void authenticate(String token, HttpServletRequest request) {
        JwtPayload payload = tokenProvider.parse(token, JwtTokenProvider.TokenType.ACCESS);
        PrincipalDetails principal =
                new PrincipalDetails(
                        payload.userId(),
                        payload.username(),
                        payload.roles(),
                        true,
                        true);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(securityProperties.jwt().headerName());
        String prefix = securityProperties.jwt().tokenPrefix();
        if (StringUtils.hasText(header) && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        // legacy header (Django parity)
        String legacy = request.getHeader(LEGACY_HEADER);
        if (StringUtils.hasText(legacy)) {
            return legacy.trim();
        }
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (LEGACY_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
