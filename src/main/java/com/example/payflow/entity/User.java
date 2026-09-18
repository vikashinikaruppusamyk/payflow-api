package com.example.payflow.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;
    private String name;
    @Column(unique = true, nullable = false)
    private String upiId;
    @Column(precision = 19, scale = 2)
    private BigDecimal balance;
    private String phoneNumber;

    public User() {
    }

    public User(String name, String upiId, BigDecimal balance, String phoneNumber) {
        this.name = name;
        this.upiId = upiId;
        this.balance = balance;
        this.phoneNumber = phoneNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUpiId() {
        return upiId;
    }

    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}