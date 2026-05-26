package com.rooti.domain.worker.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "WorkerCreateRequest")
public record CreateRequest(
        @NotBlank @Size(max = 150) String username,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 32) String phoneNumber) {}
