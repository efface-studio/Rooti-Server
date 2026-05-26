package com.rooti.domain.caregiver.infrastructure;

import com.rooti.domain.caregiver.domain.Caregiver;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaregiverRepository extends JpaRepository<Caregiver, Long> {
    Optional<Caregiver> findByUserId(Long userId);
}
