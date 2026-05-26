package com.rooti.domain.notification.application;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.rooti.domain.notification.presentation.dto.PushDtos.PushRequest;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper over Firebase Admin SDK.
 *
 * <p>The {@link FirebaseMessaging} bean is optional — when Firebase is disabled (e.g. local dev),
 * sending is a no-op and we log instead. That keeps tests fast and disconnected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final ObjectProvider<FirebaseMessaging> messagingProvider;

    @Async
    public void sendToToken(PushRequest req) {
        FirebaseMessaging messaging = messagingProvider.getIfAvailable();
        if (messaging == null) {
            log.info("[push:disabled] token={}, title={}", req.token(), req.title());
            return;
        }

        Map<String, String> data = new HashMap<>();
        if (req.deepLink() != null) data.put("deepLink", req.deepLink());
        if (req.extra() != null) req.extra().forEach((k, v) -> data.put(k, String.valueOf(v)));

        Message message =
                Message.builder()
                        .setToken(req.token())
                        .setNotification(
                                Notification.builder().setTitle(req.title()).setBody(req.body()).build())
                        .setAndroidConfig(
                                AndroidConfig.builder()
                                        .setPriority(AndroidConfig.Priority.HIGH)
                                        .setNotification(
                                                AndroidNotification.builder()
                                                        .setChannelId("rooti_default")
                                                        .build())
                                        .build())
                        .putAllData(data)
                        .build();
        try {
            String id = messaging.send(message);
            log.info("[push:sent] id={}, title={}", id, req.title());
        } catch (FirebaseMessagingException e) {
            log.warn("[push:failed] {}", e.getMessage());
            throw new BusinessException(ErrorCode.NOTIFICATION_SEND_FAILED, e.getMessage(), e);
        }
    }
}
