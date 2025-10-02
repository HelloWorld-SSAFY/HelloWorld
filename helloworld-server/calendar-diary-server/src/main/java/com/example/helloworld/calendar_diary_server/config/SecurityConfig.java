package com.example.helloworld.calendar_diary_server.config;

import com.example.helloworld.calendar_diary_server.config.security.UserInfoAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
//마지막으로 위에서 만든 필터를 Spring Security의 필터 체인에 등록
public class SecurityConfig {

    private final UserInfoAuthenticationFilter userInfoAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // actuator, swagger 등 인증이 필요 없는 경로 설정
                        .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated() // 나머지 모든 요청은 인증 필요
                )
                //  직접 만든 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(userInfoAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}