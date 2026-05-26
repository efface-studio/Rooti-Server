package com.rooti.global.security;

import com.rooti.domain.user.domain.User;
import com.rooti.domain.user.infrastructure.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Wires Spring Security's authentication step into our {@code users} table.
 *
 * <p>Used by {@code DaoAuthenticationProvider} during the password-based login flow. JWT-based
 * requests bypass this entirely — the {@link com.rooti.global.jwt.JwtAuthenticationFilter} builds
 * the principal directly from the validated token.
 */
@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
        return new PasswordCarryingPrincipal(
                new PrincipalDetails(
                        user.getId(),
                        user.getUsername(),
                        List.of(user.getRole().name()),
                        user.isEnabled(),
                        true),
                user.getPasswordHash());
    }
}
