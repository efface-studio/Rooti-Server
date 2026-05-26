package com.rooti.global.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Wraps a {@link PrincipalDetails} together with the encoded password so that {@code
 * DaoAuthenticationProvider} can compare it. The encoded password is intentionally <strong>not
 * </strong> visible on the inner {@link PrincipalDetails} record because that record is the one
 * stashed into the {@code SecurityContext} after authentication.
 */
public final class PasswordCarryingPrincipal implements UserDetails {

    private final PrincipalDetails delegate;
    private final String password;

    public PasswordCarryingPrincipal(PrincipalDetails delegate, String password) {
        this.delegate = delegate;
        this.password = password;
    }

    public PrincipalDetails delegate() {
        return delegate;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return delegate.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return delegate.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return delegate.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return delegate.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }
}
