package com.rooti.domain.job.presentation.dto;

import com.rooti.domain.job.domain.JobProcess;
import com.rooti.domain.job.domain.JobStandard;
import com.rooti.domain.job.domain.JobWorker;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class JobDtos {

    private JobDtos() {}

    // ----- JobStandard ---------------------------------------------------------
    @Schema(name = "JobStandardResponse")
    public record StandardResponse(
            long id,
            long companyId,
            String companyName,
            String name,
            boolean useFlag,
            LocalTime routineStartTime,
            int standardWorkTimeSeconds,
            int standardRestTimeSeconds,
            String startMessage,
            String endMessage,
            Map<String, Object> context,
            boolean forJournal,
            List<ProcessResponse> processes) {

        public static StandardResponse from(JobStandard j) {
            return new StandardResponse(
                    j.getId(),
                    j.getCompany().getId(),
                    j.getCompany().getName(),
                    j.getName(),
                    j.isUseFlag(),
                    j.getRoutineStartTime(),
                    j.getStandardWorkTimeSeconds(),
                    j.getStandardRestTimeSeconds(),
                    j.getStartMessage(),
                    j.getEndMessage(),
                    j.getContext(),
                    j.isForJournal(),
                    j.getProcesses().stream().map(ProcessResponse::from).toList());
        }

        public static StandardResponse summary(JobStandard j) {
            return new StandardResponse(
                    j.getId(),
                    j.getCompany().getId(),
                    j.getCompany().getName(),
                    j.getName(),
                    j.isUseFlag(),
                    j.getRoutineStartTime(),
                    j.getStandardWorkTimeSeconds(),
                    j.getStandardRestTimeSeconds(),
                    j.getStartMessage(),
                    j.getEndMessage(),
                    j.getContext(),
                    j.isForJournal(),
                    List.of());
        }
    }

    @Schema(name = "JobStandardCreateRequest")
    public record StandardCreateRequest(
            @NotNull Long companyId,
            @NotBlank @Size(max = 200) String name,
            LocalTime routineStartTime,
            @PositiveOrZero Integer standardWorkTimeSeconds,
            @PositiveOrZero Integer standardRestTimeSeconds,
            String startMessage,
            String endMessage,
            Map<String, Object> context,
            Boolean forJournal,
            @Valid List<ProcessUpsertRequest> processes) {}

    @Schema(name = "JobStandardUpdateRequest")
    public record StandardUpdateRequest(
            @Size(max = 200) String name,
            LocalTime routineStartTime,
            @PositiveOrZero Integer standardWorkTimeSeconds,
            @PositiveOrZero Integer standardRestTimeSeconds,
            String startMessage,
            String endMessage,
            Map<String, Object> context,
            Boolean forJournal) {}

    // ----- JobProcess ----------------------------------------------------------
    @Schema(name = "JobProcessResponse")
    public record ProcessResponse(
            long id,
            String name,
            int sequence,
            String videoPath,
            String startMessage,
            String endMessage,
            Map<String, Object> context,
            int processTimeSeconds) {
        public static ProcessResponse from(JobProcess p) {
            return new ProcessResponse(
                    p.getId(),
                    p.getName(),
                    p.getSequence(),
                    p.getVideoPath(),
                    p.getStartMessage(),
                    p.getEndMessage(),
                    p.getContext(),
                    p.getProcessTimeSeconds());
        }
    }

    @Schema(name = "ProcessUpsertRequest")
    public record ProcessUpsertRequest(
            Long id,
            @NotBlank @Size(max = 200) String name,
            @PositiveOrZero int sequence,
            String videoPath,
            String startMessage,
            String endMessage,
            Map<String, Object> context,
            @PositiveOrZero int processTimeSeconds) {}

    // ----- JobWorker (assignment) ---------------------------------------------
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

    @Schema(name = "AssignJobWorkerRequest")
    public record AssignRequest(@NotNull Long companyWorkerId, @NotNull Long jobStandardId) {}
}
