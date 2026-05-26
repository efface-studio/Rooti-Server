package com.rooti.global.email;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op email sender that logs the message instead of shipping it. Used in
 * local dev / tests where {@code rooti.resend.enabled=false}.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(ResendEmailSender.class)
public class LoggingEmailSender implements EmailSender {

    @Override
    public boolean send(String to, String subject, String html, List<Attachment> attachments) {
        int attachBytes = attachments == null ? 0 : attachments.stream().mapToInt(a -> a.content().length).sum();
        log.info(
                "[email:dry-run] to={} subject={} htmlLen={} attachCount={} attachBytes={}",
                to, subject, html == null ? 0 : html.length(),
                attachments == null ? 0 : attachments.size(), attachBytes);
        return false;
    }
}
