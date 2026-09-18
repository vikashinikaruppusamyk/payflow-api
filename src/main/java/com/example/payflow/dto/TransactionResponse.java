package com.example.payflow.dto;

import com.example.payflow.entity.FailureReason;
import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long transactionId,
        String senderUpiId,
        String receiverUpiId,
        BigDecimal amount,
        String note,
        TransactionStatus status,
        FailureReason failureReason,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.getTransactionId(), transaction.getSenderUpiId(),
                transaction.getReceiverUpiId(), transaction.getAmount(), transaction.getNote(), transaction.getStatus(),
                transaction.getFailureReason(), transaction.getCreatedAt(), transaction.getCompletedAt());
    }
}
