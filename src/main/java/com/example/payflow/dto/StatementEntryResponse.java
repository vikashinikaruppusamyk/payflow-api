package com.example.payflow.dto;

import com.example.payflow.entity.FailureReason;
import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One line of a user's statement, from that user's point of view
public record StatementEntryResponse(
        Long transactionId,
        Direction direction,
        String counterpartyUpiId,
        BigDecimal amount,
        String note,
        TransactionStatus status,
        FailureReason failureReason,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public enum Direction { DEBIT, CREDIT }

    public static StatementEntryResponse from(Transaction transaction, String upiId) {
        boolean sent = transaction.getSenderUpiId().equals(upiId);
        return new StatementEntryResponse(
                transaction.getTransactionId(),
                sent ? Direction.DEBIT : Direction.CREDIT,
                sent ? transaction.getReceiverUpiId() : transaction.getSenderUpiId(),
                transaction.getAmount(),
                transaction.getNote(),
                transaction.getStatus(),
                transaction.getFailureReason(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt());
    }
}
