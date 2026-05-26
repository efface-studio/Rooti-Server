package com.rooti.domain.worker.presentation;

import com.rooti.domain.worker.application.WorkerService;
import com.rooti.domain.worker.presentation.dto.WorkerDtos.CompanyWorkerResponse;
import com.rooti.domain.worker.presentation.dto.WorkerDtos.CreateRequest;
import com.rooti.domain.worker.presentation.dto.WorkerDtos.HireRequest;
import com.rooti.domain.worker.presentation.dto.WorkerDtos.Response;
import com.rooti.global.response.ApiResponse;
import com.rooti.global.response.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/v1/workers")
@RequiredArgsConstructor
@Tag(name = "Worker")
@SecurityRequirement(name = "bearerAuth")
public class WorkerController {

    private final WorkerService workerService;

    @GetMapping
    public ApiResponse<PageResponse<Response>> list(
            @RequestParam(required = false) String keyword, @ParameterObject Pageable pageable) {
        return ApiResponse.ok(workerService.search(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<Response> get(@PathVariable long id) {
        return ApiResponse.ok(workerService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<Response> create(@Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(workerService.create(req));
    }

    @PostMapping("/hire")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<CompanyWorkerResponse> hire(@Valid @RequestBody HireRequest req) {
        return ApiResponse.ok(workerService.hire(req.companyId(), req.workerId()));
    }

    @DeleteMapping("/company-workers/{companyWorkerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<Void> fire(@PathVariable long companyWorkerId) {
        workerService.fire(companyWorkerId);
        return ApiResponse.ok();
    }

    @GetMapping("/by-company/{companyId}")
    public ApiResponse<PageResponse<CompanyWorkerResponse>> byCompany(
            @PathVariable long companyId, @ParameterObject Pageable pageable) {
        return ApiResponse.ok(workerService.listByCompany(companyId, pageable));
    }
}
