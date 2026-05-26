package com.rooti.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * RFC 7807 {@link ProblemDetail} 응답 빌더.
 *
 * <p>{@code @RestControllerAdvice} 들이 공유하는 단일 구성 지점입니다. {@code type} URI 의
 * 베이스, code / timestamp 같은 표준 확장 필드 채우는 방식 등 표현 규칙을 한 곳에서만 결정합니다.
 */
@Component
public class ProblemDetailFactory {

    private static final URI BASE_TYPE = URI.create("https://docs.rooti.io/errors");

    public ProblemDetail of(ErrorCode code, String message, HttpServletRequest req) {
        return of(code, message, req, null);
    }

    public ProblemDetail of(
            ErrorCode code, String message, HttpServletRequest req, Map<String, ?> extras) {
        HttpStatus status = code.getStatus();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setTitle(code.name());
        pd.setType(BASE_TYPE.resolve("/" + code.name().toLowerCase()));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("code", code.name());
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        if (extras != null) extras.forEach(pd::setProperty);
        return pd;
    }
}
