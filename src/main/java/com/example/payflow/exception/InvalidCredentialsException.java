package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

// Same message for an unknown UPI ID and a wrong password, so login cannot be used to discover accounts
public class InvalidCredentialsException extends PayFlowException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid UPI ID or password");
    }
}
