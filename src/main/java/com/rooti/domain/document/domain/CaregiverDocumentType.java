package com.rooti.domain.document.domain;

import com.rooti.global.audit.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "caregiver_document_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaregiverDocumentType extends BaseTimeEntity {

    public enum RequestOn {
        NOTHING,
        REGISTER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_on", nullable = false, length = 20)
    private RequestOn requestOn;
}
