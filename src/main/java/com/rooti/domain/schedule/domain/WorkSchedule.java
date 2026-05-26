package com.rooti.domain.schedule.domain;

import com.rooti.domain.company.domain.CompanyCharger;
import com.rooti.domain.job.domain.JobStandard;
import com.rooti.domain.job.domain.JobWorker;
import com.rooti.global.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A planned shift for one worker doing one job. Records and process-records are children of this.
 *
 * <p>{@code endAt == null} means "open / still in progress" – a unique index supports cheap lookups
 * for that case (one open schedule per job-worker at any time).
 */
@Entity
@Table(name = "work_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_worker_id", nullable = false)
    private JobWorker jobWorker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_charger_id")
    private CompanyCharger companyCharger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_standard_id", nullable = false)
    private JobStandard jobStandard;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "work_doc_path", length = 500)
    private String workDocPath;

    @Column(name = "make_work_doc", nullable = false)
    private boolean makeWorkDoc;

    @Builder
    private WorkSchedule(
            JobWorker jobWorker,
            CompanyCharger companyCharger,
            JobStandard jobStandard,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String workDocPath,
            Boolean makeWorkDoc) {
        this.jobWorker = jobWorker;
        this.companyCharger = companyCharger;
        this.jobStandard = jobStandard;
        this.startAt = startAt;
        this.endAt = endAt;
        this.workDocPath = workDocPath;
        this.makeWorkDoc = makeWorkDoc != null && makeWorkDoc;
    }

    public void close(LocalDateTime at) {
        this.endAt = at;
    }

    public void attachWorkDoc(String path) {
        this.workDocPath = path;
        this.makeWorkDoc = true;
    }

    public boolean isOpen() {
        return endAt == null;
    }
}
