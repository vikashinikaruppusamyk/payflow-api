package com.example.payflow.service;

import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.User;
import com.example.payflow.repository.TransactionRepository;
import com.example.payflow.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    // Debit, credit and the transaction record commit together or not at all
    @Transactional
    public Transaction transfer(String senderUpiId, String receiverUpiId, BigDecimal amount, String note) {
        User sender = userRepository.findByUpiId(senderUpiId);
        User receiver = userRepository.findByUpiId(receiverUpiId);

        if (sender == null) {
            throw new IllegalArgumentException("Sender UPI ID not found");
        }
        if (receiver == null) {
            throw new IllegalArgumentException("Receiver UPI ID not found");
        }
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        // Both rows carry a @Version; if another transfer changed either user since we read it,
        // the UPDATE matches zero rows and Hibernate throws an optimistic locking failure on commit
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        Transaction transaction = new Transaction(senderUpiId, receiverUpiId, amount, note);
        transaction.setTimestamp(LocalDateTime.now());
        return transactionRepository.save(transaction);
    }
}
