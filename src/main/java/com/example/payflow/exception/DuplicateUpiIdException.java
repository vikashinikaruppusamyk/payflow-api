package com.example.payflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateUpiIdException extends RuntimeException {
    public DuplicateUpiIdException(String upiId) {
        super("UPI ID already registered: " + upiId);
    }
}
