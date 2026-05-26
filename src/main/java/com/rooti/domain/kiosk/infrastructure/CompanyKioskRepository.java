package com.rooti.domain.kiosk.infrastructure;

import com.rooti.domain.kiosk.domain.CompanyKiosk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyKioskRepository extends JpaRepository<CompanyKiosk, Long> {
    List<CompanyKiosk> findAllByCompanyId(Long companyId);

    boolean existsByCompanyIdAndKioskId(Long companyId, String kioskId);
}
