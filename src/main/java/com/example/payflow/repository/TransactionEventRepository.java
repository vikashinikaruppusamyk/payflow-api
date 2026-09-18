package com.example.payflow.repository;

import com.example.payflow.entity.TransactionEvent;
import com.example.payflow.entity.TransferActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionEventRepository extends JpaRepository<TransactionEvent, Long> {
    List<TransactionEvent> findByTransactionIdOrderByOccurredAtAscEventIdAsc(Long transactionId);

    List<TransactionEvent> findByOccurredAtBetweenOrderByTransactionIdAscOccurredAtAscEventIdAsc(LocalDateTime from,
                                                                                               LocalDateTime to);

    long countByActivity(TransferActivity activity);
}
