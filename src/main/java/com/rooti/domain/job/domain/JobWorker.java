package com.rooti.domain.job.domain;

import com.rooti.domain.worker.domain.CompanyWorker;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Assignment of one {@link CompanyWorker} to one {@link JobStandard}. */
@Entity
@Table(
        name = "job_workers",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_job_worker",
                        columnNames = {"company_worker_id", "job_standard_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobWorker extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_worker_id", nullable = false)
    private CompanyWorker companyWorker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_standard_id", nullable = false)
    private JobStandard jobStandard;

    @Column(name = "use_flag", nullable = false)
    private boolean useFlag;

    @Convert(converter = JsonAttributeConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> context;

    public static JobWorker assign(CompanyWorker cw, JobStandard standard) {
        JobWorker jw = new JobWorker();
        jw.companyWorker = cw;
        jw.jobStandard = standard;
        jw.useFlag = true;
        return jw;
    }

    public void unassign() {
        this.useFlag = false;
    }

    public void reassign() {
        this.useFlag = true;
    }

    public void updateContext(Map<String, Object> ctx) {
        this.context = ctx;
    }
}
