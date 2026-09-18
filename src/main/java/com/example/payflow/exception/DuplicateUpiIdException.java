package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

public class DuplicateUpiIdException extends PayFlowException {
    public DuplicateUpiIdException(String upiId) {
        super(HttpStatus.CONFLICT, "UPI ID already registered: " + upiId);
    }
}
