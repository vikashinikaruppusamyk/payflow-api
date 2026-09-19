package com.example.payflow.security;

import com.example.payflow.entity.Role;

import java.security.Principal;

/**
 * The caller identified from a verified JWT. Stored as the principal of the Spring Security
 * Authentication, so controllers never trust identity fields sent in the request body.
 */
public record AuthenticatedUser(Long userId, String upiId, Role role) implements Principal {

    @Override
    public String getName() {
        return upiId;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
