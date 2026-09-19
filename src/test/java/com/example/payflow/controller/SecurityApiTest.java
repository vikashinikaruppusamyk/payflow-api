package com.example.payflow.controller;

import com.example.payflow.IntegrationTestSupport;
import com.example.payflow.entity.User;
import com.example.payflow.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of authentication (who you are) and authorization (what you may touch).
 */
class SecurityApiTest extends IntegrationTestSupport {
    private static final String TEST_SECRET = "test-only-signing-key-that-is-at-least-32-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void createUsers() {
        createUser("priya@okaxis", "1000.00");
        createUser("ravi@oksbi", "500.00");
    }

    private String login(String upiId, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upiId\": \"" + upiId + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    @Test
    void registerThenLoginThenUseTheToken() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Anu", "upiId": "anu@okhdfc", "initialBalance": 100, "password": "s3cret-pass"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"upiId": "ANU@okhdfc", "password": "s3cret-pass"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.upiId").value("anu@okhdfc"))
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(get("/users/me").header("Authorization", login("anu@okhdfc", "s3cret-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upiId").value("anu@okhdfc"));
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHash() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Anu", "upiId": "anu@okhdfc", "password": "s3cret-pass"}
                        """))
                .andExpect(status().isCreated());

        String stored = userRepository.findByUpiId("anu@okhdfc").getPasswordHash();
        assertThat(stored).startsWith("$2a$").doesNotContain("s3cret-pass");
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"upiId": "priya@okaxis", "password": "wrong-password"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid UPI ID or password"));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"upiId": "ghost@okaxis", "password": "whatever-123"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid UPI ID or password"));
    }

    @Test
    void accountWithoutPasswordCannotLogIn() throws Exception {
        // e.g. an account created before authentication was introduced
        userRepository.save(new User("Old", "old@okaxis", new BigDecimal("10.00"), null));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"upiId": "old@okaxis", "password": "anything-at-all"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(startsWith("Authentication required")));
        mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 10}
                        """))
                .andExpect(status().isUnauthorized());

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("1000.00");
    }

    @Test
    void tamperedExpiredAndForeignTokensAreRejected() throws Exception {
        String token = bearer("priya@okaxis");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mockMvc.perform(get("/users/me").header("Authorization", tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token is invalid"));

        User priya = userRepository.findByUpiId("priya@okaxis");
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expired = new JwtService(TEST_SECRET, Duration.ofHours(1), twoHoursAgo).generateToken(priya);
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token has expired; log in again"));

        String otherKey = new JwtService("a-completely-different-key-of-32-bytes-plus", Duration.ofHours(1),
                Clock.systemUTC()).generateToken(priya);
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + otherKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token is invalid"));
    }

    @Test
    void cannotSendMoneyFromSomeoneElsesAccount() throws Exception {
        mockMvc.perform(post("/transactions").header("Authorization", bearer("ravi@oksbi"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 500}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only send money from your own account"));

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("1000.00");
        assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("500.00");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void onlySenderReceiverAndAdminsCanSeeATransfer() throws Exception {
        createUser("anu@okhdfc", "0.00");
        createAdmin("admin@payflow");
        String body = mockMvc.perform(post("/transactions").header("Authorization", bearer("priya@okaxis"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 10}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.transactionId");

        for (String allowed : new String[]{"priya@okaxis", "ravi@oksbi", "admin@payflow"}) {
            mockMvc.perform(get("/transactions/{id}", id).header("Authorization", bearer(allowed)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/transactions/{id}/events", id).header("Authorization", bearer(allowed)))
                    .andExpect(status().isOk());
        }
        // Someone else gets 404, exactly as if the transfer did not exist
        mockMvc.perform(get("/transactions/{id}", id).header("Authorization", bearer("anu@okhdfc")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/transactions/{id}/events", id).header("Authorization", bearer("anu@okhdfc")))
                .andExpect(status().isNotFound());
    }

    @Test
    void eventLogExportIsAdminOnly() throws Exception {
        createAdmin("admin@payflow");

        mockMvc.perform(get("/events/export").header("Authorization", bearer("priya@okaxis")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(get("/events/export").header("Authorization", bearer("admin@payflow")))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpointsWorkWithoutAToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
