package com.rooti.domain.worker.infrastructure;

import com.rooti.domain.worker.domain.CompanyWorker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyWorkerRepository extends JpaRepository<CompanyWorker, Long> {

    @EntityGraph(attributePaths = {"company", "worker", "worker.user"})
    @Query("select cw from CompanyWorker cw where cw.company.id = :companyId")
    Page<CompanyWorker> findAllByCompany(Long companyId, Pageable pageable);

    Optional<CompanyWorker> findByCompanyIdAndWorkerId(Long companyId, Long workerId);

    @EntityGraph(attributePaths = {"worker", "worker.user"})
    List<CompanyWorker> findAllByCompanyIdAndHiredTrue(Long companyId);

    boolean existsByCompanyIdAndWorkerId(Long companyId, Long workerId);
}
