package com.rooti.global.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 spec.
 *
 * <p>Adds a global {@code bearerAuth} requirement so that endpoints annotated with the standard
 * {@code @SecurityRequirement} are shown as locked in Swagger UI.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the access token issued from POST /api/v1/auth/login")
public class OpenApiConfig {

    @Bean
    public OpenAPI rootiOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Rooti Server API")
                                .description(
                                        "REST API for the Rooti workforce-management platform "
                                                + "(challenged workers, caregivers, company chargers).")
                                .version("v1")
                                .contact(
                                        new Contact()
                                                .name("Rooti Engineering")
                                                .email("dev@rooti.io"))
                                .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("Current")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components());
    }
}
