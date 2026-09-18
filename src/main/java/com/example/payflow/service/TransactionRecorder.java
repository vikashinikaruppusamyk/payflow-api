package com.example.payflow.service;

import com.example.payflow.entity.FailureReason;
import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.TransactionEvent;
import com.example.payflow.entity.TransferActivity;
import com.example.payflow.repository.TransactionEventRepository;
import com.example.payflow.repository.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Writes transaction status changes and life-cycle events in their own transactions (REQUIRES_NEW).
 * A FAILED record saved inside the transfer transaction would be rolled back together with
 * the failed transfer, so failures are recorded here, after the transfer transaction has ended.
 */
@Component
public class TransactionRecorder {
    private final TransactionRepository transactionRepository;
    private final TransactionEventRepository eventRepository;

    public TransactionRecorder(TransactionRepository transactionRepository, TransactionEventRepository eventRepository) {
        this.transactionRepository = transactionRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction createPending(String senderUpiId, String receiverUpiId, BigDecimal amount, String note,
                                     String idempotencyKey) {
        Transaction transaction = transactionRepository.save(
                new Transaction(senderUpiId, receiverUpiId, amount, note, idempotencyKey));
        eventRepository.save(new TransactionEvent(transaction.getTransactionId(), TransferActivity.INITIATED, null));
        return transaction;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction markFailed(Long transactionId, FailureReason reason) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        transaction.markFailed(reason);
        eventRepository.save(new TransactionEvent(transactionId, TransferActivity.FAILED, reason.name()));
        return transaction;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(Long transactionId, TransferActivity activity, String details) {
        eventRepository.save(new TransactionEvent(transactionId, activity, details));
    }
}
