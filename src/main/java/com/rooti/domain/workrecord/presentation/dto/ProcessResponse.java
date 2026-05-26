package com.rooti.domain.workrecord.presentation.dto;

import com.rooti.domain.workrecord.domain.WorkProcessRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(name = "ProcessRecordResponse")
public record ProcessResponse(
        long id,
        long workScheduleId,
        long jobProcessId,
        String jobProcessName,
        String type,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Short startCondition,
        Short endCondition,
        String startAnswer,
        String endAnswer,
        String startVoicePath,
        String endVoicePath,
        Map<String, Object> process) {
    public static ProcessResponse from(WorkProcessRecord r) {
        return new ProcessResponse(
                r.getId(),
                r.getWorkSchedule().getId(),
                r.getJobProcess().getId(),
                r.getJobProcess().getName(),
                r.getType(),
                r.getStartAt(),
                r.getEndAt(),
                r.getStartCondition(),
                r.getEndCondition(),
                r.getStartAnswer(),
                r.getEndAnswer(),
                r.getStartVoicePath(),
                r.getEndVoicePath(),
                r.getProcess());
    }
}
