package com.rooti.domain.caregiver.domain;

import com.rooti.domain.worker.domain.ChallengedWorker;
import com.rooti.global.audit.BaseTimeEntity;
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

/**
 * Caregiver ↔ ChallengedWorker many-to-many link table.
 *
 * <p>Modeled as an explicit entity (not a hidden join table) because documents and access logs
 * reference it directly — they belong to a {@code (caregiver, worker)} pair, not just one side.
 */
@Entity
@Table(
        name = "caregiver_worker_relations",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_caregiver_worker",
                        columnNames = {"caregiver_id", "challenged_worker_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaregiverWorkerRelation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false)
    private Caregiver caregiver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenged_worker_id", nullable = false)
    private ChallengedWorker worker;

    public static CaregiverWorkerRelation of(Caregiver c, ChallengedWorker w) {
        CaregiverWorkerRelation r = new CaregiverWorkerRelation();
        r.caregiver = c;
        r.worker = w;
        return r;
    }
}
