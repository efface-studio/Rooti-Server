package com.rooti.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/** URL 매칭 / HTTP 메서드 매칭 단계에서 떨어지는 예외만 전담. */
@RestControllerAdvice
@RequiredArgsConstructor
public class RoutingExceptionAdvice {

    private final ProblemDetailFactory factory;

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return factory.of(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), req);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ProblemDetail handleNoHandler(NoHandlerFoundException ex, HttpServletRequest req) {
        return factory.of(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), req);
    }
}
