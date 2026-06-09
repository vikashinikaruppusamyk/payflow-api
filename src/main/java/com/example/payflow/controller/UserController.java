package com.example.payflow.controller;

import com.example.payflow.entity.User;
import com.example.payflow.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.registerUser(user);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{userId}")
    public User getUserById(@PathVariable Long userId) {
        return userService.getUserById(userId);
    }

    @GetMapping("/upi/{upiId}")
    public User getUserByUpiId(@PathVariable String upiId) {
        return userService.findByUpiId(upiId);
    }

    @GetMapping("/balance/{amount}")
    public List<User> getUsersByBalanceGreaterThanEqual(@PathVariable Double amount) {
        return userService.findByBalanceGreaterThanEqual(amount);
    }
}