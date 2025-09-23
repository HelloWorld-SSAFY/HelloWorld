package com.example.helloworld.healthserver.config;


import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServerFeignConfig {

    @Bean
    public RequestInterceptor addAppTokenHeader(
            @Value("${ai.app-token}") String appToken
    ) {
        return template -> {
            template.header("X-App-Token", appToken);
        };
    }
}
