package com.example.payflow.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_events")
public class TransactionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;
    @Column(nullable = false)
    private Long transactionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransferActivity activity;
    @Column(nullable = false)
    private LocalDateTime occurredAt;
    private String details;

    protected TransactionEvent() {
    }

    public TransactionEvent(Long transactionId, TransferActivity activity, String details) {
        this.transactionId = transactionId;
        this.activity = activity;
        this.details = details;
        this.occurredAt = LocalDateTime.now();
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public TransferActivity getActivity() {
        return activity;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getDetails() {
        return details;
    }
}
