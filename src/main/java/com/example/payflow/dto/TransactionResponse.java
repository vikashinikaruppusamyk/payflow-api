package com.example.payflow.dto;

import com.example.payflow.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long transactionId,
        String senderUpiId,
        String receiverUpiId,
        BigDecimal amount,
        String note,
        LocalDateTime timestamp
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.getTransactionId(), transaction.getSenderUpiId(),
                transaction.getReceiverUpiId(), transaction.getAmount(), transaction.getNote(), transaction.getTimestamp());
    }
}
