package com.example.payflow;

import com.example.payflow.entity.Role;
import com.example.payflow.entity.User;
import com.example.payflow.repository.TransactionEventRepository;
import com.example.payflow.repository.TransactionRepository;
import com.example.payflow.repository.UserRepository;
import com.example.payflow.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    protected static final String PASSWORD = "correct-horse-42";

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TransactionRepository transactionRepository;
    @Autowired
    protected TransactionEventRepository eventRepository;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String passwordHash;

    @BeforeEach
    void cleanDatabase() {
        eventRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
    }

    protected User createUser(String upiId, String balance) {
        return userRepository.save(new User(upiId.substring(0, upiId.indexOf('@')), upiId, new BigDecimal(balance),
                null, passwordHash));
    }

    protected User createAdmin(String upiId) {
        User admin = new User("admin", upiId, BigDecimal.ZERO.setScale(2), null, passwordHash);
        admin.setRole(Role.ADMIN);
        return userRepository.save(admin);
    }

    // Authorization header value for an existing user, as a client would send after POST /auth/login
    protected String bearer(String upiId) {
        return "Bearer " + jwtService.generateToken(userRepository.findByUpiId(upiId));
    }

    /**
     * Transfers are sent with the sender's own token, as a real client would (money can only leave the caller's
     * account). When the body has no registered sender, e.g. invalid JSON, the fallback user's token is used.
     */
    protected String bearerForSender(String transferJson, String fallbackUpiId) {
        try {
            String sender = JsonPath.read(transferJson, "$.senderUpiId");
            if (sender != null && userRepository.existsByUpiId(sender.trim().toLowerCase())) {
                return bearer(sender.trim().toLowerCase());
            }
        } catch (RuntimeException ignored) {
            // malformed JSON or no senderUpiId: fall through to the fallback user
        }
        return bearer(fallbackUpiId);
    }

    protected BigDecimal balanceOf(String upiId) {
        return userRepository.findByUpiId(upiId).getBalance();
    }
}
