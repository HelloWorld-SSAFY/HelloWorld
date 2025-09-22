package com.example.helloworld.healthserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public io.swagger.v3.oas.models.OpenAPI openAPI() {
        final String schemeName = "bearerAuth";

        return new io.swagger.v3.oas.models.OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("Health API")
                        .version("v1")
                        .description("health API"))
                // Gateway 아래로 붙는 베이스 경로 지정 (예: /user)
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/health")))
                // 보안 스키마 + 글로벌 적용
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes(
                        schemeName,
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .name(schemeName)
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(schemeName));
    }
}