package com.example.payflow.service;

import com.example.payflow.entity.Transaction;
import com.example.payflow.exception.ConcurrentTransferException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TransactionService {
    private final TransferProcessor transferProcessor;
    private final int maxAttempts;

    public TransactionService(TransferProcessor transferProcessor,
                              @Value("${payflow.transfer.max-attempts:3}") int maxAttempts) {
        this.transferProcessor = transferProcessor;
        this.maxAttempts = maxAttempts;
    }

    public Transaction sendMoney(Transaction transaction) {
        // Validate amount is positive
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        if (transaction.getAmount().stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Transfer amount can have at most 2 decimal places");
        }

        String senderUpiId = UserService.normalizeUpiId(transaction.getSenderUpiId());
        String receiverUpiId = UserService.normalizeUpiId(transaction.getReceiverUpiId());

        // Optimistic locking: conflicts are rare, so instead of holding row locks while we check the
        // balance we detect a concurrent update at commit time and retry the whole attempt.
        // ConcurrencyFailureException covers both version conflicts and database lock/deadlock errors.
        for (int attempt = 1; ; attempt++) {
            try {
                return transferProcessor.transfer(senderUpiId, receiverUpiId, transaction.getAmount(), transaction.getNote());
            } catch (ConcurrencyFailureException e) {
                if (attempt >= maxAttempts) {
                    throw new ConcurrentTransferException(attempt);
                }
                backOff(attempt);
            }
        }
    }

    // Short randomised pause so competing requests do not collide again immediately
    private static void backOff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50L * attempt + 10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying transfer", e);
        }
    }
}
