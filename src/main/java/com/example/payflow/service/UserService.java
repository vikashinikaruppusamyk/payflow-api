package com.example.payflow.service;

import com.example.payflow.entity.User;
import com.example.payflow.exception.DuplicateUpiIdException;
import com.example.payflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User registerUser(User user) {
        String upiId = normalizeUpiId(user.getUpiId());
        if (upiId == null || upiId.isEmpty()) {
            throw new IllegalArgumentException("UPI ID is required");
        }
        // Fast check for a friendly error; the unique constraint still guards concurrent registrations
        if (userRepository.existsByUpiId(upiId)) {
            throw new DuplicateUpiIdException(upiId);
        }
        user.setUpiId(upiId);
        if (user.getBalance() == null) {
            user.setBalance(BigDecimal.ZERO);
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public User findByUpiId(String upiId) {
        return userRepository.findByUpiId(normalizeUpiId(upiId));
    }

    @Transactional(readOnly = true)
    public List<User> findByBalanceGreaterThanEqual(BigDecimal amount) {
        return userRepository.findByBalanceGreaterThanEqual(amount);
    }

    // UPI IDs are case-insensitive, so they are stored and looked up in lower case
    public static String normalizeUpiId(String upiId) {
        return upiId == null ? null : upiId.trim().toLowerCase(Locale.ROOT);
    }
}
