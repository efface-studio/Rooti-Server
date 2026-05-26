package com.rooti.global.exception;

/** 회사 ID 로 찾을 수 없을 때. */
public class CompanyNotFoundException extends BusinessException {

    public CompanyNotFoundException(long companyId) {
        super(ErrorCode.COMPANY_NOT_FOUND, "회사를 찾을 수 없습니다 (id=" + companyId + ")");
    }
}
