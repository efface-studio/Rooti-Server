package com.rooti.domain.job.presentation;

import com.rooti.domain.job.application.JobWorkerService;
import com.rooti.domain.job.presentation.dto.JobDtos.AssignRequest;
import com.rooti.domain.job.presentation.dto.JobDtos.JobWorkerResponse;
import com.rooti.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-workers")
@RequiredArgsConstructor
@Tag(name = "JobWorker")
@SecurityRequirement(name = "bearerAuth")
public class JobWorkerController {

    private final JobWorkerService jobWorkerService;

    @GetMapping
    public ApiResponse<List<JobWorkerResponse>> list(
            @RequestParam(required = false) Long jobStandardId,
            @RequestParam(required = false) Long companyWorkerId) {
        if (jobStandardId != null) {
            return ApiResponse.ok(jobWorkerService.listByJobStandard(jobStandardId));
        }
        if (companyWorkerId != null) {
            return ApiResponse.ok(jobWorkerService.listActiveByCompanyWorker(companyWorkerId));
        }
        return ApiResponse.ok(List.of());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<JobWorkerResponse> assign(@Valid @RequestBody AssignRequest req) {
        return ApiResponse.ok(
                jobWorkerService.assign(req.companyWorkerId(), req.jobStandardId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<Void> unassign(@PathVariable long id) {
        jobWorkerService.unassign(id);
        return ApiResponse.ok();
    }
}
