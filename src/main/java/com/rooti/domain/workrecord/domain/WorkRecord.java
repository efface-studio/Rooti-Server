package com.rooti.domain.workrecord.domain;

import com.rooti.domain.schedule.domain.WorkSchedule;
import com.rooti.global.audit.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Coarse-grain "what state am I in" record. One row per state transition on a schedule. */
@Entity
@Table(name = "work_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkRecord extends BaseTimeEntity {

    public enum Type {
        ON,
        WORK,
        REST,
        OFF
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_schedule_id", nullable = false)
    private WorkSchedule workSchedule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    public static WorkRecord begin(WorkSchedule schedule, Type type, LocalDateTime at) {
        WorkRecord r = new WorkRecord();
        r.workSchedule = schedule;
        r.type = type;
        r.startAt = at;
        return r;
    }

    public void end(LocalDateTime at) {
        this.endAt = at;
    }
}
