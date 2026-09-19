package com.example.payflow.controller;

import com.example.payflow.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiDocsTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesOpenApiSpecForAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("PayFlow API"))
                .andExpect(jsonPath("$.paths['/users']").exists())
                .andExpect(jsonPath("$.paths['/users/{upiId}/transactions']").exists())
                .andExpect(jsonPath("$.paths['/transactions']").exists())
                .andExpect(jsonPath("$.paths['/transactions/{transactionId}']").exists())
                .andExpect(jsonPath("$.paths['/transactions/{transactionId}/events']").exists())
                .andExpect(jsonPath("$.paths['/events/export']").exists());
    }

    @Test
    void documentsEveryStatusCodeTheTransferEndpointReturns() throws Exception {
        String post = "$.paths['/transactions'].post.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(post + "['201']").exists())
                .andExpect(jsonPath(post + "['200'].headers['Idempotent-Replayed']").exists())
                .andExpect(jsonPath(post + "['400']").exists())
                .andExpect(jsonPath(post + "['404']").exists())
                .andExpect(jsonPath(post + "['409']").exists())
                .andExpect(jsonPath(post + "['422']").exists())
                .andExpect(jsonPath(post + "['500']").exists())
                .andExpect(jsonPath(post + "['422'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/users'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/users'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/users'].post.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/events/export'].get.responses['200'].content['text/csv']").exists());
    }

    @Test
    void documentsBearerAuthenticationAndWhichEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/auth/login'].post").exists())
                // Registration and login are public: no token requirement and no 401
                .andExpect(jsonPath("$.paths['/users'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/users'].post.responses['401']").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty())
                // Protected endpoints document 401, and 403 where ownership or role rules apply
                .andExpect(jsonPath("$.paths['/transactions'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/transactions'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/users'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/events/export'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/users/me'].get.responses['401']").exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
