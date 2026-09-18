package com.example.payflow.controller;

import com.example.payflow.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyApiTest extends IntegrationTestSupport {

    private static final String TRANSFER = """
            {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 100, "note": "rent"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void createUsers() {
        createUser("priya@okaxis", "1000.00");
        createUser("ravi@oksbi", "500.00");
    }

    private ResultActions transfer(String key, String json) throws Exception {
        return mockMvc.perform(post("/transactions")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void retryWithSameKeyReturnsOriginalResultAndMovesMoneyOnce() throws Exception {
        String first = transfer("order-42", TRANSFER)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse().getContentAsString();
        Number transactionId = JsonPath.read(first, "$.transactionId");

        transfer("order-42", TRANSFER)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("900.00");
        assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("600.00");
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void differentKeysAreDifferentTransfers() throws Exception {
        transfer("order-1", TRANSFER).andExpect(status().isCreated());
        transfer("order-2", TRANSFER).andExpect(status().isCreated());

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("800.00");
    }

    @Test
    void reusingKeyForDifferentRequestIsRejected() throws Exception {
        transfer("order-42", TRANSFER).andExpect(status().isCreated());

        transfer("order-42", """
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 250, "note": "rent"}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("order-42")));

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("900.00");
    }

    @Test
    void keysAreScopedPerSender() throws Exception {
        transfer("same-key", TRANSFER).andExpect(status().isCreated());
        transfer("same-key", """
                {"senderUpiId": "ravi@oksbi", "receiverUpiId": "priya@okaxis", "amount": 100, "note": "rent"}
                """).andExpect(status().isCreated());

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void retryOfFailedTransferReturnsSameFailureWithoutRetryingIt() throws Exception {
        String tooMuch = """
                {"senderUpiId": "ravi@oksbi", "receiverUpiId": "priya@okaxis", "amount": 600}
                """;
        String first = transfer("order-7", tooMuch)
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        Number transactionId = JsonPath.read(first, "$.transactionId");

        // Even after Ravi receives enough money, the retry reports the original outcome
        transfer("top-up", TRANSFER).andExpect(status().isCreated());
        transfer("order-7", tooMuch)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failureReason").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.transactionId").value(transactionId));

        assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("600.00");
    }

    @Test
    void invalidKeyIsRejected() throws Exception {
        transfer("not a valid key!", TRANSFER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.idempotencyKey").exists());
    }

    @Test
    void concurrentRetriesWithSameKeyMoveMoneyExactlyOnce() throws Exception {
        int requests = 20;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return transfer("double-tap", TRANSFER).andReturn().getResponse();
                }));
            }
            startGate.countDown();

            int created = 0;
            Set<Object> transactionIds = new HashSet<>();
            for (Future<MockHttpServletResponse> future : futures) {
                MockHttpServletResponse response = future.get(60, TimeUnit.SECONDS);
                // 201 for the winner, 200 for replays, 409 while the winner is still in flight
                assertThat(response.getStatus()).isIn(201, 200, 409);
                if (response.getStatus() == 201) {
                    created++;
                }
                if (response.getStatus() != 409) {
                    transactionIds.add(JsonPath.read(response.getContentAsString(), "$.transactionId"));
                }
            }

            assertThat(created).isEqualTo(1);
            assertThat(transactionIds).hasSize(1);
            assertThat(transactionRepository.count()).isEqualTo(1);
            assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("900.00");
            assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("600.00");
        } finally {
            pool.shutdownNow();
        }
    }
}
