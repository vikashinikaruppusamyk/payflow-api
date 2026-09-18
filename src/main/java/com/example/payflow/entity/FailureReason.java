package com.example.payflow.entity;

public enum FailureReason {
    SENDER_NOT_FOUND,
    RECEIVER_NOT_FOUND,
    INSUFFICIENT_BALANCE,
    CONCURRENT_UPDATE,
    SYSTEM_ERROR
}
