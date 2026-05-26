package com.rooti.domain.caregiver.presentation.dto;

import com.rooti.domain.caregiver.domain.Caregiver;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CaregiverResponse")
public record Response(
        long id, long userId, String username, String name, String phoneNumber, String email) {
    public static Response from(Caregiver c) {
        return new Response(
                c.getId(),
                c.getUser().getId(),
                c.getUser().getUsername(),
                c.getUser().getName(),
                c.getUser().getPhoneNumber(),
                c.getUser().getEmail());
    }
}
