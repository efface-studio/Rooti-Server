package com.rooti.domain.company.infrastructure;

import com.rooti.domain.company.domain.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Query(
            "select c from Company c where c.useFlag = true "
                    + "and (:keyword is null or lower(c.name) like lower(concat('%', :keyword, '%'))) "
                    + "order by c.id desc")
    Page<Company> searchActive(String keyword, Pageable pageable);

    boolean existsByName(String name);
}
