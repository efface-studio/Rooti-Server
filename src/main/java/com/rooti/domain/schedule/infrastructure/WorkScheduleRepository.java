package com.rooti.domain.schedule.infrastructure;

import com.rooti.domain.schedule.domain.WorkSchedule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    @EntityGraph(attributePaths = {"jobWorker", "jobStandard", "companyCharger"})
    @Query(
            "select s from WorkSchedule s where s.jobWorker.id = :jobWorkerId "
                    + "and s.startAt < :to and (s.endAt is null or s.endAt > :from) order by s.startAt")
    List<WorkSchedule> findInRange(Long jobWorkerId, LocalDateTime from, LocalDateTime to);

    @Query(
            "select s from WorkSchedule s where s.jobWorker.id = :jobWorkerId "
                    + "and s.endAt is null order by s.startAt desc")
    List<WorkSchedule> findOpen(Long jobWorkerId);

    @EntityGraph(
            attributePaths = {
                "jobWorker",
                "jobWorker.companyWorker",
                "jobWorker.companyWorker.worker",
                "jobWorker.companyWorker.worker.user",
                "jobStandard"
            })
    @Query(
            "select s from WorkSchedule s where s.jobStandard.id = :jobStandardId "
                    + "and s.startAt >= :from and s.startAt < :to order by s.startAt desc")
    Page<WorkSchedule> findByStandardInRange(
            Long jobStandardId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Optional<WorkSchedule> findFirstByJobWorkerIdAndEndAtIsNullOrderByStartAtDesc(Long jobWorkerId);
}
