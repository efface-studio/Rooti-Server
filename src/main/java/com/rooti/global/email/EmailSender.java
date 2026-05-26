package com.rooti.global.email;

import java.util.List;

/**
 * Channel-neutral email port. Concrete implementations:
 *
 * <ul>
 *   <li>{@code ResendEmailSender} — HTTP API (production)
 *   <li>{@code LoggingEmailSender} — dry-run, no-op (local dev / tests)
 * </ul>
 */
public interface EmailSender {

    record Attachment(String filename, String contentType, byte[] content) {}

    /**
     * @return {@code true} if the message was accepted by the provider,
     *         {@code false} if the sender is in dry-run mode.
     */
    boolean send(String to, String subject, String html, List<Attachment> attachments);
}
