package com.rooti.domain.workrecord.infrastructure;

import com.rooti.domain.workrecord.domain.WorkRecord;
import com.rooti.domain.workrecord.domain.WorkRecord.Type;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {

    List<WorkRecord> findAllByWorkScheduleIdOrderByStartAtAsc(Long workScheduleId);

    Optional<WorkRecord> findFirstByWorkScheduleIdAndTypeAndEndAtIsNullOrderByStartAtDesc(
            Long workScheduleId, Type type);

    Optional<WorkRecord> findFirstByWorkScheduleIdAndEndAtIsNullOrderByStartAtDesc(
            Long workScheduleId);
}
