package com.example.helloworld.healthserver.config;


import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServerFeignConfig {

    @Value("${ai.app-token}")
    private String appToken;

    @Bean
    public RequestInterceptor addAppTokenHeader() {
        return template -> {
            if (appToken != null && !appToken.isBlank()) {
                template.header("X-App-Token", appToken);
            }
        };
    }
}
