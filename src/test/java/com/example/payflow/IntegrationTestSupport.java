package com.example.payflow;

import com.example.payflow.entity.User;
import com.example.payflow.repository.TransactionEventRepository;
import com.example.payflow.repository.TransactionRepository;
import com.example.payflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

/**
 * Boots the full application against a real database and wipes all tables before each test.
 * Every integration test extends this class so they share one cached Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TransactionRepository transactionRepository;
    @Autowired
    protected TransactionEventRepository eventRepository;

    @BeforeEach
    void cleanDatabase() {
        eventRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    protected User createUser(String upiId, String balance) {
        return userRepository.save(new User(upiId.substring(0, upiId.indexOf('@')), upiId, new BigDecimal(balance), null));
    }

    protected BigDecimal balanceOf(String upiId) {
        return userRepository.findByUpiId(upiId).getBalance();
    }
}
