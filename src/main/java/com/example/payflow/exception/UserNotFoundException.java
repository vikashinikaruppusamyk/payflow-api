package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends PayFlowException {
    public UserNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static UserNotFoundException byId(Long userId) {
        return new UserNotFoundException("No user found with id " + userId);
    }

    public static UserNotFoundException byUpiId(String upiId) {
        return new UserNotFoundException("No user found with UPI ID " + upiId);
    }
}
