package com.example.payflow.service;

import com.example.payflow.dto.CreateUserRequest;
import com.example.payflow.entity.User;
import com.example.payflow.exception.DuplicateUpiIdException;
import com.example.payflow.exception.UserNotFoundException;
import com.example.payflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public User registerUser(CreateUserRequest request) {
        String upiId = normalizeUpiId(request.upiId());
        // Fast check for a friendly error; the unique constraint still guards concurrent registrations
        if (userRepository.existsByUpiId(upiId)) {
            throw new DuplicateUpiIdException(upiId);
        }
        BigDecimal initialBalance = request.initialBalance() == null ? BigDecimal.ZERO : request.initialBalance();
        initialBalance = initialBalance.setScale(2, RoundingMode.UNNECESSARY);
        User user = new User(request.name().trim(), upiId, initialBalance, request.phoneNumber());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> UserNotFoundException.byId(userId));
    }

    @Transactional(readOnly = true)
    public User findByUpiId(String upiId) {
        User user = userRepository.findByUpiId(normalizeUpiId(upiId));
        if (user == null) {
            throw UserNotFoundException.byUpiId(upiId);
        }
        return user;
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
