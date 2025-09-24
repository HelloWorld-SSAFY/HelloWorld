package com.example.helloworld.healthserver.config;


import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import feign.Logger;

@Slf4j
@Configuration
public class AiServerFeignConfig {

    // Feign 자체 로그를 상세(FULL)로
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Value("${ai.app-token}")
    private String appToken;

    @Bean
    public RequestInterceptor aiHeadersAndLoggingInterceptor(
            @Value("${ai.server.app-token:}") String appToken,
            @Value("${ai.server.log-requests:true}") boolean enableLog
    ) {
        return template -> {
            // 공통 헤더(앱 토큰) 주입
            if (appToken != null && !appToken.isBlank()) {
                template.header("X-App-Token", appToken);
            }

            // 사람이 읽기 쉽게, 우리도 별도 로그 남기기 (토큰은 마스킹)
            if (enableLog && log.isDebugEnabled()) {
                String url = template.feignTarget() != null
                        ? template.feignTarget().url() + template.path()
                        : template.path();

                String method = template.method();
                String maskedToken = (appToken == null || appToken.isBlank())
                        ? "(none)"
                        : appToken.substring(0, Math.min(6, appToken.length())) + "***";

                String body = null;
                if (template.requestBody() != null) {
                    try {
                        body = template.requestBody().asString(); // UTF-8 기본
                    } catch (Exception ignore) {
                        body = "(binary or unavailable)";
                    }
                }

                log.debug("\n[AI-REQ] {} {}\nHeaders: {}\nX-App-Token: {}\nBody: {}\n",
                        method, url, template.headers(), maskedToken, body);
            }
        };
    }
}
