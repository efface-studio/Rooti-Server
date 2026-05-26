package com.rooti.domain.job.infrastructure;

import com.rooti.domain.job.domain.JobWorker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobWorkerRepository extends JpaRepository<JobWorker, Long> {

    @EntityGraph(
            attributePaths = {"companyWorker", "companyWorker.worker", "companyWorker.worker.user"})
    List<JobWorker> findAllByJobStandardIdAndUseFlagTrue(Long jobStandardId);

    Optional<JobWorker> findByCompanyWorkerIdAndJobStandardId(Long cwId, Long jsId);

    @Query(
            "select jw from JobWorker jw join fetch jw.companyWorker cw join fetch cw.worker w join fetch w.user "
                    + "where cw.id = :companyWorkerId and jw.useFlag = true")
    List<JobWorker> findActiveByCompanyWorker(Long companyWorkerId);
}
