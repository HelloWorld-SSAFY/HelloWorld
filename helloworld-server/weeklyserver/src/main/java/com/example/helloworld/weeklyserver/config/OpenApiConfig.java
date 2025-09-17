package com.example.helloworld.weeklyserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public io.swagger.v3.oas.models.OpenAPI openAPI() {
        return new io.swagger.v3.oas.models.OpenAPI()
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/weekly")));
    }
}
