package com.foodie.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI foodieOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Foodie API")
            .version("0.1.0")
            .description("API da plataforma Foodie. Autenticação por cookie de sessão (web) ou "
                + "Authorization: Bearer (aplicativos móveis). O token é devolvido no login em X-Foodie-Token."));
    }
}
