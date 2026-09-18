package com.example.payflow.service;

import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.TransactionStatus;
import com.example.payflow.exception.UserNotFoundException;
import com.example.payflow.repository.TransactionRepository;
import com.example.payflow.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public StatementService(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // Newest first; transactionId breaks ties so paging is stable when timestamps are equal
    @Transactional(readOnly = true)
    public Page<Transaction> getStatement(String upiId, TransactionStatus status, int page, int size) {
        String normalizedUpiId = UserService.normalizeUpiId(upiId);
        if (!userRepository.existsByUpiId(normalizedUpiId)) {
            throw UserNotFoundException.byUpiId(upiId);
        }
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "transactionId")));
        return status == null
                ? transactionRepository.findStatement(normalizedUpiId, pageable)
                : transactionRepository.findStatementByStatus(normalizedUpiId, status, pageable);
    }
}
