package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends PayFlowException {
    public TransactionNotFoundException(Long transactionId) {
        super(HttpStatus.NOT_FOUND, "No transaction found with id " + transactionId);
    }
}
