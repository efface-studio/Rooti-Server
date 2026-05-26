package com.rooti.domain.company.presentation;

import com.rooti.domain.company.application.CompanyService;
import com.rooti.domain.company.presentation.dto.CompanyDtos.CreateRequest;
import com.rooti.domain.company.presentation.dto.CompanyDtos.Response;
import com.rooti.domain.company.presentation.dto.CompanyDtos.UpdateRequest;
import com.rooti.global.response.ApiResponse;
import com.rooti.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Company")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @Operation(summary = "List active companies")
    public ApiResponse<PageResponse<Response>> list(
            @RequestParam(required = false) String keyword, @ParameterObject Pageable pageable) {
        return ApiResponse.ok(companyService.search(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<Response> get(@PathVariable long id) {
        return ApiResponse.ok(companyService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Response> create(@Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(companyService.create(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Response> update(
            @PathVariable long id, @Valid @RequestBody UpdateRequest req) {
        return ApiResponse.ok(companyService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivate(@PathVariable long id) {
        companyService.deactivate(id);
        return ApiResponse.ok();
    }
}
