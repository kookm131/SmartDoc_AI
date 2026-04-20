package com.smartdoc.analysis

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun smartDocOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("SmartDoc Analysis API")
                    .description("Document analysis job submission, lookup, and retry endpoints.")
                    .version("0.1.0")
            )
}
