package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

// The same Idempotency-Key was sent with a different request body
public class IdempotencyKeyReuseException extends PayFlowException {
    public IdempotencyKeyReuseException(String idempotencyKey) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                "Idempotency-Key '" + idempotencyKey + "' was already used for a different transfer request");
    }
}
