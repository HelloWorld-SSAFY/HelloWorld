package com.example.helloworld.userserver.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filter(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**").permitAll() // 로그인/리프레시 허용
                        .anyRequest().permitAll()                   // (임시) 나중에 보호 구간만 authenticated()로
                )
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())                  // 폼 로그인 끔 → HTML 리다이렉트 방지
                .build();
    }
}

