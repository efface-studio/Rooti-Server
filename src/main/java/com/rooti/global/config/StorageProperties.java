package com.rooti.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage driver configuration.
 *
 * <p>The driver string ({@code local} | {@code s3}) selects which {@code StorageService}
 * implementation is wired in {@code com.rooti.domain.document.infrastructure}.
 *
 * @param driver one of {@code local}, {@code s3}
 * @param local local-disk driver settings (used when {@code driver == "local"})
 */
@ConfigurationProperties(prefix = "rooti.storage")
public record StorageProperties(String driver, Local local) {

    public record Local(String root, String publicUrl) {}
}
