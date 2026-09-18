package com.example.payflow.service;

import com.example.payflow.dto.TransferRequest;
import com.example.payflow.entity.Transaction;
import com.example.payflow.exception.ConcurrentTransferException;
import com.example.payflow.exception.InvalidTransferException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public Transaction sendMoney(TransferRequest request) {
        // Field-level rules (formats, amount range, decimals) are enforced by Bean Validation on TransferRequest
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        String senderUpiId = UserService.normalizeUpiId(request.senderUpiId());
        String receiverUpiId = UserService.normalizeUpiId(request.receiverUpiId());
        if (senderUpiId.equals(receiverUpiId)) {
            throw new InvalidTransferException("Sender and receiver cannot be the same account");
        }

        // Optimistic locking: conflicts are rare, so instead of holding row locks while we check the
        // balance we detect a concurrent update at commit time and retry the whole attempt.
        // ConcurrencyFailureException covers both version conflicts and database lock/deadlock errors.
        for (int attempt = 1; ; attempt++) {
            try {
                return transferProcessor.transfer(senderUpiId, receiverUpiId, amount, request.note());
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
