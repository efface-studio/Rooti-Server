package com.rooti.global.exception;

/**
 * Refresh 토큰이 store 와 불일치 — 만료, 폐기, 재생(replay) 중 하나.
 *
 * <p>외부적으로는 "찾을 수 없음" 으로 일관되게 응답해 어느 경우인지 정보를 흘리지 않습니다.
 */
public class AuthRefreshNotFoundException extends BusinessException {

    public AuthRefreshNotFoundException() {
        super(ErrorCode.AUTH_REFRESH_NOT_FOUND, "리프레시 토큰이 만료되었거나 폐기되었습니다.");
    }
}
