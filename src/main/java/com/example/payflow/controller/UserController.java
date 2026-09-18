package com.example.payflow.controller;

import com.example.payflow.dto.CreateUserRequest;
import com.example.payflow.dto.ErrorResponse;
import com.example.payflow.dto.PageResponse;
import com.example.payflow.dto.StatementEntryResponse;
import com.example.payflow.dto.UserResponse;
import com.example.payflow.entity.TransactionStatus;
import com.example.payflow.entity.User;
import com.example.payflow.service.StatementService;
import com.example.payflow.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "Register a user", description = "UPI IDs are unique and case-insensitive (stored in lower case).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered",
                    headers = @Header(name = "Location", description = "URL of the new user",
                            schema = @Schema(type = "string", example = "http://localhost:8080/users/1")),
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                            name = "Validation error", value = """
                                    {"timestamp": "2026-09-18T08:00:00Z", "status": 400, "error": "Bad Request",
                                     "message": "Request validation failed", "path": "/users",
                                     "fieldErrors": {"upiId": "UPI ID must look like name@bank",
                                                     "initialBalance": "Initial balance cannot be negative"}}"""))),
            @ApiResponse(responseCode = "409", description = "UPI ID is already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                            name = "Duplicate UPI ID", value = """
                                    {"timestamp": "2026-09-18T08:00:00Z", "status": 409, "error": "Conflict",
                                     "message": "UPI ID already registered: priya@okaxis", "path": "/users"}""")))
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.registerUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}").buildAndExpand(user.getUserId()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    // GET /users lists everyone; GET /users?minBalance=500 filters by balance
    @Operation(summary = "List users", description = "Optionally only users whose balance is at least minBalance")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching users",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "400", description = "minBalance is negative or not a number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public List<UserResponse> getUsers(@RequestParam(required = false) @PositiveOrZero(message = "minBalance cannot be negative") BigDecimal minBalance) {
        List<User> users = minBalance == null
                ? userService.getAllUsers()
                : userService.findByBalanceGreaterThanEqual(minBalance);
        return users.stream().map(UserResponse::from).toList();
    }

    @Operation(summary = "Get a user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "User id is not a number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No user with this id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}")
    public UserResponse getUserById(@PathVariable Long userId) {
        return UserResponse.from(userService.getUserById(userId));
    }

    @Operation(summary = "Get a user by UPI ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "No user with this UPI ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/upi/{upiId}")
    public UserResponse getUserByUpiId(@PathVariable String upiId) {
        return UserResponse.from(userService.findByUpiId(upiId));
    }

    // Statement: money sent and received by this UPI ID, newest first, optionally filtered by status
    @Operation(summary = "Transaction history (statement)",
            description = "Money sent (DEBIT) and received (CREDIT), newest first, paginated, optionally filtered by status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of the statement"),
            @ApiResponse(responseCode = "400", description = "Invalid page, size (max 100) or status value",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No user with this UPI ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
