package com.rooti.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Security 가 던지는 인증 / 인가 예외를 Rooti 의 표준 ErrorCode 로 정규화합니다.
 *
 * <p>구체 메시지를 그대로 노출하지 않고 단순 코드 + 영어 메시지만 돌려줘, 공격자에게 시스템 내부
 * 정보를 흘리지 않도록 합니다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class SecurityExceptionAdvice {

    private final ProblemDetailFactory factory;

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return factory.of(ErrorCode.AUTH_INVALID_CREDENTIALS, ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return factory.of(ErrorCode.AUTH_FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return factory.of(ErrorCode.AUTH_TOKEN_INVALID, ex.getMessage(), req);
    }
}
