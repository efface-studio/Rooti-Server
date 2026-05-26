package com.rooti.global.exception;

/** 회원가입 시 username 이 이미 등록되어 있을 때. */
public class UserUsernameDuplicatedException extends BusinessException {

    public UserUsernameDuplicatedException(String username) {
        super(ErrorCode.USER_USERNAME_DUPLICATED, "이미 사용 중인 아이디입니다: " + username);
    }
}
