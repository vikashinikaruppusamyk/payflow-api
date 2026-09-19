package com.example.payflow.controller;

import com.example.payflow.IntegrationTestSupport;
import com.example.payflow.entity.TransactionStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void createUsers() {
        createUser("priya@okaxis", "1000.00");
        createUser("ravi@oksbi", "500.00");
    }

    private ResultActions transfer(String json) throws Exception {
        return mockMvc.perform(post("/transactions")
                .header("Authorization", bearerForSender(json, "priya@okaxis"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void successfulTransferUpdatesBothBalances() throws Exception {
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 250.75, "note": "rent"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").isNumber())
                .andExpect(jsonPath("$.amount").value(250.75))
                .andExpect(jsonPath("$.note").value("rent"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.completedAt").exists());

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("749.25");
        assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("750.75");
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void insufficientBalanceReturns422AndChangesNothing() throws Exception {
        transfer("""
                {"senderUpiId": "ravi@oksbi", "receiverUpiId": "priya@okaxis", "amount": 500.01}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Insufficient balance"))
                .andExpect(jsonPath("$.failureReason").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.transactionId").isNumber());

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("1000.00");
        assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("500.00");
        // The failed attempt is kept for auditing instead of vanishing
        assertThat(transactionRepository.countByStatus(TransactionStatus.FAILED)).isEqualTo(1);
        assertThat(transactionRepository.countByStatus(TransactionStatus.SUCCESS)).isZero();
    }

    @Test
    void failedTransferCanBeLookedUpById() throws Exception {
        String body = transfer("""
                {"senderUpiId": "ravi@oksbi", "receiverUpiId": "priya@okaxis", "amount": 9999}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        long transactionId = ((Number) JsonPath.read(body, "$.transactionId")).longValue();

        mockMvc.perform(get("/transactions/{id}", transactionId).header("Authorization", bearer("priya@okaxis")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void unknownTransactionIdReturns404() throws Exception {
        mockMvc.perform(get("/transactions/{id}", 987654).header("Authorization", bearer("priya@okaxis")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownReceiverReturns404() throws Exception {
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ghost@oksbi", "amount": 10}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.failureReason").value("RECEIVER_NOT_FOUND"));

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("1000.00");
    }

    @Test
    void transferToSelfIsRejected() throws Exception {
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "Priya@OkAxis", "amount": 10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sender and receiver cannot be the same account"));

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void invalidAmountsAreRejectedBeforeReachingTheService() throws Exception {
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 10.001}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 100000.01}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
        transfer("""
                {"senderUpiId": "priya", "receiverUpiId": "ravi@oksbi"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.senderUpiId").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        transfer("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }
}
