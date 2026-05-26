package com.rooti.domain.job.domain;

import com.rooti.domain.company.domain.Company;
import com.rooti.global.audit.BaseEntity;
import com.rooti.global.util.JsonAttributeConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * "Standard work" definition – the blueprint for what a worker should do, in what order, with what
 * timings. Owns its {@link JobProcess} steps.
 */
@Entity
@Table(name = "job_standards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobStandard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "use_flag", nullable = false)
    private boolean useFlag;

    @Column(name = "routine_start_time")
    private LocalTime routineStartTime;

    @Column(name = "standard_work_time", nullable = false)
    private int standardWorkTimeSeconds;

    @Column(name = "standard_rest_time", nullable = false)
    private int standardRestTimeSeconds;

    @Column(name = "start_message", columnDefinition = "TEXT")
    private String startMessage;

    @Column(name = "end_message", columnDefinition = "TEXT")
    private String endMessage;

    @Convert(converter = JsonAttributeConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> context;

    @Column(name = "for_journal", nullable = false)
    private boolean forJournal;

    @OneToMany(
            mappedBy = "jobStandard",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sequence asc")
    private List<JobProcess> processes = new ArrayList<>();

    @Builder
    private JobStandard(
            Company company,
            String name,
            Boolean useFlag,
            LocalTime routineStartTime,
            Integer standardWorkTimeSeconds,
            Integer standardRestTimeSeconds,
            String startMessage,
            String endMessage,
            Map<String, Object> context,
            Boolean forJournal) {
        this.company = company;
        this.name = name;
        this.useFlag = useFlag == null || useFlag;
        this.routineStartTime = routineStartTime;
        this.standardWorkTimeSeconds = standardWorkTimeSeconds == null ? 0 : standardWorkTimeSeconds;
        this.standardRestTimeSeconds = standardRestTimeSeconds == null ? 0 : standardRestTimeSeconds;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
        this.context = context;
        this.forJournal = forJournal != null && forJournal;
    }

    public JobProcess addProcess(
            String name,
            int sequence,
            String videoPath,
            int processTime,
            String startMessage,
            String endMessage,
            Map<String, Object> context) {
        JobProcess p =
                JobProcess.builder()
                        .jobStandard(this)
                        .name(name)
                        .sequence(sequence)
                        .videoPath(videoPath)
                        .processTimeSeconds(processTime)
                        .startMessage(startMessage)
                        .endMessage(endMessage)
                        .context(context)
                        .build();
        processes.add(p);
        return p;
    }

    public void updateBasic(
            String name,
            LocalTime routineStart,
            Integer workSec,
            Integer restSec,
            String startMsg,
            String endMsg,
            Boolean forJournal,
            Map<String, Object> context) {
        if (name != null) this.name = name;
        this.routineStartTime = routineStart;
        if (workSec != null) this.standardWorkTimeSeconds = workSec;
        if (restSec != null) this.standardRestTimeSeconds = restSec;
        this.startMessage = startMsg;
        this.endMessage = endMsg;
        if (forJournal != null) this.forJournal = forJournal;
        if (context != null) this.context = context;
    }

    public void deactivate() {
        this.useFlag = false;
    }
}
