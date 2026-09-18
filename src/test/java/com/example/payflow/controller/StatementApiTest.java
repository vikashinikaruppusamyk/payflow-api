package com.example.payflow.controller;

import com.example.payflow.IntegrationTestSupport;
import com.example.payflow.dto.TransferRequest;
import com.example.payflow.exception.TransferFailedException;
import com.example.payflow.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatementApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionService transactionService;

    @BeforeEach
    void createHistory() {
        createUser("priya@okaxis", "1000.00");
        createUser("ravi@oksbi", "500.00");
        createUser("anu@okhdfc", "0.00");

        send("priya@okaxis", "ravi@oksbi", "100.00");   // priya DEBIT
        send("ravi@oksbi", "priya@okaxis", "40.00");    // priya CREDIT
        send("priya@okaxis", "anu@okhdfc", "10.00");    // priya DEBIT
        send("ravi@oksbi", "anu@okhdfc", "5.00");       // not priya's
        assertThatThrownBy(() -> send("priya@okaxis", "ravi@oksbi", "5000.00")) // priya FAILED
                .isInstanceOf(TransferFailedException.class);
    }

    private void send(String from, String to, String amount) {
        transactionService.sendMoney(new TransferRequest(from, to, new BigDecimal(amount), null));
    }

    @Test
    void returnsBothDirectionsNewestFirst() throws Exception {
        mockMvc.perform(get("/users/{upiId}/transactions", "Priya@OkAxis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].failureReason").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.content[1].direction").value("DEBIT"))
                .andExpect(jsonPath("$.content[1].counterpartyUpiId").value("anu@okhdfc"))
                .andExpect(jsonPath("$.content[2].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[2].counterpartyUpiId").value("ravi@oksbi"))
                .andExpect(jsonPath("$.content[2].amount").value(40.00))
                .andExpect(jsonPath("$.content[3].direction").value("DEBIT"));
    }

    @Test
    void filtersByStatus() throws Exception {
        mockMvc.perform(get("/users/{upiId}/transactions", "priya@okaxis").param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void paginates() throws Exception {
        mockMvc.perform(get("/users/{upiId}/transactions", "priya@okaxis").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].amount").value(100.00));
    }

    @Test
    void rejectsInvalidPagingAndUnknownStatus() throws Exception {
        mockMvc.perform(get("/users/{upiId}/transactions", "priya@okaxis").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").value("size cannot exceed 100"));
        mockMvc.perform(get("/users/{upiId}/transactions", "priya@okaxis").param("status", "DONE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserReturns404() throws Exception {
        mockMvc.perform(get("/users/{upiId}/transactions", "ghost@okaxis"))
                .andExpect(status().isNotFound());
    }
}
