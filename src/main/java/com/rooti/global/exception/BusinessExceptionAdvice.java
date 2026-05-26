package com.rooti.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 도메인 규칙으로 던진 {@link BusinessException} 그리고 어디에서도 잡지 못한 마지막 fallback.
 *
 * <p>로깅 정책: {@code BusinessException} 은 WARN(코드 + 메시지만), 그 외 모든 예외는 ERROR(스택
 * 포함). 트레이스 ID 는 Logback MDC 에서 자동 부착됩니다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class BusinessExceptionAdvice {

    private final ProblemDetailFactory factory;

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn(
                "[Business] {} - {} at {}",
                ex.getErrorCode(),
                ex.getMessage(),
                req.getRequestURI());
        return factory.of(ex.getErrorCode(), ex.getMessage(), req);
    }

    /** 어디에서도 처리되지 않은 진짜 예외 — 운영에서 들여다 봐야 하는 케이스. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("[Unhandled] {} at {}", ex.getMessage(), req.getRequestURI(), ex);
        return factory.of(ErrorCode.INTERNAL_ERROR, ex.getMessage(), req);
    }
}
