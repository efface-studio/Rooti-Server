package com.rooti.global.exception;

/** 비활성화된 계정으로 로그인 시도. */
public class AuthAccountDisabledException extends BusinessException {

    public AuthAccountDisabledException() {
        super(ErrorCode.AUTH_ACCOUNT_DISABLED, "비활성화된 계정입니다. 관리자에게 문의하세요.");
    }
}
