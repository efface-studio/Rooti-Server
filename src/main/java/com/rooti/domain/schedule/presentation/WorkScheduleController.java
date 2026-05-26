package com.rooti.domain.schedule.presentation;

import com.rooti.domain.schedule.application.WorkScheduleReader;
import com.rooti.domain.schedule.application.WorkScheduleWriter;
import com.rooti.domain.schedule.presentation.dto.BatchMakeRequest;
import com.rooti.domain.schedule.presentation.dto.CloseRequest;
import com.rooti.domain.schedule.presentation.dto.CreateRequest;
import com.rooti.domain.schedule.presentation.dto.Response;
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

/**
 * 근무 스케줄 HTTP 진입점.
 *
 * <p>조회 / 변경 use case 가 각각 다른 컴포넌트({@link WorkScheduleReader} / {@link
 * WorkScheduleWriter}) 로 분리되어 있어, 컨트롤러에서도 의도가 자연스럽게 드러납니다.
 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "WorkSchedule")
@SecurityRequirement(name = "bearerAuth")
public class WorkScheduleController {

    private final WorkScheduleReader reader;
    private final WorkScheduleWriter writer;

    @GetMapping("/{id}")
    public ApiResponse<Response> get(@PathVariable long id) {
        return ApiResponse.ok(reader.get(id));
    }

    @GetMapping("/by-job-worker/{jobWorkerId}")
    public ApiResponse<List<Response>> byJobWorker(
            @PathVariable long jobWorkerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reader.listForJobWorker(jobWorkerId, from, to));
    }

    @GetMapping("/by-job-standard/{jobStandardId}")
    public ApiResponse<PageResponse<Response>> byJobStandard(
            @PathVariable long jobStandardId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject Pageable pageable) {
        return ApiResponse.ok(reader.listForStandard(jobStandardId, from, to, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<Response> create(@Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(writer.create(req));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<List<Response>> batch(@Valid @RequestBody BatchMakeRequest req) {
        return ApiResponse.ok(writer.batchMake(req));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Response> close(
            @PathVariable long id, @RequestBody(required = false) CloseRequest req) {
        return ApiResponse.ok(writer.close(id, req == null ? null : req.endAt()));
    }
}
