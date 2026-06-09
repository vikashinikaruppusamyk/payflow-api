package com.example.payflow.service;

import com.example.payflow.entity.Transaction;
import com.example.payflow.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction sendMoney(Transaction transaction) {
        return transactionRepository.save(transaction);
    }
}