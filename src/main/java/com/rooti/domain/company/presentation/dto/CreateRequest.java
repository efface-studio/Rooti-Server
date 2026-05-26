package com.rooti.domain.company.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(name = "CompanyCreateRequest")
public record CreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String location,
        @Size(max = 500) String imagePath,
        @Size(max = 100) String templateId,
        Map<String, Object> templateData) {}
