package com.rooti.domain.workrecord.application;

import com.rooti.domain.job.domain.JobProcess;
import com.rooti.domain.job.infrastructure.JobProcessRepository;
import com.rooti.domain.schedule.application.WorkScheduleService;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.domain.workrecord.domain.WorkProcessRecord;
import com.rooti.domain.workrecord.domain.WorkRecord;
import com.rooti.domain.workrecord.domain.WorkRecord.Type;
import com.rooti.domain.workrecord.infrastructure.WorkProcessRecordRepository;
import com.rooti.domain.workrecord.infrastructure.WorkRecordRepository;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.EndRequest;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.ProcessEndRequest;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.ProcessResponse;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.ProcessStartRequest;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.RecordResponse;
import com.rooti.domain.workrecord.presentation.dto.WorkRecordDtos.StartRequest;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * State-machine for the two record streams.
 *
 * <p>Concurrency note: an open {@code WorkRecord} (end == null) of a given type acts as a soft
 * lock per schedule + type. We refuse to {@code begin} a second one of the same type without
 * closing the previous, matching the original Django invariant.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkRecordService {

    private final WorkRecordRepository recordRepository;
    private final WorkProcessRecordRepository processRecordRepository;
    private final WorkScheduleService scheduleService;
    private final JobProcessRepository jobProcessRepository;

    // ----------------------------------------------------------------- WorkRecord
    public RecordResponse begin(StartRequest req) {
        WorkSchedule s = scheduleService.getOrThrow(req.workScheduleId());
        recordRepository
                .findFirstByWorkScheduleIdAndTypeAndEndAtIsNullOrderByStartAtDesc(s.getId(), req.type())
                .ifPresent(
                        existing -> {
                            throw new BusinessException(
                                    ErrorCode.WORK_RECORD_OUT_OF_RANGE,
                                    "이미 동일 타입의 진행중인 기록이 있습니다 (id=" + existing.getId() + ")");
                        });
        WorkRecord r = WorkRecord.begin(s, req.type(), req.at() == null ? LocalDateTime.now() : req.at());
        return RecordResponse.from(recordRepository.save(r));
    }

    public RecordResponse end(EndRequest req) {
        WorkRecord r =
                recordRepository
                        .findFirstByWorkScheduleIdAndTypeAndEndAtIsNullOrderByStartAtDesc(
                                req.workScheduleId(), req.type())
                        .orElseThrow(() -> new BusinessException(ErrorCode.WORK_RECORD_NOT_FOUND));
        r.end(req.at() == null ? LocalDateTime.now() : req.at());
        // OFF closes the schedule itself
        if (req.type() == Type.OFF) {
            r.getWorkSchedule().close(r.getEndAt());
        }
        return RecordResponse.from(r);
    }

    @Transactional(readOnly = true)
    public List<RecordResponse> list(long scheduleId) {
        return recordRepository.findAllByWorkScheduleIdOrderByStartAtAsc(scheduleId).stream()
                .map(RecordResponse::from)
                .toList();
    }

    // ----------------------------------------------------------- WorkProcessRecord
    public ProcessResponse beginProcess(ProcessStartRequest req) {
        WorkSchedule s = scheduleService.getOrThrow(req.workScheduleId());
        JobProcess process =
                jobProcessRepository
                        .findById(req.jobProcessId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_PROCESS_NOT_FOUND));
        WorkProcessRecord r =
                WorkProcessRecord.builder()
                        .workSchedule(s)
                        .jobProcess(process)
                        .type(req.type() == null ? "PROCESS" : req.type())
                        .startAt(req.at() == null ? LocalDateTime.now() : req.at())
                        .startCondition(req.condition())
                        .startAnswer(req.answer())
                        .startVoicePath(req.voicePath())
                        .process(req.process())
                        .build();
        return ProcessResponse.from(processRecordRepository.save(r));
    }

    public ProcessResponse endProcess(ProcessEndRequest req) {
        WorkProcessRecord r =
                processRecordRepository
                        .findFirstByWorkScheduleIdAndJobProcessIdAndEndAtIsNull(
                                req.workScheduleId(), req.jobProcessId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.WORK_RECORD_NOT_FOUND));
        r.recordEnd(
                req.at() == null ? LocalDateTime.now() : req.at(),
                req.condition(),
                req.answer(),
                req.voicePath(),
                req.process());
        return ProcessResponse.from(r);
    }

    @Transactional(readOnly = true)
    public List<ProcessResponse> listProcess(long scheduleId) {
        return processRecordRepository.findAllByScheduleOrdered(scheduleId).stream()
                .map(ProcessResponse::from)
                .toList();
    }
}
