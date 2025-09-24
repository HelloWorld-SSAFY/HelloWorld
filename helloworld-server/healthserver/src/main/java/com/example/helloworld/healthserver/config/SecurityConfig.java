package com.example.helloworld.healthserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@Slf4j
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserInfoAuthenticationFilter userInfoAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 기본 설정: CSRF 비활성화, 세션 STATELESS 설정 등
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                // 인가(Authorization) 규칙 설정 (가장 구체적인 경로 -> 넓은 경로 순서)
                .authorizeHttpRequests(auth -> auth
                        // 1. Swagger, Actuator 경로는 인증 없이 모두 허용
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**"
                        ).permitAll()
                        // 2. /api/** 경로는 'INTERNAL_USER' 역할이 반드시 필요
                        .requestMatchers("/api/**").hasRole("INTERNAL_USER")
                        // 3. 그 외 명시되지 않은 모든 요청은 인증만 되면 허용
                        .anyRequest().authenticated()
                )

                // 우리가 만든 커스텀 필터를 Spring Security 필터 체인에 추가
                .addFilterBefore(userInfoAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}