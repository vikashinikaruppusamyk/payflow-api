package com.example.payflow.service;

import com.example.payflow.entity.FailureReason;
import com.example.payflow.entity.Transaction;
import com.example.payflow.repository.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Writes transaction status changes in their own transactions (REQUIRES_NEW).
 * A FAILED record saved inside the transfer transaction would be rolled back together with
 * the failed transfer, so failures are recorded here, after the transfer transaction has ended.
 */
@Component
public class TransactionRecorder {
    private final TransactionRepository transactionRepository;

    public TransactionRecorder(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction createPending(String senderUpiId, String receiverUpiId, BigDecimal amount, String note) {
        return transactionRepository.save(new Transaction(senderUpiId, receiverUpiId, amount, note));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction markFailed(Long transactionId, FailureReason reason) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        transaction.markFailed(reason);
        return transaction;
    }
}
