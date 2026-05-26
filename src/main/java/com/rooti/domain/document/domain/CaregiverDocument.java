package com.rooti.domain.document.domain;

import com.rooti.domain.caregiver.domain.CaregiverWorkerRelation;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "caregiver_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaregiverDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relation_id", nullable = false)
    private CaregiverWorkerRelation relation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private CaregiverDocumentType type;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Builder
    private CaregiverDocument(
            CaregiverWorkerRelation relation,
            CaregiverDocumentType type,
            String filename,
            Long fileSize,
            String contentType) {
        this.relation = relation;
        this.type = type;
        this.filename = filename;
        this.fileSize = fileSize;
        this.contentType = contentType;
    }
}
