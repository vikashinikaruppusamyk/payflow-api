package com.example.payflow.dto;

import com.example.payflow.entity.Role;

/**
 * @param expiresIn token lifetime in seconds
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String upiId,
        Role role
) {
}
