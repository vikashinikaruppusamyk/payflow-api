package com.example.payflow.repository;

import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    long countByStatus(TransactionStatus status);
}
