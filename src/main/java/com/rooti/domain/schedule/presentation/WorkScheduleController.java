package com.rooti.domain.schedule.presentation;

import com.rooti.domain.schedule.application.WorkScheduleService;
import com.rooti.domain.schedule.presentation.dto.ScheduleDtos.BatchMakeRequest;
import com.rooti.domain.schedule.presentation.dto.ScheduleDtos.CloseRequest;
import com.rooti.domain.schedule.presentation.dto.ScheduleDtos.CreateRequest;
import com.rooti.domain.schedule.presentation.dto.ScheduleDtos.Response;
import com.rooti.global.response.ApiResponse;
import com.rooti.global.response.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "WorkSchedule")
@SecurityRequirement(name = "bearerAuth")
public class WorkScheduleController {

    private final WorkScheduleService scheduleService;

    @GetMapping("/{id}")
    public ApiResponse<Response> get(@PathVariable long id) {
        return ApiResponse.ok(scheduleService.get(id));
    }

    @GetMapping("/by-job-worker/{jobWorkerId}")
    public ApiResponse<List<Response>> byJobWorker(
            @PathVariable long jobWorkerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(scheduleService.listForJobWorker(jobWorkerId, from, to));
    }

    @GetMapping("/by-job-standard/{jobStandardId}")
    public ApiResponse<PageResponse<Response>> byJobStandard(
            @PathVariable long jobStandardId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject Pageable pageable) {
        return ApiResponse.ok(scheduleService.listForStandard(jobStandardId, from, to, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<Response> create(@Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(scheduleService.create(req));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<List<Response>> batch(@Valid @RequestBody BatchMakeRequest req) {
        return ApiResponse.ok(scheduleService.batchMake(req));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Response> close(
            @PathVariable long id, @RequestBody(required = false) CloseRequest req) {
        return ApiResponse.ok(scheduleService.close(id, req == null ? null : req.endAt()));
    }
}
