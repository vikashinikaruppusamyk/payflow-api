package com.example.payflow.entity;

// USER can act only on their own account; ADMIN can view every account and export the event log
public enum Role {
    USER,
    ADMIN
}
