package com.rooti.domain.job.presentation;

import com.rooti.domain.job.application.JobStandardService;
import com.rooti.domain.job.presentation.dto.ProcessUpsertRequest;
import com.rooti.domain.job.presentation.dto.StandardCreateRequest;
import com.rooti.domain.job.presentation.dto.StandardResponse;
import com.rooti.domain.job.presentation.dto.StandardUpdateRequest;
import com.rooti.global.response.ApiResponse;
import com.rooti.global.response.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-standards")
@RequiredArgsConstructor
@Tag(name = "JobStandard")
@SecurityRequirement(name = "bearerAuth")
public class JobStandardController {

    private final JobStandardService jobStandardService;

    @GetMapping
    public ApiResponse<PageResponse<StandardResponse>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @ParameterObject Pageable pageable) {
        return ApiResponse.ok(jobStandardService.search(companyId, keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<StandardResponse> get(@PathVariable long id) {
        return ApiResponse.ok(jobStandardService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<StandardResponse> create(@Valid @RequestBody StandardCreateRequest req) {
        return ApiResponse.ok(jobStandardService.create(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<StandardResponse> update(
            @PathVariable long id, @Valid @RequestBody StandardUpdateRequest req) {
        return ApiResponse.ok(jobStandardService.update(id, req));
    }

    @PutMapping("/{id}/processes")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<StandardResponse> syncProcesses(
            @PathVariable long id, @Valid @RequestBody List<ProcessUpsertRequest> processes) {
        return ApiResponse.ok(jobStandardService.syncProcesses(id, processes));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<Void> deactivate(@PathVariable long id) {
        jobStandardService.deactivate(id);
        return ApiResponse.ok();
    }
}
