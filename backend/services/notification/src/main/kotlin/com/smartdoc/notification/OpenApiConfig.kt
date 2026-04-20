package com.smartdoc.notification

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
                    .title("SmartDoc Notification API")
                    .description("Notification dispatch, event history, and keyword rule endpoints.")
                    .version("0.1.0")
            )
}
