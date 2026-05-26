package com.rooti.domain.company.infrastructure;

import com.rooti.domain.company.domain.CompanyCharger;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyChargerRepository extends JpaRepository<CompanyCharger, Long> {

    Optional<CompanyCharger> findByUserId(Long userId);

    List<CompanyCharger> findByCompanyIdAndHiredTrue(Long companyId);
}
