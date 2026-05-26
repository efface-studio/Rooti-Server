package com.rooti.global.audit;

import com.rooti.global.security.PrincipalDetails;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the current authenticated user-id for JPA auditing.
 *
 * <p>Anonymous calls (Flyway migrations, system jobs) intentionally return {@link Optional#empty()}
 * — the persistence layer treats null as "system" so we don't fabricate a fake user.
 */
public class SecurityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof PrincipalDetails details) {
            return Optional.of(details.userId());
        }
        return Optional.empty();
    }
}
