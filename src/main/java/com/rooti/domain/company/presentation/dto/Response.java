package com.rooti.domain.company.presentation.dto;

import com.rooti.domain.company.domain.Company;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

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
