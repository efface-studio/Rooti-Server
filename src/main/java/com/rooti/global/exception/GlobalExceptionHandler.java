package com.rooti.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Translate any uncaught exception into an RFC 7807 {@link ProblemDetail}.
 *
 * <p>Logging policy:
 *
 * <ul>
 *   <li>{@link BusinessException}: WARN with code + message, no stack trace.
 *   <li>Validation/binding errors: WARN with a compact summary.
 *   <li>Everything else: ERROR with full stack trace (these are real bugs or downstream failures).
 * </ul>
 *
 * <p>The MDC {@code traceId} is automatically included by Logback.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI BASE_TYPE = URI.create("https://docs.rooti.io/errors");

    // ---------------------------------------------------------------------
    //  Business
    // ---------------------------------------------------------------------
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn("[Business] {} - {} at {}", ex.getErrorCode(), ex.getMessage(), req.getRequestURI());
        return problem(ex.getErrorCode(), ex.getMessage(), req, null);
    }

    // ---------------------------------------------------------------------
    //  Validation
    // ---------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .collect(
                                Collectors.toMap(
                                        FieldError::getField,
                                        fe ->
                                                fe.getDefaultMessage() == null
                                                        ? "invalid"
                                                        : fe.getDefaultMessage(),
                                        (a, b) -> a,
                                        LinkedHashMap::new));
        log.warn("[Validation] {} field error(s) at {}", fieldErrors.size(), req.getRequestURI());
        return problem(ErrorCode.INVALID_INPUT, ex.getMessage(), req, Map.of("fields", fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> violations =
                ex.getConstraintViolations().stream()
                        .collect(
                                Collectors.toMap(
                                        v -> v.getPropertyPath().toString(),
                                        v -> v.getMessage(),
                                        (a, b) -> a,
                                        LinkedHashMap::new));
        return problem(ErrorCode.INVALID_INPUT, ex.getMessage(), req, Map.of("violations", violations));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("missingParameter", ex.getParameterName());
        return problem(ErrorCode.INVALID_INPUT, ex.getMessage(), req, extra);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return problem(
                ErrorCode.INVALID_INPUT,
                "Parameter '" + ex.getName() + "' has invalid type",
                req,
                null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problem(ErrorCode.INVALID_INPUT, "Malformed JSON body", req, null);
    }

    // ---------------------------------------------------------------------
    //  Routing
    // ---------------------------------------------------------------------
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return problem(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ProblemDetail handleNoHandler(NoHandlerFoundException ex, HttpServletRequest req) {
        return problem(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), req, null);
    }

    // ---------------------------------------------------------------------
    //  Security
    // ---------------------------------------------------------------------
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return problem(ErrorCode.AUTH_INVALID_CREDENTIALS, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return problem(ErrorCode.AUTH_FORBIDDEN, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return problem(ErrorCode.AUTH_TOKEN_INVALID, ex.getMessage(), req, null);
    }

    // ---------------------------------------------------------------------
    //  Fallback
    // ---------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("[Unhandled] {} at {}", ex.getMessage(), req.getRequestURI(), ex);
        return problem(ErrorCode.INTERNAL_ERROR, ex.getMessage(), req, null);
    }

    // ---------------------------------------------------------------------
    //  Helper
    // ---------------------------------------------------------------------
    private ProblemDetail problem(
            ErrorCode code, String message, HttpServletRequest req, Map<String, ?> extras) {
        HttpStatus status = code.getStatus();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setTitle(code.name());
        pd.setType(BASE_TYPE.resolve("/" + code.name().toLowerCase()));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("code", code.name());
        pd.setProperty("timestamp", java.time.OffsetDateTime.now().toString());
        if (extras != null) {
            extras.forEach(pd::setProperty);
        }
        return pd;
    }
}
