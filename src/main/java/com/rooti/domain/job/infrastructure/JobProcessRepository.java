package com.rooti.domain.job.infrastructure;

import com.rooti.domain.job.domain.JobProcess;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobProcessRepository extends JpaRepository<JobProcess, Long> {

    List<JobProcess> findAllByJobStandardIdOrderBySequenceAsc(Long jobStandardId);
}
