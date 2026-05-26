package com.rooti.domain.document.infrastructure;

import com.rooti.domain.document.domain.CaregiverDocumentLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보호자 서류의 상태 변경 이력 — 가장 최근 액션부터 내림차순으로 조회합니다.
 */
public interface CaregiverDocumentLogRepository extends JpaRepository<CaregiverDocumentLog, Long> {

    List<CaregiverDocumentLog> findAllByDocumentIdOrderByActionAtDesc(Long documentId);
}
