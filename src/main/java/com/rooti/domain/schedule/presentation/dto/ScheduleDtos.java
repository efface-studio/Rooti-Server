package com.rooti.domain.schedule.presentation.dto;

import com.rooti.domain.schedule.domain.WorkSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ScheduleDtos {

    private ScheduleDtos() {}

    @Schema(name = "ScheduleResponse")
    public record Response(
            long id,
            long jobWorkerId,
            long jobStandardId,
            String jobStandardName,
            Long companyChargerId,
            long workerId,
            String workerName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean makeWorkDoc,
            String workDocPath) {

        public static Response from(WorkSchedule s) {
            return new Response(
                    s.getId(),
                    s.getJobWorker().getId(),
                    s.getJobStandard().getId(),
                    s.getJobStandard().getName(),
                    s.getCompanyCharger() == null ? null : s.getCompanyCharger().getId(),
                    s.getJobWorker().getCompanyWorker().getWorker().getId(),
                    s.getJobWorker().getCompanyWorker().getWorker().getUser().getName(),
                    s.getStartAt(),
                    s.getEndAt(),
                    s.isMakeWorkDoc(),
                    s.getWorkDocPath());
        }
    }

    @Schema(name = "CreateScheduleRequest")
    public record CreateRequest(
            @NotNull Long jobWorkerId,
            Long companyChargerId,
            @NotNull LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean makeWorkDoc) {}

    @Schema(name = "BatchMakeRequest")
    public record BatchMakeRequest(
            @NotNull Long jobStandardId,
            @NotNull LocalDate date,
            Long companyChargerId,
            List<@NotNull Long> jobWorkerIds) {}

    @Schema(name = "CloseScheduleRequest")
    public record CloseRequest(LocalDateTime endAt) {}
}
