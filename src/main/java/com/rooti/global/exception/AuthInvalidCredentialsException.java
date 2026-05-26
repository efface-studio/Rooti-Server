package com.rooti.global.exception;

/**
 * 로그인 시 username/password 불일치 또는 사용자 미존재.
 *
 * <p>"존재하지 않는 사용자" 와 "비밀번호 불일치" 를 외부적으로 구별하지 않습니다 — username
 * enumeration 공격을 피하기 위함입니다.
 */
public class AuthInvalidCredentialsException extends BusinessException {

    public AuthInvalidCredentialsException() {
        super(ErrorCode.AUTH_INVALID_CREDENTIALS, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
