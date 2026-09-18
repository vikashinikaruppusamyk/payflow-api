package com.example.payflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConcurrentTransferException extends RuntimeException {
    public ConcurrentTransferException(int attempts) {
        super("Transfer could not be completed after " + attempts
                + " attempts because the account was being updated concurrently. Please retry.");
    }
}
