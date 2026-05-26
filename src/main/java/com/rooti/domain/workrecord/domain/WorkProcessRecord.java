package com.rooti.domain.workrecord.domain;

import com.rooti.domain.job.domain.JobProcess;
import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.global.audit.BaseTimeEntity;
import com.rooti.global.util.JsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fine-grain process-level record. Captures per-step start/end conditions, answers, and voice
 * recording paths. The {@code Condition} flag mirrors the legacy {@code 1=OK, 0=NO, -1=OTHER}
 * convention exactly so historical reports keep rendering.
 */
@Entity
@Table(name = "work_process_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkProcessRecord extends BaseTimeEntity {

    public static final short CONDITION_OK = 1;
    public static final short CONDITION_NO = 0;
    public static final short CONDITION_OTHER = -1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_schedule_id", nullable = false)
    private WorkSchedule workSchedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_process_id", nullable = false)
    private JobProcess jobProcess;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "start_condition")
    private Short startCondition;

    @Column(name = "end_condition")
    private Short endCondition;

    @Column(name = "start_answer", columnDefinition = "TEXT")
    private String startAnswer;

    @Column(name = "end_answer", columnDefinition = "TEXT")
    private String endAnswer;

    @Column(name = "start_voice_path", length = 500)
    private String startVoicePath;

    @Column(name = "end_voice_path", length = 500)
    private String endVoicePath;

    @Convert(converter = JsonAttributeConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> process;

    @Builder
    private WorkProcessRecord(
            WorkSchedule workSchedule,
            JobProcess jobProcess,
            String type,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Short startCondition,
            Short endCondition,
            String startAnswer,
            String endAnswer,
            String startVoicePath,
            String endVoicePath,
            Map<String, Object> process) {
        this.workSchedule = workSchedule;
        this.jobProcess = jobProcess;
        this.type = type;
        this.startAt = startAt;
        this.endAt = endAt;
        this.startCondition = startCondition;
        this.endCondition = endCondition;
        this.startAnswer = startAnswer;
        this.endAnswer = endAnswer;
        this.startVoicePath = startVoicePath;
        this.endVoicePath = endVoicePath;
        this.process = process;
    }

    public void recordEnd(
            LocalDateTime at,
            Short condition,
            String answer,
            String voicePath,
            Map<String, Object> processSnapshot) {
        this.endAt = at;
        this.endCondition = condition;
        this.endAnswer = answer;
        this.endVoicePath = voicePath;
        if (processSnapshot != null) this.process = processSnapshot;
    }
}
