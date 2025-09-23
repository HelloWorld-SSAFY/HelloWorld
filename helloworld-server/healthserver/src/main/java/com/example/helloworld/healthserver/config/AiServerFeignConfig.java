package com.example.helloworld.healthserver.config;


import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServerFeignConfig {

    @Bean
    public RequestInterceptor addAppTokenHeader() {
        // 이 인터셉터는 요청을 보내기 직전에 헤더를 추가하는 역할을 합니다.
        return template -> {
            template.header("X-App-Token", "e3d10cf9-bfad-43a7-9817-6b0b5dc2730c");
        };
    }
}
