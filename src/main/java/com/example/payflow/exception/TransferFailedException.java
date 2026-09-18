package com.example.payflow.exception;

import com.example.payflow.entity.FailureReason;
import org.springframework.http.HttpStatus;

/**
 * A transfer that was accepted and recorded, but could not be completed.
 * The FAILED transaction stays in the database with the same failure reason.
 */
public class TransferFailedException extends PayFlowException {
    private final FailureReason reason;
    private final Long transactionId;

    public TransferFailedException(FailureReason reason, String message, Long transactionId) {
        super(statusFor(reason), message);
        this.reason = reason;
        this.transactionId = transactionId;
    }

    public static TransferFailedException senderNotFound(String upiId, Long transactionId) {
        return new TransferFailedException(FailureReason.SENDER_NOT_FOUND, "Sender UPI ID not found: " + upiId, transactionId);
    }

    public static TransferFailedException receiverNotFound(String upiId, Long transactionId) {
        return new TransferFailedException(FailureReason.RECEIVER_NOT_FOUND, "Receiver UPI ID not found: " + upiId, transactionId);
    }

    public static TransferFailedException insufficientBalance(Long transactionId) {
        return new TransferFailedException(FailureReason.INSUFFICIENT_BALANCE, "Insufficient balance", transactionId);
    }

    public static TransferFailedException concurrentUpdate(int attempts, Long transactionId) {
        return new TransferFailedException(FailureReason.CONCURRENT_UPDATE, "Transfer could not be completed after " + attempts
                + " attempts because the account was being updated concurrently. Please retry.", transactionId);
    }

    public static TransferFailedException systemError(Long transactionId) {
        return new TransferFailedException(FailureReason.SYSTEM_ERROR, "Transfer failed due to an internal error", transactionId);
    }

    private static HttpStatus statusFor(FailureReason reason) {
        return switch (reason) {
            case SENDER_NOT_FOUND, RECEIVER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            // 422: the request is well-formed, but the business rule (enough balance) is not met
            case INSUFFICIENT_BALANCE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CONCURRENT_UPDATE -> HttpStatus.CONFLICT;
            case SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public FailureReason getReason() {
        return reason;
    }

    public Long getTransactionId() {
        return transactionId;
    }
}
