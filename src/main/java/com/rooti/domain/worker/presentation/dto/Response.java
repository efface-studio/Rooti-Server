package com.rooti.domain.worker.presentation.dto;

import com.rooti.domain.worker.domain.ChallengedWorker;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WorkerResponse")
public record Response(
        long id, long userId, String username, String name, String phoneNumber, String fcmToken) {
    public static Response from(ChallengedWorker w) {
        return new Response(
                w.getId(),
                w.getUser().getId(),
                w.getUser().getUsername(),
                w.getUser().getName(),
                w.getUser().getPhoneNumber(),
                w.getFcmToken());
    }
}
