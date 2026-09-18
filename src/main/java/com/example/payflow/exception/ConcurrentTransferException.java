package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

public class ConcurrentTransferException extends PayFlowException {
    public ConcurrentTransferException(int attempts) {
        super(HttpStatus.CONFLICT, "Transfer could not be completed after " + attempts
                + " attempts because the account was being updated concurrently. Please retry.");
    }
}
