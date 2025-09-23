package com.example.helloworld.healthserver.config;

import lombok.RequiredArgsConstructor;
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
                        // WebSecurityCustomizer에서 처리하는 경로는 여기서 제거
                        // actuator/health 엔드포인트만 인증 없이 접근 허용
                        .requestMatchers("/actuator/health"
//                                "/api/wearable/daily-buckets"
                        ).permitAll()
                        // 그 외의 모든 요청은 반드시 인증을 거쳐야 함
                        .anyRequest().authenticated()
                )
                .addFilterBefore(userInfoAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}