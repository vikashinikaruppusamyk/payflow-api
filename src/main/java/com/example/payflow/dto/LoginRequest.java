package com.example.payflow.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "UPI ID is required")
        String upiId,

        @NotBlank(message = "Password is required")
        String password
) {
}
