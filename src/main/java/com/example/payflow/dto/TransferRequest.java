package com.example.payflow.dto;

import java.math.BigDecimal;

public record TransferRequest(
        String senderUpiId,
        String receiverUpiId,
        BigDecimal amount,
        String note
) {
}
