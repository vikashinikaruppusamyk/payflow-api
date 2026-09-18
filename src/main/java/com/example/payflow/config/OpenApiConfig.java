package com.example.payflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Interactive API docs at /swagger-ui.html, raw spec at /v3/api-docs
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI payFlowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PayFlow API")
                .version("1.0")
                .description("""
                        UPI-style wallet API: register users, transfer money and view statements.

                        Transfers are atomic, protected against concurrent updates with optimistic locking, \
                        and idempotent when an `Idempotency-Key` header is sent. Every transfer is recorded as \
                        PENDING and then SUCCESS or FAILED."""));
    }
}
