package com.example.payflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;
    @Column(nullable = false)
    private String senderUpiId;
    @Column(nullable = false)
    private String receiverUpiId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    private String note;

    // Every attempt is recorded: PENDING when accepted, then SUCCESS or FAILED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private FailureReason failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public Transaction() {
    }

    public Transaction(String senderUpiId, String receiverUpiId, BigDecimal amount, String note) {
        this.senderUpiId = senderUpiId;
        this.receiverUpiId = receiverUpiId;
        this.amount = amount;
        this.note = note;
        this.status = TransactionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markSucceeded() {
        this.status = TransactionStatus.SUCCESS;
        this.failureReason = null;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(FailureReason reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getSenderUpiId() {
        return senderUpiId;
    }

    public String getReceiverUpiId() {
        return receiverUpiId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
