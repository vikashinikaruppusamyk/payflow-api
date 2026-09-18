package com.example.payflow.entity;

// Steps in a transfer's life cycle, as recorded in the event log
public enum TransferActivity {
    INITIATED,
    VALIDATED,
    DEBITED,
    CREDITED,
    COMPLETED,
    RETRIED,
    FAILED,
    REPLAYED
}
