package com.example.payflow.controller;

import com.example.payflow.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
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
        return mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void successfulTransferUpdatesBothBalances() throws Exception {
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 250.75, "note": "rent"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").isNumber())
                .andExpect(jsonPath("$.amount").value(250.75))
                .andExpect(jsonPath("$.note").value("rent"));

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
                .andExpect(jsonPath("$.message").value("Insufficient balance"));

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("1000.00");
        assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("500.00");
    }

    @Test
    void unknownReceiverReturns404() throws Exception {
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ghost@oksbi", "amount": 10}
                """)
                .andExpect(status().isNotFound());

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("1000.00");
    }

    @Test
    void transferToSelfIsRejected() throws Exception {
        transfer("""
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "Priya@OkAxis", "amount": 10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sender and receiver cannot be the same account"));
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
