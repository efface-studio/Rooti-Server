package com.rooti.domain.user.application;

import com.rooti.domain.user.domain.User;
import com.rooti.domain.user.infrastructure.UserRepository;
import com.rooti.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access pattern around {@link User}. Anything that needs to fetch a user but doesn't
 * own the lifecycle goes through here so that cross-cutting concerns (e.g. soft-delete filters,
 * caching) stay in one place.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public User getByUsername(String username) {
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(-1L));
    }
}
