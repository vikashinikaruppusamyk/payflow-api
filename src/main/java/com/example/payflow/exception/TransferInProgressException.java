package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

// A request with the same Idempotency-Key is still being processed; the client should retry shortly
public class TransferInProgressException extends PayFlowException {
    public TransferInProgressException(Long transactionId) {
        super(HttpStatus.CONFLICT, "Transfer " + transactionId
                + " with this Idempotency-Key is still being processed. Retry the same request shortly.");
    }
}
