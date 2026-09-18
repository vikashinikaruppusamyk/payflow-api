package com.example.payflow.repository;

import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    long countByStatus(TransactionStatus status);

    // Both directions of a user's money movement; served by the sender and receiver indexes
    @Query("SELECT t FROM Transaction t WHERE t.senderUpiId = :upiId OR t.receiverUpiId = :upiId")
    Page<Transaction> findStatement(@Param("upiId") String upiId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE (t.senderUpiId = :upiId OR t.receiverUpiId = :upiId) AND t.status = :status")
    Page<Transaction> findStatementByStatus(@Param("upiId") String upiId, @Param("status") TransactionStatus status,
                                            Pageable pageable);
}
