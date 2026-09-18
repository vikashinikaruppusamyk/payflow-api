package com.example.payflow.controller;

import com.example.payflow.dto.CreateUserRequest;
import com.example.payflow.dto.PageResponse;
import com.example.payflow.dto.StatementEntryResponse;
import com.example.payflow.dto.UserResponse;
import com.example.payflow.entity.TransactionStatus;
import com.example.payflow.entity.User;
import com.example.payflow.service.StatementService;
import com.example.payflow.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@Tag(name = "Users", description = "Register wallet users and view balances and statements")
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final StatementService statementService;

    public UserController(UserService userService, StatementService statementService) {
        this.userService = userService;
        this.statementService = statementService;
    }

    @Operation(summary = "Register a user", description = "UPI IDs are unique and case-insensitive. Returns 409 if the UPI ID is taken.")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.registerUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}").buildAndExpand(user.getUserId()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    // GET /users lists everyone; GET /users?minBalance=500 filters by balance
    @Operation(summary = "List users", description = "Optionally only users whose balance is at least minBalance")
    @GetMapping
    public List<UserResponse> getUsers(@RequestParam(required = false) @PositiveOrZero(message = "minBalance cannot be negative") BigDecimal minBalance) {
        List<User> users = minBalance == null
                ? userService.getAllUsers()
                : userService.findByBalanceGreaterThanEqual(minBalance);
        return users.stream().map(UserResponse::from).toList();
    }

    @Operation(summary = "Get a user by id")
    @GetMapping("/{userId}")
    public UserResponse getUserById(@PathVariable Long userId) {
        return UserResponse.from(userService.getUserById(userId));
    }

    @Operation(summary = "Get a user by UPI ID")
    @GetMapping("/upi/{upiId}")
    public UserResponse getUserByUpiId(@PathVariable String upiId) {
        return UserResponse.from(userService.findByUpiId(upiId));
    }

    // Statement: money sent and received by this UPI ID, newest first, optionally filtered by status
    @Operation(summary = "Transaction history (statement)",
            description = "Money sent (DEBIT) and received (CREDIT), newest first, paginated, optionally filtered by status")
    @GetMapping("/{upiId}/transactions")
    public PageResponse<StatementEntryResponse> getTransactionHistory(
            @PathVariable String upiId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page cannot be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size cannot exceed 100") int size) {
        String normalizedUpiId = UserService.normalizeUpiId(upiId);
        return PageResponse.from(statementService.getStatement(upiId, status, page, size),
                transaction -> StatementEntryResponse.from(transaction, normalizedUpiId));
    }
}
