package com.rooti.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Catalog of every business error the API can emit.
 *
 * <p>Naming convention: {@code <DOMAIN>_<DESCRIPTION>} — domain first so codes group naturally when
 * sorted. The frontend pattern-matches on {@link #name()}, so renames are breaking changes.
 */
@Getter
public enum ErrorCode {

    // ---------- 4xx common ----------
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "리소스 충돌이 발생했습니다."),
    UNPROCESSABLE(HttpStatus.UNPROCESSABLE_ENTITY, "요청을 처리할 수 없습니다."),

    // ---------- Auth ----------
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_REFRESH_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),

    // ---------- User / Member ----------
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_USERNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    // ---------- Company ----------
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "회사를 찾을 수 없습니다."),

    // ---------- Worker ----------
    WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "근로자를 찾을 수 없습니다."),
    WORKER_ALREADY_HIRED(HttpStatus.CONFLICT, "이미 채용된 근로자입니다."),

    // ---------- Caregiver ----------
    CAREGIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "보호자를 찾을 수 없습니다."),
    CAREGIVER_RELATION_DUPLICATED(HttpStatus.CONFLICT, "이미 연결된 보호자-근로자 관계입니다."),

    // ---------- Job ----------
    JOB_STANDARD_NOT_FOUND(HttpStatus.NOT_FOUND, "업무 표준을 찾을 수 없습니다."),
    JOB_PROCESS_NOT_FOUND(HttpStatus.NOT_FOUND, "업무 프로세스를 찾을 수 없습니다."),
    JOB_WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "업무-근로자 매핑을 찾을 수 없습니다."),
    JOB_WORKER_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "이미 해당 근로자에게 할당된 업무입니다."),

    // ---------- Schedule / Record ----------
    WORK_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "근무 일정을 찾을 수 없습니다."),
    WORK_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "근무 기록을 찾을 수 없습니다."),
    WORK_RECORD_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "근무 기록 시간이 일정 범위를 벗어났습니다."),

    // ---------- Document ----------
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."),
    DOCUMENT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "문서 종류를 찾을 수 없습니다."),
    STORAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    STORAGE_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 너무 큽니다."),

    // ---------- Board ----------
    BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),

    // ---------- Notification ----------
    NOTIFICATION_SEND_FAILED(HttpStatus.BAD_GATEWAY, "푸시 전송에 실패했습니다."),

    // ---------- Server ----------
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
