package com.example.payflow.service;

import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.User;
import com.example.payflow.exception.TransferFailedException;
import com.example.payflow.repository.TransactionRepository;
import com.example.payflow.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Runs a single transfer attempt inside one database transaction.
 * Kept separate from TransactionService so that the retry loop there calls this bean
 * through its Spring proxy: every retry gets a fresh transaction and fresh entity versions.
 */
@Component
public class TransferProcessor {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransferProcessor(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // Debit, credit and the SUCCESS status commit together or not at all
    @Transactional
    public Transaction execute(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        BigDecimal amount = transaction.getAmount();

        User sender = userRepository.findByUpiId(transaction.getSenderUpiId());
        User receiver = userRepository.findByUpiId(transaction.getReceiverUpiId());

        if (sender == null) {
            throw TransferFailedException.senderNotFound(transaction.getSenderUpiId(), transactionId);
        }
        if (receiver == null) {
            throw TransferFailedException.receiverNotFound(transaction.getReceiverUpiId(), transactionId);
        }
        if (sender.getBalance().compareTo(amount) < 0) {
            throw TransferFailedException.insufficientBalance(transactionId);
        }

        // Both rows carry a @Version; if another transfer changed either user since we read it,
        // the UPDATE matches zero rows and Hibernate throws an optimistic locking failure on commit
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        transaction.markSucceeded();
        return transaction;
    }
}
