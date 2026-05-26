package com.rooti.domain.workrecord.presentation.dto;

import com.rooti.domain.workrecord.domain.WorkRecord;
import com.rooti.domain.workrecord.domain.WorkRecord.Type;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "WorkRecordResponse")
public record RecordResponse(
        long id, long workScheduleId, Type type, LocalDateTime startAt, LocalDateTime endAt) {
    public static RecordResponse from(WorkRecord r) {
        return new RecordResponse(
                r.getId(), r.getWorkSchedule().getId(), r.getType(), r.getStartAt(), r.getEndAt());
    }
}
