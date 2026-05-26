package com.rooti;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Rooti Server entry point.
 *
 * <p>Bootstrap order matters here:
 *
 * <ol>
 *   <li>{@code @ConfigurationPropertiesScan} — auto-bind {@code rooti.*} prefixed properties
 *   <li>{@code @EnableJpaAuditing} — populate {@code @CreatedDate} / {@code @LastModifiedDate}
 *   <li>{@code @EnableAsync} / {@code @EnableScheduling} — push notifications, schedule jobs
 * </ol>
 *
 * <p>JVM-wide timezone is pinned to {@code Asia/Seoul} so that {@code LocalDateTime} based domain
 * logic stays consistent regardless of the deploy host.
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.rooti")
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableAsync
@EnableScheduling
public class RootiApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(RootiApplication.class, args);
    }
}
