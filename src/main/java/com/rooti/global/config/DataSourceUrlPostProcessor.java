package com.rooti.global.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Bumps {@code sslmode=require} onto the JDBC URL when {@code DB_SSL_REQUIRED=true}.
 *
 * <p>AWS RDS encourages TLS for all client connections, and this is the lowest-friction
 * way to enforce it without making every developer learn JDBC URL syntax. Local dev
 * (where you typically run an unprotected docker postgres) leaves the flag unset, so
 * the URL is not modified.
 *
 * <p>Wired via {@code META-INF/spring.factories} so it runs before any datasource bean
 * is materialised.
 */
public class DataSourceUrlPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (!Boolean.parseBoolean(env.getProperty("DB_SSL_REQUIRED", "false"))) return;
        String current = env.getProperty("spring.datasource.url");
        if (current == null || current.contains("sslmode=")) return;

        String separator = current.contains("?") ? "&" : "?";
        String rewritten = current + separator + "sslmode=require";

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("spring.datasource.url", rewritten);
        env.getPropertySources().addFirst(new MapPropertySource("rooti-ssl-mode", overrides));
    }
}
