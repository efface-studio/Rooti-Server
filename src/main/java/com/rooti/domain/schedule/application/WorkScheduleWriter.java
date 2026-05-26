package com.rooti.domain.schedule.application;

import com.rooti.domain.company.infrastructure.CompanyChargerRepository;
import com.rooti.domain.job.application.JobStandardService;
import com.rooti.domain.job.domain.JobStandard;
import com.rooti.domain.job.domain.JobWorker;
import com.rooti.domain.job.infrastructure.JobWorkerRepository;
import com.rooti.domain.schedule.application.event.WorkScheduleClosedEvent;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.schedule.infrastructure.WorkScheduleRepository;
import com.rooti.domain.schedule.presentation.dto.BatchMakeRequest;
import com.rooti.domain.schedule.presentation.dto.CreateRequest;
import com.rooti.domain.schedule.presentation.dto.Response;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근무 스케줄 도메인의 <strong>변경 전용</strong> 진입점.
 *
 * <p>create / batch / close 같은 상태 변경 use case 를 모아두고, 조회는 {@link
 * WorkScheduleReader} 를 통해 합성합니다. 이 분리는 두 가지 실질적 이점이 있습니다:
 *
 * <ul>
 *   <li>읽기 트랜잭션(@Transactional(readOnly=true)) 과 쓰기 트랜잭션 경계가 코드 구조에서
 *       그대로 드러납니다 — 한 클래스가 두 모드를 메서드별로 섞어 갖지 않습니다.
 *   <li>변경 use case 가 늘어나도 Reader 의 메서드는 영향을 받지 않아, 캐싱 / 인덱스 힌트 등
 *       조회 최적화를 자유롭게 적용할 수 있습니다.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Transactional
public class WorkScheduleWriter {

    private final WorkScheduleRepository scheduleRepository;
    private final WorkScheduleReader scheduleReader;
    private final JobWorkerRepository jobWorkerRepository;
    private final JobStandardService jobStandardService;
    private final CompanyChargerRepository chargerRepository;
    private final ApplicationEventPublisher events;

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
                                                        () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)))
                        .startAt(req.startAt())
                        .endAt(req.endAt())
                        .makeWorkDoc(req.makeWorkDoc())
                        .build();
        return Response.from(scheduleRepository.save(schedule));
    }

    /**
     * 한 업무 표준 + 한 날짜 기준으로 모든 근로자(또는 지정 근로자) 의 스케줄을 한 번에 생성합니다.
     * 표준의 {@code routineStartTime} 이 있으면 그 시각으로, 없으면 09:00 으로 기본 시작.
     */
    public List<Response> batchMake(BatchMakeRequest req) {
        JobStandard std = jobStandardService.getOrThrow(req.jobStandardId());
        LocalTime defaultStart =
                std.getRoutineStartTime() != null ? std.getRoutineStartTime() : LocalTime.of(9, 0);
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
        WorkSchedule s = scheduleReader.getOrThrow(scheduleId);
        LocalDateTime actualClosedAt = endAt == null ? LocalDateTime.now() : endAt;
        s.close(actualClosedAt);
        // commit 이후 알림·정산·근무일지 자동 발송 등 cross-domain 사이드 이펙트가 트리거될 수
        // 있도록 이벤트를 발행합니다. 리스너는 AFTER_COMMIT 단계에서 비동기로 실행되어,
        // close 트랜잭션 자체의 지연이나 실패에 영향을 주지 않습니다.
        events.publishEvent(
                new WorkScheduleClosedEvent(
                        s.getId(),
                        s.getJobWorker().getCompanyWorker().getWorker().getId(),
                        s.getJobStandard().getId(),
                        actualClosedAt));
        return Response.from(s);
    }
}
