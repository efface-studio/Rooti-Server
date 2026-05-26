package com.rooti.domain.workrecord.infrastructure;

import com.rooti.domain.workrecord.domain.WorkProcessRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkProcessRecordRepository extends JpaRepository<WorkProcessRecord, Long> {

    @EntityGraph(attributePaths = {"jobProcess"})
    @Query(
            "select r from WorkProcessRecord r where r.workSchedule.id = :scheduleId "
                    + "order by r.jobProcess.sequence asc, r.startAt asc")
    List<WorkProcessRecord> findAllByScheduleOrdered(Long scheduleId);

    Optional<WorkProcessRecord> findFirstByWorkScheduleIdAndJobProcessIdAndEndAtIsNull(
            Long scheduleId, Long processId);
}
