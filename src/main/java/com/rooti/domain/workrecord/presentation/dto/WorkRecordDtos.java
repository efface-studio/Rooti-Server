package com.rooti.domain.workrecord.presentation.dto;

import com.rooti.domain.workrecord.domain.WorkProcessRecord;
import com.rooti.domain.workrecord.domain.WorkRecord;
import com.rooti.domain.workrecord.domain.WorkRecord.Type;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

public final class WorkRecordDtos {

    private WorkRecordDtos() {}

    // ---------- coarse "ON/WORK/REST/OFF" record ----------
    @Schema(name = "WorkRecordResponse")
    public record RecordResponse(
            long id, long workScheduleId, Type type, LocalDateTime startAt, LocalDateTime endAt) {
        public static RecordResponse from(WorkRecord r) {
            return new RecordResponse(
                    r.getId(), r.getWorkSchedule().getId(), r.getType(), r.getStartAt(), r.getEndAt());
        }
    }

    @Schema(name = "RecordStartRequest")
    public record StartRequest(@NotNull Long workScheduleId, @NotNull Type type, LocalDateTime at) {}

    @Schema(name = "RecordEndRequest")
    public record EndRequest(@NotNull Long workScheduleId, @NotNull Type type, LocalDateTime at) {}

    // ---------- fine-grain process record ----------
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

    @Schema(name = "ProcessStartRequest")
    public record ProcessStartRequest(
            @NotNull Long workScheduleId,
            @NotNull Long jobProcessId,
            String type,
            LocalDateTime at,
            Short condition,
            String answer,
            String voicePath,
            Map<String, Object> process) {}

    @Schema(name = "ProcessEndRequest")
    public record ProcessEndRequest(
            @NotNull Long workScheduleId,
            @NotNull Long jobProcessId,
            LocalDateTime at,
            Short condition,
            String answer,
            String voicePath,
            Map<String, Object> process) {}
}
