package com.example.payflow.dto;

import java.math.BigDecimal;

public record CreateUserRequest(
        String name,
        String upiId,
        BigDecimal initialBalance,
        String phoneNumber
) {
}
