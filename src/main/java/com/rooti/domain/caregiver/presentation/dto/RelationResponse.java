package com.rooti.domain.caregiver.presentation.dto;

import com.rooti.domain.caregiver.domain.CaregiverWorkerRelation;
import io.swagger.v3.oas.annotations.media.Schema;

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
