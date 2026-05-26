package com.rooti.domain.board.infrastructure;

import com.rooti.domain.board.domain.CaregiverBoard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CaregiverBoardRepository extends JpaRepository<CaregiverBoard, Long> {

    @EntityGraph(attributePaths = {"author"})
    @Query(
            "select b from CaregiverBoard b where b.published = true "
                    + "and (:keyword is null or lower(b.title) like lower(concat('%', :keyword, '%')))")
    Page<CaregiverBoard> searchPublished(String keyword, Pageable pageable);
}
