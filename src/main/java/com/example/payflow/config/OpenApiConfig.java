package com.example.payflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Interactive API docs at /swagger-ui.html, raw spec at /v3/api-docs
@Configuration
public class OpenApiConfig {
    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI payFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayFlow API")
                        .version("1.0")
                        .description("""
                                UPI-style wallet API: register users, log in, transfer money and view statements.

                                **Authentication:** register with POST /users, log in with POST /auth/login, then click \
                                **Authorize** and paste the accessToken. Users can act only on their own account; \
                                admins can view every account and export the event log.

                                Transfers are atomic, protected against concurrent updates with optimistic locking, \
                                and idempotent when an `Idempotency-Key` header is sent. Every transfer is recorded as \
                                PENDING and then SUCCESS or FAILED, and each step is written to an event log that can be \
                                exported as CSV for process mining.

                                All errors share one JSON shape (ErrorResponse): timestamp, status, error, message, path, \
                                plus fieldErrors for validation failures and failureReason/transactionId for failed transfers."""))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /auth/login")))
                // Every operation needs the token unless it opts out with @SecurityRequirements()
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    // Responses every endpoint shares, documented once instead of on each controller method
    @Bean
    public OpenApiCustomizer commonErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            boolean isPublic = operation.getSecurity() != null && operation.getSecurity().isEmpty();
            if (!isPublic) {
                operation.getResponses().putIfAbsent("401",
                        errorResponse("Missing, invalid or expired access token"));
            }
            operation.getResponses().putIfAbsent("500",
                    errorResponse("Unexpected server error; details are logged, never returned to the client"));
        }));
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
    }
}
