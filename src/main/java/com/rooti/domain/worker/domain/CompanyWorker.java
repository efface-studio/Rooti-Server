package com.rooti.domain.worker.domain;

import com.rooti.domain.company.domain.Company;
import com.rooti.global.audit.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The "company hires worker" relationship. Per-company, per-worker; unique pair enforced. */
@Entity
@Table(
        name = "company_workers",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_company_workers",
                        columnNames = {"company_id", "challenged_worker_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyWorker extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenged_worker_id", nullable = false)
    private ChallengedWorker worker;

    @Column(name = "is_hired", nullable = false)
    private boolean hired;

    public static CompanyWorker hire(Company company, ChallengedWorker worker) {
        CompanyWorker cw = new CompanyWorker();
        cw.company = company;
        cw.worker = worker;
        cw.hired = true;
        return cw;
    }

    public void fire() {
        this.hired = false;
    }

    public void rehire() {
        this.hired = true;
    }
}
