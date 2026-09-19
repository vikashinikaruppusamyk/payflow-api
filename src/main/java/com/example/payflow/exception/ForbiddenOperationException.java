package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

// Authenticated, but acting on an account the caller does not own
public class ForbiddenOperationException extends PayFlowException {
    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
