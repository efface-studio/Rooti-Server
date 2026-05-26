package com.rooti.domain.caregiver.presentation;

import com.rooti.domain.caregiver.application.CaregiverService;
import com.rooti.domain.caregiver.presentation.dto.RegisterRequest;
import com.rooti.domain.caregiver.presentation.dto.RelationResponse;
import com.rooti.domain.caregiver.presentation.dto.Response;
import com.rooti.global.response.ApiResponse;
import com.rooti.global.security.CurrentUser;
import com.rooti.global.security.PrincipalDetails;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/caregivers")
@RequiredArgsConstructor
@Tag(name = "Caregiver")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverController {

    private final CaregiverService caregiverService;

    @GetMapping("/me")
    public ApiResponse<Response> me(@CurrentUser PrincipalDetails me) {
        return ApiResponse.ok(caregiverService.me(me.userId()));
    }

    @GetMapping("/me/relations")
    public ApiResponse<List<RelationResponse>> listMyRelations(
            @CurrentUser PrincipalDetails me) {
        return ApiResponse.ok(caregiverService.listRelations(me.userId()));
    }

    @PostMapping("/me/relations")
    public ApiResponse<RelationResponse> registerWorker(
            @CurrentUser PrincipalDetails me, @Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(caregiverService.registerWorker(me.userId(), req.workerId()));
    }

    @DeleteMapping("/me/relations/{relationId}")
    public ApiResponse<Void> remove(
            @CurrentUser PrincipalDetails me, @PathVariable long relationId) {
        caregiverService.removeRelation(me.userId(), relationId);
        return ApiResponse.ok();
    }
}
