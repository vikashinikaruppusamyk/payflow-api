package com.example.payflow.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank(message = "Sender UPI ID is required")
        @Pattern(regexp = ValidationPatterns.UPI_ID, message = "Sender UPI ID must look like name@bank")
        String senderUpiId,

        @NotBlank(message = "Receiver UPI ID is required")
        @Pattern(regexp = ValidationPatterns.UPI_ID, message = "Receiver UPI ID must look like name@bank")
        String receiverUpiId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @DecimalMax(value = ValidationPatterns.MAX_TRANSFER_AMOUNT, message = "Amount cannot exceed the per-transaction limit of 100000.00")
        @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Note must be at most 255 characters")
        String note
) {
}
