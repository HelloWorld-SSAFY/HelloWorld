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

    /** 요청/응답 전문을 모두 찍음 (헤더/바디 포함) */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public RequestInterceptor aiHeadersAndLoggingInterceptor(
            // ⚠ yml의 키와 반드시 일치시켜 주세요: ai.server.app-token
            @Value("${ai.app-token:}") String appToken,
            @Value("${ai.server.log-requests:true}") boolean enableLog
    ) {
        return template -> {
            // 1) 공통 헤더 주입
            if (appToken != null && !appToken.isBlank()) {
                template.header("X-App-Token", appToken);
            }

            // 2) 사람이 보기 쉬운 디버그 로그 (민감정보 마스킹)
            if (enableLog && log.isDebugEnabled()) {
                String url = (template.feignTarget() != null
                        ? template.feignTarget().url()
                        : "") + template.path();

                String maskedToken;
                if (appToken == null || appToken.isBlank()) {
                    maskedToken = "(none)";
                } else {
                    int keep = Math.min(6, appToken.length());
                    maskedToken = appToken.substring(0, keep) + "***";
                }

                String body;
                try {
                    body = (template.requestBody() != null) ? template.requestBody().asString() : "(no-body)";
                } catch (Exception e) {
                    body = "(binary/unavailable)";
                }

                log.debug(
                        "\n[AI-REQ] {} {}\nX-App-Token: {}\nHeaders: {}\nBody: {}\n",
                        template.method(), url, maskedToken, template.headers(), body
                );
            }
        };
    }
}