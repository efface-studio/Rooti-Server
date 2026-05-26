package com.rooti.domain.job.application;

import com.rooti.domain.job.domain.JobStandard;
import com.rooti.domain.job.domain.JobWorker;
import com.rooti.domain.job.infrastructure.JobWorkerRepository;
import com.rooti.domain.job.presentation.dto.JobDtos.JobWorkerResponse;
import com.rooti.domain.worker.domain.CompanyWorker;
import com.rooti.domain.worker.infrastructure.CompanyWorkerRepository;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assigning / un-assigning workers to a {@link JobStandard}. */
@Service
@RequiredArgsConstructor
@Transactional
public class JobWorkerService {

    private final JobWorkerRepository jobWorkerRepository;
    private final CompanyWorkerRepository companyWorkerRepository;
    private final JobStandardService jobStandardService;

    public JobWorkerResponse assign(long companyWorkerId, long jobStandardId) {
        CompanyWorker cw =
                companyWorkerRepository
                        .findById(companyWorkerId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.WORKER_NOT_FOUND));
        JobStandard standard = jobStandardService.getOrThrow(jobStandardId);

        JobWorker jw =
                jobWorkerRepository
                        .findByCompanyWorkerIdAndJobStandardId(companyWorkerId, jobStandardId)
                        .map(
                                existing -> {
                                    if (existing.isUseFlag()) {
                                        throw new BusinessException(
                                                ErrorCode.JOB_WORKER_ALREADY_ASSIGNED);
                                    }
                                    existing.reassign();
                                    return existing;
                                })
                        .orElseGet(() -> jobWorkerRepository.save(JobWorker.assign(cw, standard)));
        return JobWorkerResponse.from(jw);
    }

    public void unassign(long jobWorkerId) {
        JobWorker jw =
                jobWorkerRepository
                        .findById(jobWorkerId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_WORKER_NOT_FOUND));
        jw.unassign();
    }

    @Transactional(readOnly = true)
    public List<JobWorkerResponse> listByJobStandard(long jobStandardId) {
        return jobWorkerRepository.findAllByJobStandardIdAndUseFlagTrue(jobStandardId).stream()
                .map(JobWorkerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobWorkerResponse> listActiveByCompanyWorker(long companyWorkerId) {
        return jobWorkerRepository.findActiveByCompanyWorker(companyWorkerId).stream()
                .map(JobWorkerResponse::from)
                .toList();
    }
}
