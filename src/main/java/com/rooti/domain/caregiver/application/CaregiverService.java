package com.rooti.domain.caregiver.application;

import com.rooti.domain.caregiver.domain.Caregiver;
import com.rooti.domain.caregiver.domain.CaregiverWorkerRelation;
import com.rooti.domain.caregiver.infrastructure.CaregiverRepository;
import com.rooti.domain.caregiver.infrastructure.CaregiverWorkerRelationRepository;
import com.rooti.domain.caregiver.presentation.dto.RelationResponse;
import com.rooti.domain.caregiver.presentation.dto.Response;
import com.rooti.domain.worker.application.WorkerService;
import com.rooti.domain.worker.domain.ChallengedWorker;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CaregiverService {

    private final CaregiverRepository caregiverRepository;
    private final CaregiverWorkerRelationRepository relationRepository;
    private final WorkerService workerService;

    public Caregiver getByUserId(long userId) {
        return caregiverRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Response me(long userId) {
        return Response.from(getByUserId(userId));
    }

    public RelationResponse registerWorker(long caregiverUserId, long workerId) {
        Caregiver caregiver = getByUserId(caregiverUserId);
        if (relationRepository.existsByCaregiverIdAndWorkerId(caregiver.getId(), workerId)) {
            throw new BusinessException(ErrorCode.CAREGIVER_RELATION_DUPLICATED);
        }
        ChallengedWorker worker = workerService.getOrThrow(workerId);
        CaregiverWorkerRelation r =
                relationRepository.save(CaregiverWorkerRelation.of(caregiver, worker));
        return RelationResponse.from(r);
    }

    @Transactional(readOnly = true)
    public List<RelationResponse> listRelations(long caregiverUserId) {
        Caregiver caregiver = getByUserId(caregiverUserId);
        return relationRepository.findAllByCaregiverId(caregiver.getId()).stream()
                .map(RelationResponse::from)
                .toList();
    }

    public void removeRelation(long caregiverUserId, long relationId) {
        Caregiver caregiver = getByUserId(caregiverUserId);
        CaregiverWorkerRelation r =
                relationRepository
                        .findById(relationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CAREGIVER_NOT_FOUND));
        if (!r.getCaregiver().getId().equals(caregiver.getId())) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        relationRepository.delete(r);
    }
}
