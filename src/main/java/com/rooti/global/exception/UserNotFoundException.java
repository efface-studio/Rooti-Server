package com.rooti.global.exception;

/**
 * 주어진 ID 의 사용자를 찾을 수 없을 때 던지는 예외.
 *
 * <p>{@code throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자가 없습니다")} 같은 익명
 * 호출 대신 의미를 가진 타입으로 던집니다. 호출자가 catch 할 때, 로그 grep 할 때, 테스트 작성 시
 * 어떤 케이스인지 코드만 봐도 즉시 식별됩니다.
 */
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(long userId) {
        super(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다 (id=" + userId + ")");
    }
}
