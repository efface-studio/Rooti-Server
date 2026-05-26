package com.rooti.domain.schedule.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "CloseScheduleRequest")
public record CloseRequest(LocalDateTime endAt) {}
