package com.rooti.domain.document.infrastructure;

import com.rooti.domain.document.domain.CaregiverDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보호자 서류의 종류(신분증/여권/위임장 등) 사전 — 시스템 운영 중에는 거의 읽기 전용입니다.
 */
public interface CaregiverDocumentTypeRepository extends JpaRepository<CaregiverDocumentType, Long> {}
