package com.example.payflow.controller;

import com.example.payflow.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventLogApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void createUsers() {
        createUser("priya@okaxis", "1000.00");
        createUser("ravi@oksbi", "50.00");
    }

    private ResultActions transfer(String key, String json) throws Exception {
        var request = post("/transactions").contentType(MediaType.APPLICATION_JSON).content(json);
        return mockMvc.perform(key == null ? request : request.header("Idempotency-Key", key));
    }

    private long transactionIdOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.transactionId")).longValue();
    }

    @Test
    void successfulTransferRecordsEveryStepInOrder() throws Exception {
        long id = transactionIdOf(transfer(null, """
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 100}
                """).andExpect(status().isCreated()));

        mockMvc.perform(get("/transactions/{id}/events", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].activity", contains("INITIATED", "VALIDATED", "DEBITED", "CREDITED", "COMPLETED")));
    }

    @Test
    void failedTransferRecordsOnlyWhatReallyHappened() throws Exception {
        long id = transactionIdOf(transfer(null, """
                {"senderUpiId": "ravi@oksbi", "receiverUpiId": "priya@okaxis", "amount": 500}
                """).andExpect(status().isUnprocessableEntity()));

        // No VALIDATED/DEBITED: those steps were rolled back with the failed attempt
        mockMvc.perform(get("/transactions/{id}/events", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].activity", contains("INITIATED", "FAILED")))
                .andExpect(jsonPath("$[1].details").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void idempotentRetryIsRecordedAsReplay() throws Exception {
        String body = """
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 10}
                """;
        long id = transactionIdOf(transfer("k-1", body).andExpect(status().isCreated()));
        transfer("k-1", body).andExpect(status().isOk());

        mockMvc.perform(get("/transactions/{id}/events", id))
                .andExpect(jsonPath("$[*].activity",
                        contains("INITIATED", "VALIDATED", "DEBITED", "CREDITED", "COMPLETED", "REPLAYED")));
    }

    @Test
    void eventsOfUnknownTransactionReturn404() throws Exception {
        mockMvc.perform(get("/transactions/{id}/events", 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportsEventLogAsCsv() throws Exception {
        long ok = transactionIdOf(transfer(null, """
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 100}
                """));
        long failed = transactionIdOf(transfer(null, """
                {"senderUpiId": "ravi@oksbi", "receiverUpiId": "priya@okaxis", "amount": 5000}
                """));

        String csv = mockMvc.perform(get("/events/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("payflow-event-log.csv")))
                .andExpect(content().string(startsWith("case_id,activity,timestamp,details\n")))
                .andReturn().getResponse().getContentAsString();

        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(1 + 5 + 2);
        assertThat(lines[1]).startsWith(ok + ",INITIATED,");
        assertThat(lines[5]).startsWith(ok + ",COMPLETED,");
        assertThat(lines[7]).startsWith(failed + ",FAILED,").endsWith(",INSUFFICIENT_BALANCE");
    }

    @Test
    void exportCanBeLimitedToATimeWindow() throws Exception {
        transfer(null, """
                {"senderUpiId": "priya@okaxis", "receiverUpiId": "ravi@oksbi", "amount": 100}
                """);

        mockMvc.perform(get("/events/export").param("from", "2000-01-01T00:00:00").param("to", "2000-12-31T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(content().string("case_id,activity,timestamp,details\n"));
        mockMvc.perform(get("/events/export").param("from", "2030-01-01T00:00:00").param("to", "2020-01-01T00:00:00"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/events/export").param("from", "yesterday"))
                .andExpect(status().isBadRequest());
    }
}
