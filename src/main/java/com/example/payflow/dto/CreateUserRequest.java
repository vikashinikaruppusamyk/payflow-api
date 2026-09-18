package com.example.payflow.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateUserRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "UPI ID is required")
        @Pattern(regexp = ValidationPatterns.UPI_ID, message = "UPI ID must look like name@bank")
        String upiId,

        @PositiveOrZero(message = "Initial balance cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "Initial balance can have at most 2 decimal places")
        BigDecimal initialBalance,

        @Pattern(regexp = ValidationPatterns.PHONE_NUMBER, message = "Phone number must be a valid 10-digit mobile number")
        String phoneNumber
) {
}
