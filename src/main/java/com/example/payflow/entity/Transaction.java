package com.example.payflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;
    private String senderUpId;
    private String receiverUpId;
    private Double amount;
    private String note;
    private LocalDateTime timestamp;

    public Transaction() {
    }

    public Transaction(String senderUpId, String receiverUpId, Double amount, String note) {
        this.senderUpId = senderUpId;
        this.receiverUpId = receiverUpId;
        this.amount = amount;
        this.note = note;
        this.timestamp = LocalDateTime.now();
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getSenderUpId() {
        return senderUpId;
    }

    public void setSenderUpId(String senderUpId) {
        this.senderUpId = senderUpId;
    }

    public String getReceiverUpId() {
        return receiverUpId;
    }

    public void setReceiverUpId(String receiverUpId) {
        this.receiverUpId = receiverUpId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}