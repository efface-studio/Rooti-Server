package com.rooti.domain.caregiver.infrastructure;

import com.rooti.domain.caregiver.domain.CaregiverWorkerRelation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaregiverWorkerRelationRepository
        extends JpaRepository<CaregiverWorkerRelation, Long> {

    @EntityGraph(attributePaths = {"caregiver", "caregiver.user", "worker", "worker.user"})
    List<CaregiverWorkerRelation> findAllByCaregiverId(Long caregiverId);

    Optional<CaregiverWorkerRelation> findByCaregiverIdAndWorkerId(Long caregiverId, Long workerId);

    boolean existsByCaregiverIdAndWorkerId(Long caregiverId, Long workerId);

    List<CaregiverWorkerRelation> findAllByWorkerId(Long workerId);
}
