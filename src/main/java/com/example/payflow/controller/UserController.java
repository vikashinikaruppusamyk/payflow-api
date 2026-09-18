package com.example.payflow.controller;

import com.example.payflow.dto.CreateUserRequest;
import com.example.payflow.dto.UserResponse;
import com.example.payflow.entity.User;
import com.example.payflow.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.registerUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}").buildAndExpand(user.getUserId()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    // GET /users lists everyone; GET /users?minBalance=500 filters by balance
    @GetMapping
    public List<UserResponse> getUsers(@RequestParam(required = false) @PositiveOrZero BigDecimal minBalance) {
        List<User> users = minBalance == null
                ? userService.getAllUsers()
                : userService.findByBalanceGreaterThanEqual(minBalance);
        return users.stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{userId}")
    public UserResponse getUserById(@PathVariable Long userId) {
        return UserResponse.from(userService.getUserById(userId));
    }

    @GetMapping("/upi/{upiId}")
    public UserResponse getUserByUpiId(@PathVariable String upiId) {
        return UserResponse.from(userService.findByUpiId(upiId));
    }
}
