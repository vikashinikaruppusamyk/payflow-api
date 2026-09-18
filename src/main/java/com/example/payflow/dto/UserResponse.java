package com.example.payflow.dto;

import com.example.payflow.entity.User;

import java.math.BigDecimal;

public record UserResponse(
        Long userId,
        String name,
        String upiId,
        BigDecimal balance,
        String phoneNumber
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getName(), user.getUpiId(), user.getBalance(), user.getPhoneNumber());
    }
}
