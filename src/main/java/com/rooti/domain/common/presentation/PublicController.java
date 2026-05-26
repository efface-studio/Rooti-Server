package com.rooti.domain.common.presentation;

import com.rooti.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiny set of unauthenticated endpoints the mobile/web client polls at boot:
 *
 * <ul>
 *   <li>{@code /api/v1/public/version} — version gate (force-upgrade)
 *   <li>{@code /api/v1/public/ping} — used for connectivity checks
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public")
public class PublicController {

    @Value("${rooti.version.latest}")
    private String latest;

    @Value("${rooti.version.min-supported}")
    private String minSupported;

    public record VersionInfo(String latest, String minSupported) {}

    @GetMapping("/version")
    @Operation(summary = "Latest / minimum-supported app versions")
    public ApiResponse<VersionInfo> version() {
        return ApiResponse.ok(new VersionInfo(latest, minSupported));
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
