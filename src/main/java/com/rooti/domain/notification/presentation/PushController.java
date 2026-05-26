package com.rooti.domain.notification.presentation;

import com.rooti.domain.notification.application.PushNotificationService;
import com.rooti.domain.notification.presentation.dto.PushRequest;
import com.rooti.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification")
@SecurityRequirement(name = "bearerAuth")
public class PushController {

    private final PushNotificationService pushService;

    @PostMapping("/push")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGER')")
    public ApiResponse<Void> send(@Valid @RequestBody PushRequest req) {
        pushService.sendToToken(req);
        return ApiResponse.ok();
    }
}
