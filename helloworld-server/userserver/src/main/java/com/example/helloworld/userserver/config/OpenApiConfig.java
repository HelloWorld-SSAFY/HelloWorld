package com.example.helloworld.userserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class OpenApiConfig {
    @Bean
    public io.swagger.v3.oas.models.OpenAPI openAPI() {
        return new io.swagger.v3.oas.models.OpenAPI()
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/user")));
    }
}
