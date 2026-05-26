package com.rooti.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Bean Validation 실패, 잘못된 JSON, 누락된 파라미터, 타입 불일치 등 입력 검증 단계 예외 전담.
 *
 * <p>모든 케이스가 동일한 {@link ErrorCode#INVALID_INPUT} 으로 정규화되며, 가능한 한 어느 필드가
 * 어떻게 잘못됐는지 ProblemDetail extension 으로 함께 돌려줍니다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ValidationExceptionAdvice {

    private final ProblemDetailFactory factory;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .collect(
                                Collectors.toMap(
                                        FieldError::getField,
                                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                                        (a, b) -> a,
                                        LinkedHashMap::new));
        log.warn(
                "[Validation] {} field error(s) at {}",
                fieldErrors.size(),
                req.getRequestURI());
        return factory.of(ErrorCode.INVALID_INPUT, ex.getMessage(), req, Map.of("fields", fieldErrors));
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
        return factory.of(ErrorCode.INVALID_INPUT, ex.getMessage(), req, Map.of("violations", violations));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("missingParameter", ex.getParameterName());
        return factory.of(ErrorCode.INVALID_INPUT, ex.getMessage(), req, extra);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return factory.of(
                ErrorCode.INVALID_INPUT,
                "Parameter '" + ex.getName() + "' has invalid type",
                req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return factory.of(ErrorCode.INVALID_INPUT, "Malformed JSON body", req);
    }
}
