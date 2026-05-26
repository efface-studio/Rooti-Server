package com.rooti.domain.job.presentation.dto;

import com.rooti.domain.job.domain.JobStandard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
