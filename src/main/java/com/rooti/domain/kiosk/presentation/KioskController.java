package com.rooti.domain.kiosk.presentation;

import com.rooti.domain.company.application.CompanyService;
import com.rooti.domain.kiosk.domain.CompanyKiosk;
import com.rooti.domain.kiosk.infrastructure.CompanyKioskRepository;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kiosks")
@RequiredArgsConstructor
@Tag(name = "Kiosk")
@SecurityRequirement(name = "bearerAuth")
public class KioskController {

    private final CompanyKioskRepository repository;
    private final CompanyService companyService;

    public record BindRequest(Long companyId, @NotBlank String kioskId) {}

    public record KioskResponse(long id, long companyId, String kioskId) {}

    @GetMapping("/by-company/{companyId}")
    public ApiResponse<List<KioskResponse>> list(@PathVariable long companyId) {
        return ApiResponse.ok(
                repository.findAllByCompanyId(companyId).stream()
                        .map(k -> new KioskResponse(k.getId(), k.getCompany().getId(), k.getKioskId()))
                        .toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<KioskResponse> bind(@Valid @RequestBody BindRequest req) {
        if (repository.existsByCompanyIdAndKioskId(req.companyId(), req.kioskId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 키오스크입니다.");
        }
        CompanyKiosk k =
                repository.save(CompanyKiosk.bind(companyService.getOrThrow(req.companyId()), req.kioskId()));
        return ApiResponse.ok(new KioskResponse(k.getId(), k.getCompany().getId(), k.getKioskId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<Void> unbind(@PathVariable long id) {
        repository.deleteById(id);
        return ApiResponse.ok();
    }
}
