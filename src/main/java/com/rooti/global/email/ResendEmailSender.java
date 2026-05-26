package com.rooti.global.email;

import com.rooti.global.config.ResendProperties;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends mail via the Resend HTTP API (https://resend.com/docs/api-reference/emails/send-email).
 *
 * <p>Activated only when {@code rooti.resend.enabled=true} so local dev / tests
 * never hit the real provider. The fallback in that case is
 * {@link LoggingEmailSender}.
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(prefix = "rooti.resend", name = "enabled", havingValue = "true")
public class ResendEmailSender implements EmailSender {

    private final RestClient client;
    private final String from;

    public ResendEmailSender(ResendProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "rooti.resend.enabled=true but RESEND_API_KEY is empty");
        }
        this.from = props.from();
        this.client =
                RestClient.builder()
                        .baseUrl("https://api.resend.com")
                        .defaultHeader("Authorization", "Bearer " + props.apiKey())
                        .defaultHeader("Content-Type", "application/json")
                        .requestFactory(
                                new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                                    {
                                        setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
                                        setReadTimeout((int) Duration.ofSeconds(15).toMillis());
                                    }
                                })
                        .build();
    }

    @Override
    public boolean send(String to, String subject, String html, List<Attachment> attachments) {
        Map<String, Object> body = new HashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("html", html);

        if (attachments != null && !attachments.isEmpty()) {
            List<Map<String, String>> arr = new ArrayList<>();
            for (Attachment a : attachments) {
                arr.add(
                        Map.of(
                                "filename", a.filename(),
                                // Resend accepts either a URL or base64 content.
                                "content", Base64.getEncoder().encodeToString(a.content())));
            }
            body.put("attachments", arr);
        }

        try {
            client.post()
                    .uri("/emails")
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("[resend] HTTP {} body={}", res.getStatusCode(), new String(res.getBody().readAllBytes()));
                        throw new BusinessException(ErrorCode.NOTIFICATION_SEND_FAILED, "Resend rejected the message");
                    })
                    .toBodilessEntity();
            log.info("[resend] sent to={} subject={}", to, subject);
            return true;
        } catch (RestClientResponseException e) {
            log.error("[resend] {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.NOTIFICATION_SEND_FAILED, "Resend API error", e);
        }
    }
}
