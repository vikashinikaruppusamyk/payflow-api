package com.example.payflow.exception;

import org.springframework.http.HttpStatus;

// 422: the request is well-formed, but the business rule (enough balance) is not met
public class InsufficientBalanceException extends PayFlowException {
    public InsufficientBalanceException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
    }
}
