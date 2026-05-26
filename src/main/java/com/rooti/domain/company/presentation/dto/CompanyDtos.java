package com.rooti.domain.company.presentation.dto;

import com.rooti.domain.company.domain.Company;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public final class CompanyDtos {

    private CompanyDtos() {}

    @Schema(name = "CompanyResponse")
    public record Response(
            long id,
            String name,
            String location,
            boolean useFlag,
            String imagePath,
            String templateId,
            Map<String, Object> templateData) {

        public static Response from(Company c) {
            return new Response(
                    c.getId(),
                    c.getName(),
                    c.getLocation(),
                    c.isUseFlag(),
                    c.getImagePath(),
                    c.getTemplateId(),
                    c.getTemplateData());
        }
    }

    @Schema(name = "CompanyCreateRequest")
    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String location,
            @Size(max = 500) String imagePath,
            @Size(max = 100) String templateId,
            Map<String, Object> templateData) {}

    @Schema(name = "CompanyUpdateRequest")
    public record UpdateRequest(
            @Size(max = 200) String name,
            @Size(max = 500) String location,
            @Size(max = 500) String imagePath,
            @Size(max = 100) String templateId,
            Map<String, Object> templateData) {}
}
