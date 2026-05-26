package com.rooti.domain.worker.infrastructure;

import com.rooti.domain.worker.domain.ChallengedWorker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChallengedWorkerRepository extends JpaRepository<ChallengedWorker, Long> {

    Optional<ChallengedWorker> findByUserId(Long userId);

    @Query(
            "select w from ChallengedWorker w join fetch w.user u where "
                    + "(:keyword is null or lower(u.name) like lower(concat('%', :keyword, '%')) "
                    + " or lower(u.phoneNumber) like lower(concat('%', :keyword, '%')))")
    Page<ChallengedWorker> search(String keyword, Pageable pageable);

    @Query(
            "select w from ChallengedWorker w join fetch w.user u where u.name = :name "
                    + "and u.phoneNumber = :phone")
    List<ChallengedWorker> findByNameAndPhone(String name, String phone);
}
