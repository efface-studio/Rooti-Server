package com.rooti.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rooti.global.audit.SecurityAuditorAware;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

/**
 * Bootstrap JPA-adjacent infrastructure: a single shared {@link JPAQueryFactory} for QueryDSL
 * repositories, plus the {@link AuditorAware} bean that powers {@code @CreatedBy} /
 * {@code @LastModifiedBy} columns.
 */
@Configuration
public class JpaConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }

    @Bean
    public AuditorAware<Long> auditorAware() {
        return new SecurityAuditorAware();
    }
}
