package com.smartdoc.gateway

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun smartDocOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("SmartDoc Gateway API")
                    .description("Authentication and public SmartDoc API gateway endpoints.")
                    .version("0.1.0")
            )
            .components(
                Components().addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    companion object {
        private const val BEARER_AUTH = "bearerAuth"
    }
}
