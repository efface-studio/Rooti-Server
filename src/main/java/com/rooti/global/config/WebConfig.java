package com.rooti.global.config;

import com.rooti.global.security.SecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC level configuration (NOT security CORS — that one lives in SecurityConfig).
 *
 * <p>Responsibilities kept narrow on purpose:
 *
 * <ul>
 *   <li>Serve user-uploaded media files when running with the {@code local} storage driver.
 *   <li>Expose CORS settings for non-secured endpoints (Swagger, actuator).
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SecurityProperties securityProperties;
    private final StorageProperties storageProperties;

    public WebConfig(SecurityProperties securityProperties, StorageProperties storageProperties) {
        this.securityProperties = securityProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(securityProperties.cors().allowedOrigins().toArray(new String[0]))
                .allowedMethods(securityProperties.cors().allowedMethods().toArray(new String[0]))
                .allowedHeaders(securityProperties.cors().allowedHeaders().toArray(new String[0]))
                .exposedHeaders(securityProperties.cors().exposedHeaders().toArray(new String[0]))
                .allowCredentials(securityProperties.cors().allowCredentials())
                .maxAge(securityProperties.cors().maxAge());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Local-disk storage driver serves /media/** directly. In prod use S3 + CloudFront.
        if ("local".equalsIgnoreCase(storageProperties.driver())) {
            String location = "file:" + storageProperties.local().root() + "/";
            registry.addResourceHandler("/media/**")
                    .addResourceLocations(location)
                    .setCachePeriod(3600);
        }
        // Swagger UI static resources
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
    }
}
