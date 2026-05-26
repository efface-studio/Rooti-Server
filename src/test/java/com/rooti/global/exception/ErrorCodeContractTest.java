package com.rooti.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The frontend pattern-matches on {@code ErrorCode.name()}, so each one is a
 * public contract. This test guards against accidental rename / removal —
 * if you genuinely need to change a code, update the test AND the client.
 */
class ErrorCodeContractTest {

    private static final String[] PUBLIC_CODES = {
        "INVALID_INPUT",
        "METHOD_NOT_ALLOWED",
        "RESOURCE_NOT_FOUND",
        "CONFLICT",
        "UNPROCESSABLE",
        "AUTH_INVALID_CREDENTIALS",
        "AUTH_TOKEN_EXPIRED",
        "AUTH_TOKEN_INVALID",
        "AUTH_REFRESH_NOT_FOUND",
        "AUTH_FORBIDDEN",
        "AUTH_ACCOUNT_DISABLED",
        "USER_NOT_FOUND",
        "USER_USERNAME_DUPLICATED",
        "USER_EMAIL_DUPLICATED",
        "COMPANY_NOT_FOUND",
        "WORKER_NOT_FOUND",
        "WORKER_ALREADY_HIRED",
        "CAREGIVER_NOT_FOUND",
        "CAREGIVER_RELATION_DUPLICATED",
        "JOB_STANDARD_NOT_FOUND",
        "JOB_PROCESS_NOT_FOUND",
        "JOB_WORKER_NOT_FOUND",
        "JOB_WORKER_ALREADY_ASSIGNED",
        "WORK_SCHEDULE_NOT_FOUND",
        "WORK_RECORD_NOT_FOUND",
        "WORK_RECORD_OUT_OF_RANGE",
        "DOCUMENT_NOT_FOUND",
        "DOCUMENT_TYPE_NOT_FOUND",
        "STORAGE_UPLOAD_FAILED",
        "STORAGE_FILE_TOO_LARGE",
        "BOARD_NOT_FOUND",
        "NOTIFICATION_SEND_FAILED",
        "INTERNAL_ERROR",
    };

    @Test
    @DisplayName("All public ErrorCode names exist (rename = breaking change for client)")
    void all_public_codes_exist() {
        var existing = Arrays.stream(ErrorCode.values()).map(Enum::name).toList();
        for (String c : PUBLIC_CODES) {
            assertThat(existing).as("ErrorCode.%s should exist", c).contains(c);
        }
    }

    @Test
    @DisplayName("Every ErrorCode carries a non-blank default message and a non-2xx status")
    void each_code_self_consistent() {
        for (ErrorCode c : ErrorCode.values()) {
            assertThat(c.getDefaultMessage()).as("default message of %s", c).isNotBlank();
            assertThat(c.getStatus().is2xxSuccessful())
                    .as("status of %s must NOT be 2xx", c)
                    .isFalse();
        }
    }
}
