package com.rooti.domain.job.application;

import com.rooti.domain.company.application.CompanyService;
import com.rooti.domain.company.domain.Company;
import com.rooti.domain.job.domain.JobProcess;
import com.rooti.domain.job.domain.JobStandard;
import com.rooti.domain.job.infrastructure.JobStandardRepository;
import com.rooti.domain.job.presentation.dto.ProcessUpsertRequest;
import com.rooti.domain.job.presentation.dto.StandardCreateRequest;
import com.rooti.domain.job.presentation.dto.StandardResponse;
import com.rooti.domain.job.presentation.dto.StandardUpdateRequest;
import com.rooti.global.config.CacheConfig;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class JobStandardService {

    private final JobStandardRepository jobStandardRepository;
    private final CompanyService companyService;

    public StandardResponse create(StandardCreateRequest req) {
        Company company = companyService.getOrThrow(req.companyId());
        JobStandard standard =
                JobStandard.builder()
                        .company(company)
                        .name(req.name())
                        .useFlag(true)
                        .routineStartTime(req.routineStartTime())
                        .standardWorkTimeSeconds(req.standardWorkTimeSeconds())
                        .standardRestTimeSeconds(req.standardRestTimeSeconds())
                        .startMessage(req.startMessage())
                        .endMessage(req.endMessage())
                        .context(req.context())
                        .forJournal(req.forJournal())
                        .build();

        if (req.processes() != null) {
            for (ProcessUpsertRequest p : req.processes()) {
                standard.addProcess(
                        p.name(),
                        p.sequence(),
                        p.videoPath(),
                        p.processTimeSeconds(),
                        p.startMessage(),
                        p.endMessage(),
                        p.context());
            }
        }
        return StandardResponse.from(jobStandardRepository.save(standard));
    }

    @CacheEvict(cacheNames = CacheConfig.JOB_STANDARDS, key = "#id")
    public StandardResponse update(long id, StandardUpdateRequest req) {
        JobStandard s = getOrThrow(id);
        s.updateBasic(
                req.name(),
                req.routineStartTime(),
                req.standardWorkTimeSeconds(),
                req.standardRestTimeSeconds(),
                req.startMessage(),
                req.endMessage(),
                req.forJournal(),
                req.context());
        return StandardResponse.from(s);
    }

    @CacheEvict(cacheNames = CacheConfig.JOB_STANDARDS, key = "#id")
    public void deactivate(long id) {
        getOrThrow(id).deactivate();
    }

    @CacheEvict(cacheNames = CacheConfig.JOB_STANDARDS, key = "#id")
    public StandardResponse syncProcesses(long id, List<ProcessUpsertRequest> processes) {
        JobStandard s = getOrThrow(id);
        // Idempotent sync — update existing by id, add new (id == null), drop missing
        Map<Long, ProcessUpsertRequest> incomingById =
                processes.stream()
                        .filter(p -> p.id() != null)
                        .collect(Collectors.toMap(ProcessUpsertRequest::id, p -> p));

        s.getProcesses()
                .removeIf(
                        existing ->
                                existing.getId() != null
                                        && !incomingById.containsKey(existing.getId()));

        for (ProcessUpsertRequest p : processes) {
            if (p.id() == null) {
                s.addProcess(
                        p.name(),
                        p.sequence(),
                        p.videoPath(),
                        p.processTimeSeconds(),
                        p.startMessage(),
                        p.endMessage(),
                        p.context());
            } else {
                JobProcess existing =
                        s.getProcesses().stream()
                                .filter(pr -> p.id().equals(pr.getId()))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new BusinessException(
                                                        ErrorCode.JOB_PROCESS_NOT_FOUND));
                existing.update(
                        p.name(),
                        p.sequence(),
                        p.videoPath(),
                        p.startMessage(),
                        p.endMessage(),
                        p.context(),
                        p.processTimeSeconds());
            }
        }
        return StandardResponse.from(s);
    }

    /**
     * 업무 표준 + 프로세스 목록 (fetch-join) 단건 조회.
     *
     * <p>스케줄·근무일지·근로자 화면 모두에서 거의 화면 진입마다 호출되는 hot read 이고,
     * 데이터는 거의 안 바뀝니다 → Redis 캐시 적용. JOIN 한 번 줄이면 인스턴스의 read IOPS 가
     * 그대로 빠지므로 RDS storage 등급(gp3 vs io1) 선택의 여유가 커집니다.
     */
    @Cacheable(cacheNames = CacheConfig.JOB_STANDARDS, key = "#id")
    @Transactional(readOnly = true)
    public StandardResponse get(long id) {
        return StandardResponse.from(
                jobStandardRepository
                        .findWithProcesses(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_STANDARD_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public PageResponse<StandardResponse> search(Long companyId, String keyword, Pageable pageable) {
        return PageResponse.of(
                jobStandardRepository
                        .search(companyId, keyword, pageable)
                        .map(StandardResponse::summary));
    }

    public JobStandard getOrThrow(long id) {
        return jobStandardRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_STANDARD_NOT_FOUND));
    }
}
