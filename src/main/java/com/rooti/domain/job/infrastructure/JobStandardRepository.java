package com.rooti.domain.job.infrastructure;

import com.rooti.domain.job.domain.JobStandard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobStandardRepository extends JpaRepository<JobStandard, Long> {

    @EntityGraph(attributePaths = {"company"})
    @Query(
            "select j from JobStandard j where j.useFlag = true "
                    + "and (:companyId is null or j.company.id = :companyId) "
                    + "and (:keyword is null or lower(j.name) like lower(concat('%', :keyword, '%')))")
    Page<JobStandard> search(Long companyId, String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"company", "processes"})
    @Query("select j from JobStandard j where j.id = :id")
    java.util.Optional<JobStandard> findWithProcesses(Long id);
}
