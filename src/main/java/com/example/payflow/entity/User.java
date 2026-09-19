package com.example.payflow.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(unique = true, nullable = false, length = 100)
    private String upiId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @Column(length = 15)
    private String phoneNumber;
    // BCrypt hash; the plain-text password is never stored or returned
    @Column(length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    // Incremented on every update; used for optimistic locking of balance changes
    @Version
    @Column(nullable = false)
    private Long version;

    public User() {
    }

    public User(String name, String upiId, BigDecimal balance, String phoneNumber) {
        this.name = name;
        this.upiId = upiId;
        this.balance = balance;
        this.phoneNumber = phoneNumber;
    }

    public User(String name, String upiId, BigDecimal balance, String phoneNumber, String passwordHash) {
        this(name, upiId, balance, phoneNumber);
        this.passwordHash = passwordHash;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getVersion() {
        return version;
    }
}