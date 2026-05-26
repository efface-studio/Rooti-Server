package com.rooti.domain.schedule.application;

import com.rooti.domain.company.infrastructure.CompanyChargerRepository;
import com.rooti.domain.job.application.JobStandardService;
import com.rooti.domain.job.domain.JobStandard;
import com.rooti.domain.job.domain.JobWorker;
import com.rooti.domain.job.infrastructure.JobWorkerRepository;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.schedule.infrastructure.WorkScheduleRepository;
import com.rooti.domain.schedule.presentation.dto.ScheduleDtos.BatchMakeRequest;
import com.rooti.domain.schedule.presentation.dto.ScheduleDtos.CreateRequest;
import com.rooti.domain.schedule.presentation.dto.ScheduleDtos.Response;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.response.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkScheduleService {

    private final WorkScheduleRepository scheduleRepository;
    private final JobWorkerRepository jobWorkerRepository;
    private final JobStandardService jobStandardService;
    private final CompanyChargerRepository chargerRepository;

    public Response create(CreateRequest req) {
        JobWorker jw =
                jobWorkerRepository
                        .findById(req.jobWorkerId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_WORKER_NOT_FOUND));
        WorkSchedule schedule =
                WorkSchedule.builder()
                        .jobWorker(jw)
                        .jobStandard(jw.getJobStandard())
                        .companyCharger(
                                req.companyChargerId() == null
                                        ? null
                                        : chargerRepository
                                                .findById(req.companyChargerId())
                                                .orElseThrow(
                                                        () ->
                                                                new BusinessException(
                                                                        ErrorCode
                                                                                .RESOURCE_NOT_FOUND)))
                        .startAt(req.startAt())
                        .endAt(req.endAt())
                        .makeWorkDoc(req.makeWorkDoc())
                        .build();
        return Response.from(scheduleRepository.save(schedule));
    }

    /**
     * Bulk-create schedules for a standard on a given date. Honors the standard's
     * {@code routineStartTime} when present, otherwise defaults to 09:00 local.
     */
    public List<Response> batchMake(BatchMakeRequest req) {
        JobStandard std = jobStandardService.getOrThrow(req.jobStandardId());
        LocalTime defaultStart = std.getRoutineStartTime() != null ? std.getRoutineStartTime() : LocalTime.of(9, 0);
        LocalDateTime start = LocalDateTime.of(req.date(), defaultStart);

        List<JobWorker> targets =
                req.jobWorkerIds() == null || req.jobWorkerIds().isEmpty()
                        ? jobWorkerRepository.findAllByJobStandardIdAndUseFlagTrue(std.getId())
                        : jobWorkerRepository.findAllById(req.jobWorkerIds());

        List<WorkSchedule> created = new ArrayList<>();
        for (JobWorker jw : targets) {
            created.add(
                    scheduleRepository.save(
                            WorkSchedule.builder()
                                    .jobWorker(jw)
                                    .jobStandard(std)
                                    .companyCharger(
                                            req.companyChargerId() == null
                                                    ? null
                                                    : chargerRepository
                                                            .findById(req.companyChargerId())
                                                            .orElse(null))
                                    .startAt(start)
                                    .makeWorkDoc(false)
                                    .build()));
        }
        return created.stream().map(Response::from).toList();
    }

    public Response close(long scheduleId, LocalDateTime endAt) {
        WorkSchedule s = getOrThrow(scheduleId);
        s.close(endAt == null ? LocalDateTime.now() : endAt);
        return Response.from(s);
    }

    @Transactional(readOnly = true)
    public Response get(long id) {
        return Response.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<Response> listForJobWorker(long jobWorkerId, LocalDate from, LocalDate to) {
        LocalDateTime f = from.atStartOfDay();
        LocalDateTime t = to.plusDays(1).atStartOfDay();
        return scheduleRepository.findInRange(jobWorkerId, f, t).stream()
                .map(Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<Response> listForStandard(
            long jobStandardId, LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.of(
                scheduleRepository
                        .findByStandardInRange(
                                jobStandardId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), pageable)
                        .map(Response::from));
    }

    public WorkSchedule getOrThrow(long id) {
        return scheduleRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND));
    }
}
