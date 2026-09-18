package com.example.payflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }

    public static UserNotFoundException byId(Long userId) {
        return new UserNotFoundException("No user found with id " + userId);
    }

    public static UserNotFoundException byUpiId(String upiId) {
        return new UserNotFoundException("No user found with UPI ID " + upiId);
    }
}
