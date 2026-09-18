package com.example.payflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
                        PENDING and then SUCCESS or FAILED, and each step is written to an event log that can be \
                        exported as CSV for process mining.

                        All errors share one JSON shape (ErrorResponse): timestamp, status, error, message, path, \
                        plus fieldErrors for validation failures and failureReason/transactionId for failed transfers."""));
    }

    // Every endpoint can fail unexpectedly; document that once instead of on each controller method
    @Bean
    public OpenApiCustomizer internalServerErrorResponse() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses().putIfAbsent("500", new ApiResponse()
                        .description("Unexpected server error; details are logged, never returned to the client")
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))))));
    }
}
