package com.example.payflow.dto;

import com.example.payflow.entity.TransactionEvent;
import com.example.payflow.entity.TransferActivity;

import java.time.LocalDateTime;

public record TransactionEventResponse(
        Long eventId,
        TransferActivity activity,
        LocalDateTime occurredAt,
        String details
) {
    public static TransactionEventResponse from(TransactionEvent event) {
        return new TransactionEventResponse(event.getEventId(), event.getActivity(), event.getOccurredAt(), event.getDetails());
    }
}
