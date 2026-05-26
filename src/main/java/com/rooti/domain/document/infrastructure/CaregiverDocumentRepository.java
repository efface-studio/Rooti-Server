package com.rooti.domain.document.infrastructure;

import com.rooti.domain.document.domain.CaregiverDocument;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보호자가 업로드한 서류(여권/신분증 등)의 영속화 저장소.
 *
 * <p>{@link #findAllByRelationId} 는 relation → caregiver / worker 까지 한 번에 잡아 N+1 을
 * 막습니다. 다른 조회 경로가 늘어나면 그 메서드에도 동일한 entity graph 힌트를 붙여 둡니다.
 */
public interface CaregiverDocumentRepository extends JpaRepository<CaregiverDocument, Long> {

    @EntityGraph(attributePaths = {"type", "relation", "relation.caregiver", "relation.worker"})
    List<CaregiverDocument> findAllByRelationId(Long relationId);
}
