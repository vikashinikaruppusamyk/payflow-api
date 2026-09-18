package com.example.payflow.service;

import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.User;
import com.example.payflow.repository.TransactionRepository;
import com.example.payflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // Debit, credit and the transaction record commit together or not at all
    @Transactional
    public Transaction sendMoney(Transaction transaction) {
        // Validate amount is positive
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        if (transaction.getAmount().stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Transfer amount can have at most 2 decimal places");
        }

        transaction.setSenderUpiId(UserService.normalizeUpiId(transaction.getSenderUpiId()));
        transaction.setReceiverUpiId(UserService.normalizeUpiId(transaction.getReceiverUpiId()));

        // Look up sender and receiver
        User sender = userRepository.findByUpiId(transaction.getSenderUpiId());
        User receiver = userRepository.findByUpiId(transaction.getReceiverUpiId());

        if (sender == null) {
            throw new IllegalArgumentException("Sender UPI ID not found");
        }
        if (receiver == null) {
            throw new IllegalArgumentException("Receiver UPI ID not found");
        }

        // Validate sufficient balance
        if (sender.getBalance().compareTo(transaction.getAmount()) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        // Deduct from sender, credit to receiver
        sender.setBalance(sender.getBalance().subtract(transaction.getAmount()));
        receiver.setBalance(receiver.getBalance().add(transaction.getAmount()));

        userRepository.save(sender);
        userRepository.save(receiver);

        // Set timestamp at the moment the transaction is actually processed
        transaction.setTimestamp(java.time.LocalDateTime.now());

        return transactionRepository.save(transaction);
    }
}