package com.rooti.global.exception;

/** 회원가입 시 이메일이 이미 등록되어 있을 때. */
public class UserEmailDuplicatedException extends BusinessException {

    public UserEmailDuplicatedException(String email) {
        super(ErrorCode.USER_EMAIL_DUPLICATED, "이미 사용 중인 이메일입니다: " + email);
    }
}
