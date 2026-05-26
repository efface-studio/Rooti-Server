package com.rooti.domain.workrecord.presentation.dto;

import com.rooti.domain.workrecord.domain.WorkRecord.Type;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(name = "RecordStartRequest")
public record StartRequest(@NotNull Long workScheduleId, @NotNull Type type, LocalDateTime at) {}
