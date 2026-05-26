package com.rooti.domain.job.presentation.dto;

import com.rooti.domain.job.domain.JobWorker;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "JobWorkerResponse")
public record JobWorkerResponse(
        long id,
        long jobStandardId,
        String jobStandardName,
        long companyWorkerId,
        long workerId,
        String workerName,
        boolean useFlag) {

    public static JobWorkerResponse from(JobWorker jw) {
        return new JobWorkerResponse(
                jw.getId(),
                jw.getJobStandard().getId(),
                jw.getJobStandard().getName(),
                jw.getCompanyWorker().getId(),
                jw.getCompanyWorker().getWorker().getId(),
                jw.getCompanyWorker().getWorker().getUser().getName(),
                jw.isUseFlag());
    }
}
