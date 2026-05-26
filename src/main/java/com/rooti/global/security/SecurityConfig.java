package com.rooti.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.cors.CorsFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Single source of truth for the HTTP security wiring.
 *
 * <p>Strategy:
 *
 * <ul>
 *   <li>Stateless — no session, no CSRF (JWT only).
 *   <li>Public paths come from {@link SecurityProperties#publicPaths()}.
 *   <li>JSON ProblemDetail responses for 401/403 to match {@link com.rooti.global.exception.GlobalExceptionHandler}.
 *   <li>Method-level {@code @PreAuthorize} enabled for fine-grained role checks.
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new org.springframework.security.authentication.ProviderManager(List.of(provider));
    }

    @Bean
    public CorsFilter corsFilter() {
        SecurityProperties.Cors cors = securityProperties.cors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(cors.allowedMethods());
        config.setAllowedHeaders(cors.allowedHeaders());
        config.setExposedHeaders(cors.exposedHeaders());
        config.setAllowCredentials(cors.allowCredentials());
        config.setMaxAge(cors.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] publicPaths = securityProperties.publicPaths().toArray(new String[0]);

        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(corsFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (req, res, e) -> writeProblem(res, propagated(req, e, ErrorCode.AUTH_TOKEN_INVALID)))
                                        .accessDeniedHandler(
                                                (req, res, e) -> writeProblem(res, build(ErrorCode.AUTH_FORBIDDEN, e.getMessage()))))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                        .requestMatchers(publicPaths).permitAll()
                                        .anyRequest().authenticated());

        return http.build();
    }

    // ----- helpers --------------------------------------------------------------
    private ProblemDetail propagated(
            jakarta.servlet.http.HttpServletRequest req,
            org.springframework.security.core.AuthenticationException e,
            ErrorCode fallback) {
        // JwtAuthenticationFilter may have attached the underlying cause for richer messaging.
        Object stored = req.getAttribute("rooti.auth.error");
        if (stored instanceof BusinessException be) {
            return build(be.getErrorCode(), be.getMessage());
        }
        return build(fallback, e.getMessage());
    }

    private ProblemDetail build(ErrorCode code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.getStatus(), message);
        pd.setTitle(code.name());
        pd.setType(URI.create("https://docs.rooti.io/errors/" + code.name().toLowerCase()));
        pd.setProperty("code", code.name());
        pd.setProperty("timestamp", java.time.OffsetDateTime.now().toString());
        return pd;
    }

    private void writeProblem(HttpServletResponse res, ProblemDetail pd) throws IOException {
        res.setStatus(pd.getStatus());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(), pd);
    }
}
