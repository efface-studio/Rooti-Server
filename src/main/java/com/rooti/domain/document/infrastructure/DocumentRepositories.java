package com.rooti.domain.document.infrastructure;

import com.rooti.domain.document.domain.CaregiverDocument;
import com.rooti.domain.document.domain.CaregiverDocumentLog;
import com.rooti.domain.document.domain.CaregiverDocumentType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Convenience grouping so each domain doesn't need three nearly-empty repo files. */
public interface DocumentRepositories {

    interface DocumentRepository extends JpaRepository<CaregiverDocument, Long> {
        @EntityGraph(attributePaths = {"type", "relation", "relation.caregiver", "relation.worker"})
        List<CaregiverDocument> findAllByRelationId(Long relationId);
    }

    interface DocumentTypeRepository extends JpaRepository<CaregiverDocumentType, Long> {}

    interface DocumentLogRepository extends JpaRepository<CaregiverDocumentLog, Long> {
        List<CaregiverDocumentLog> findAllByDocumentIdOrderByActionAtDesc(Long documentId);
    }
}
