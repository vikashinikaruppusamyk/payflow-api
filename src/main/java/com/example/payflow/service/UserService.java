package com.example.payflow.service;

import com.example.payflow.entity.User;
import com.example.payflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User registerUser(User user) {
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public User findByUpiId(String upiId) {
        return userRepository.findByUpiId(upiId);
    }

    public List<User> findByBalanceGreaterThanEqual(Double amount) {
        return userRepository.findByBalanceGreaterThanEqual(amount);
    }
}