package com.example.payflow.dto;

public final class ValidationPatterns {
    // handle@bank, e.g. priya.s@okaxis
    public static final String UPI_ID = "^[a-zA-Z0-9._-]{2,64}@[a-zA-Z]{2,64}$";
    // 10-digit Indian mobile number
    public static final String PHONE_NUMBER = "^[6-9][0-9]{9}$";
    // Per-transaction UPI limit
    public static final String MAX_TRANSFER_AMOUNT = "100000.00";

    private ValidationPatterns() {
    }
}
