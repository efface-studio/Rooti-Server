package com.rooti.domain.worker.presentation.dto;

import com.rooti.domain.worker.domain.CompanyWorker;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CompanyWorkerResponse")
public record CompanyWorkerResponse(
        long id, long companyId, String companyName, Response worker, boolean hired) {
    public static CompanyWorkerResponse from(CompanyWorker cw) {
        return new CompanyWorkerResponse(
                cw.getId(),
                cw.getCompany().getId(),
                cw.getCompany().getName(),
                Response.from(cw.getWorker()),
                cw.isHired());
    }
}
