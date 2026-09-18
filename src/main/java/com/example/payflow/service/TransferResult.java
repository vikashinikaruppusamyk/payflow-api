package com.example.payflow.service;

import com.example.payflow.entity.Transaction;

/**
 * Outcome of a transfer request.
 *
 * @param replayed true when the Idempotency-Key matched an earlier request and the stored result was returned
 */
public record TransferResult(Transaction transaction, boolean replayed) {
}
