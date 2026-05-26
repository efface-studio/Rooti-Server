package com.rooti.global.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * Adds {@code created_by} / {@code updated_by} columns on top of {@link BaseTimeEntity}.
 *
 * <p>Use this for entities where an operator audit trail is important (companies, schedules,
 * documents). Lightweight value-objects can stick with {@link BaseTimeEntity}.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
