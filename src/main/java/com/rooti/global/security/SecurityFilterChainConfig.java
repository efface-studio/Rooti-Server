package com.rooti.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.exception.ProblemDetailFactory;
import com.rooti.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

/**
 * HTTP 보안 필터 체인.
 *
 * <p>전략:
 *
 * <ul>
 *   <li>Stateless — 세션 없음, CSRF 끔 (JWT 단독)
 *   <li>공개 경로는 {@link SecurityProperties#publicPaths()} 에서 주입
 *   <li>401/403 응답은 {@link ProblemDetailFactory} 를 거쳐 RFC 7807 JSON 으로 통일
 *   <li>메서드 단 {@code @PreAuthorize} 사용을 위해 {@code @EnableMethodSecurity}
 * </ul>
 *
 * <p>다른 설정(PasswordEncoder / AuthenticationManager / Cors) 은 각각의 전용
 * {@code @Configuration} 으로 분리되어 있어, 본 파일에는 필터 체인 정의만 남습니다.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityFilterChainConfig {

    private static final String AUTH_ERROR_ATTR = "rooti.auth.error";

    private final SecurityProperties securityProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsFilter corsFilter;
    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] publicPaths = securityProperties.publicPaths().toArray(new String[0]);

        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(this::onAuthenticationError)
                                        .accessDeniedHandler(this::onAccessDenied))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers(publicPaths)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated());

        return http.build();
    }

    // ----- entry-point handlers ---------------------------------------------
    /**
     * 401 — 토큰이 없거나 만료. {@link JwtAuthenticationFilter} 가 더 구체적인
     * {@link BusinessException} 을 request attribute 에 심어두면 그 코드를 우선 사용합니다.
     */
    private void onAuthenticationError(
            HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        Object stored = req.getAttribute(AUTH_ERROR_ATTR);
        if (stored instanceof BusinessException be) {
            writeProblem(res, problemDetailFactory.of(be.getErrorCode(), be.getMessage(), req));
            return;
        }
        writeProblem(res, problemDetailFactory.of(ErrorCode.AUTH_TOKEN_INVALID, ex.getMessage(), req));
    }

    /** 403 — 토큰은 유효하나 권한 부족. */
    private void onAccessDenied(
            HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex)
            throws IOException {
        writeProblem(res, problemDetailFactory.of(ErrorCode.AUTH_FORBIDDEN, ex.getMessage(), req));
    }

    private void writeProblem(HttpServletResponse res, ProblemDetail pd) throws IOException {
        res.setStatus(pd.getStatus());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(), pd);
    }
}
