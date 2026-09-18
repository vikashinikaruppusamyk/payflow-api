package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransferException extends PayFlowException {
    public InvalidTransferException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
