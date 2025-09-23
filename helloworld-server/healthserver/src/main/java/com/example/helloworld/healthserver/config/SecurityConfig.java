package com.example.helloworld.healthserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
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
    public WebSecurityCustomizer webSecurityCustomizer() {
        // 이 설정은 Spring Security 필터 체인을 완전히 우회시킵니다.
        // Swagger UI, API 문서 같은 정적 리소스에 적용하기 가장 좋은 방법입니다.
        return (web) -> web.ignoring().requestMatchers(
                "/swagger-ui/**",
                "/v3/api-docs/**" // Swagger API 문서 경로도 함께 무시합니다.
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        //  ▼▼▼ [수정] actuator 하위 모든 경로를 인증 없이 허용합니다. (헬스 체크용) ▼▼▼
                                     .requestMatchers("/actuator/**").permitAll()
                        //                        // /api/** 경로는 INTERNAL_USER 역할이 필요합니다.
                                           .requestMatchers("/api/**").hasRole("INTERNAL_USER")
                .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.error("Access denied: {}", accessDeniedException.getMessage());
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Forbidden\"}");
                        })
                )
                .addFilterBefore(userInfoAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}