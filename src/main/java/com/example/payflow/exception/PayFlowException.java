package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected business errors. Each subclass decides which HTTP status it maps to,
 * and GlobalExceptionHandler turns it into a structured error response.
 */
public abstract class PayFlowException extends RuntimeException {
    private final HttpStatus status;

    protected PayFlowException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
