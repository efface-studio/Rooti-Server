package com.rooti.domain.caregiver.presentation.dto;

import com.rooti.domain.caregiver.domain.Caregiver;
import com.rooti.domain.caregiver.domain.CaregiverWorkerRelation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public final class CaregiverDtos {

    private CaregiverDtos() {}

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

    @Schema(name = "CaregiverWorkerRelationResponse")
    public record RelationResponse(
            long id, long caregiverId, long workerId, String workerName, String workerPhone) {
        public static RelationResponse from(CaregiverWorkerRelation r) {
            return new RelationResponse(
                    r.getId(),
                    r.getCaregiver().getId(),
                    r.getWorker().getId(),
                    r.getWorker().getUser().getName(),
                    r.getWorker().getUser().getPhoneNumber());
        }
    }

    @Schema(name = "RegisterWorkerRequest")
    public record RegisterRequest(@NotNull Long workerId) {}
}
