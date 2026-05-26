package com.rooti.global.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring Security 의 {@link AuthenticationManager} 빈.
 *
 * <p>현재 username/password 로그인 흐름에서는 직접 호출하지 않지만, {@code @PreAuthorize} 와
 * 일부 외부 의존성이 컨테이너에서 {@code AuthenticationManager} 를 기대하므로 빈을 노출해 둡니다.
 * 새 인증 방식(예: SSO) 추가 시 이 파일에서 provider 목록만 늘리면 됩니다.
 */
@Configuration
public class AuthenticationManagerConfig {

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(List.of(provider));
    }
}
