package com.rooti.domain.job.domain;

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
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A single step inside a {@link JobStandard}. Ordered by {@link #sequence}. */
@Entity
@Table(
        name = "job_processes",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_job_process_seq",
                        columnNames = {"job_standard_id", "sequence"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobProcess extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_standard_id", nullable = false)
    private JobStandard jobStandard;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "video_path", length = 500)
    private String videoPath;

    @Column(name = "start_message", columnDefinition = "TEXT")
    private String startMessage;

    @Column(name = "end_message", columnDefinition = "TEXT")
    private String endMessage;

    @Convert(converter = JsonAttributeConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> context;

    @Column(name = "process_time", nullable = false)
    private int processTimeSeconds;

    @Builder
    private JobProcess(
            JobStandard jobStandard,
            String name,
            int sequence,
            String videoPath,
            String startMessage,
            String endMessage,
            Map<String, Object> context,
            int processTimeSeconds) {
        this.jobStandard = jobStandard;
        this.name = name;
        this.sequence = sequence;
        this.videoPath = videoPath;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
        this.context = context;
        this.processTimeSeconds = processTimeSeconds;
    }

    public void update(
            String name,
            Integer sequence,
            String videoPath,
            String startMessage,
            String endMessage,
            Map<String, Object> context,
            Integer processTimeSeconds) {
        if (name != null) this.name = name;
        if (sequence != null) this.sequence = sequence;
        this.videoPath = videoPath;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
        if (context != null) this.context = context;
        if (processTimeSeconds != null) this.processTimeSeconds = processTimeSeconds;
    }
}
