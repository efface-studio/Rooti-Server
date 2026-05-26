package com.rooti.global.exception;

import lombok.Getter;

/**
 * The single checked-meaning, unchecked-type exception used across the application.
 *
 * <p>Throw this anywhere in the domain/application layer with an {@link ErrorCode}; the global
 * handler will translate it into an RFC 7807 response. Stack traces from these are <em>not</em>
 * logged at ERROR level because they're expected business outcomes, not bugs.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
