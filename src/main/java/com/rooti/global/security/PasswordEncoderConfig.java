package com.rooti.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더 단독 설정.
 *
 * <p>BCrypt 의 cost 는 10 — Rooti 의 평균 로그인 부하(초당 수십 건) 와 데스크탑급 인스턴스에서
 * 60~80ms 정도 걸리는 합리적인 값입니다. 비밀번호 정책이나 알고리즘이 바뀌면 이 파일 하나만
 * 손대면 충분합니다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
