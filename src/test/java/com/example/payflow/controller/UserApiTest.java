package com.example.payflow.controller;

import com.example.payflow.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersUser() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Priya", "upiId": "Priya@OkAxis", "initialBalance": 1000, "phoneNumber": "9876543210",
                         "password": "correct-horse-42"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/users/")))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.upiId").value("priya@okaxis"))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(userRepository.findByUpiId("priya@okaxis")).isNotNull();
    }

    @Test
    void clientCannotChooseItsOwnIdBalanceOrRole() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Priya", "upiId": "priya@okaxis", "password": "correct-horse-42",
                         "userId": 999, "balance": 1000000, "role": "ADMIN"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(0.00));

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("0.00");
        assertThat(userRepository.findByUpiId("priya@okaxis").getRole().name()).isEqualTo("USER");
    }

    @Test
    void rejectsInvalidRegistrationWithFieldErrors() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "", "upiId": "not-a-upi", "initialBalance": -1, "phoneNumber": "12345", "password": "short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.upiId").exists())
                .andExpect(jsonPath("$.fieldErrors.initialBalance").exists())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must be 8 to 72 characters"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rejectsDuplicateUpiIdIgnoringCase() throws Exception {
        createUser("priya@okaxis", "100.00");

        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Someone Else", "upiId": "PRIYA@okaxis", "password": "correct-horse-42"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("priya@okaxis")));
    }

    @Test
    void usersCanReadTheirOwnAccount() throws Exception {
        Long id = createUser("priya@okaxis", "100.00").getUserId();
        String token = bearer("priya@okaxis");

        mockMvc.perform(get("/users/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.balance").value(100.00));
        mockMvc.perform(get("/users/{id}", id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upiId").value("priya@okaxis"));
        mockMvc.perform(get("/users/upi/{upiId}", "Priya@OkAxis").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id));
    }

    @Test
    void usersCannotReadSomeoneElsesAccount() throws Exception {
        createUser("priya@okaxis", "100.00");
        Long raviId = createUser("ravi@oksbi", "50.00").getUserId();

        mockMvc.perform(get("/users/{id}", raviId).header("Authorization", bearer("priya@okaxis")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only access your own account"));
        mockMvc.perform(get("/users/upi/{upiId}", "ravi@oksbi").header("Authorization", bearer("priya@okaxis")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyAccountAndGets404ForUnknownOnes() throws Exception {
        Long raviId = createUser("ravi@oksbi", "50.00").getUserId();
        createAdmin("admin@payflow");
        String admin = bearer("admin@payflow");

        mockMvc.perform(get("/users/{id}", raviId).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}", 424242).header("Authorization", admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/users/424242"));
        mockMvc.perform(get("/users/upi/{upiId}", "ghost@okaxis").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanListUsers() throws Exception {
        createUser("priya@okaxis", "1000.00");
        createUser("ravi@oksbi", "50.00");
        createAdmin("admin@payflow");
        String admin = bearer("admin@payflow");

        mockMvc.perform(get("/users").header("Authorization", bearer("priya@okaxis")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get("/users").param("minBalance", "500").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].upiId").value("priya@okaxis"));
        mockMvc.perform(get("/users").param("minBalance", "-1").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minBalance").exists());
    }
}
