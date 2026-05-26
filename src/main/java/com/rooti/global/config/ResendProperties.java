package com.rooti.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resend HTTP API config.
 *
 * <p>Enabled when {@code rooti.resend.enabled=true} and an API key is present.
 * The key must never appear in version control — keep it in Secrets Manager and
 * inject through the {@code RESEND_API_KEY} env var.
 *
 * @param enabled feature flag; defaults to {@code false} so local dev / tests
 *     don't accidentally hit the real provider
 * @param apiKey  Resend API key (read from {@code rooti.resend.api-key} →
 *     {@code RESEND_API_KEY})
 * @param from    {@code "Display Name <noreply@your-domain>"} sender header.
 *     Domain must be verified on Resend.
 */
@ConfigurationProperties(prefix = "rooti.resend")
public record ResendProperties(boolean enabled, String apiKey, String from) {}
