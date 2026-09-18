package com.example.payflow.service;

import com.example.payflow.dto.TransferRequest;
import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.TransferActivity;
import com.example.payflow.exception.IdempotencyKeyReuseException;
import com.example.payflow.exception.InvalidTransferException;
import com.example.payflow.exception.TransactionNotFoundException;
import com.example.payflow.exception.TransferFailedException;
import com.example.payflow.exception.TransferInProgressException;
import com.example.payflow.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrates a transfer. Deliberately not @Transactional itself: it coordinates several
 * short transactions (record PENDING, transfer attempt(s), record FAILED) so that a failed
 * attempt can be rolled back while the failure itself is still recorded.
 */
@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransferProcessor transferProcessor;
    private final TransactionRecorder transactionRecorder;
    private final TransactionRepository transactionRepository;
    private final int maxAttempts;

    public TransactionService(TransferProcessor transferProcessor,
                              TransactionRecorder transactionRecorder,
                              TransactionRepository transactionRepository,
                              @Value("${payflow.transfer.max-attempts:3}") int maxAttempts) {
        this.transferProcessor = transferProcessor;
        this.transactionRecorder = transactionRecorder;
        this.transactionRepository = transactionRepository;
        this.maxAttempts = maxAttempts;
    }

    public Transaction sendMoney(TransferRequest request) {
        return sendMoney(request, null).transaction();
    }

    /**
     * @param idempotencyKey optional client-generated key; retrying a request with the same key returns the
     *                       original result instead of moving money a second time
     */
    public TransferResult sendMoney(TransferRequest request, String idempotencyKey) {
        // Field-level rules (formats, amount range, decimals) are enforced by Bean Validation on TransferRequest
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        String senderUpiId = UserService.normalizeUpiId(request.senderUpiId());
        String receiverUpiId = UserService.normalizeUpiId(request.receiverUpiId());
        if (senderUpiId.equals(receiverUpiId)) {
            throw new InvalidTransferException("Sender and receiver cannot be the same account");
        }

        if (idempotencyKey != null) {
            Optional<Transaction> previous = transactionRepository.findBySenderUpiIdAndIdempotencyKey(senderUpiId, idempotencyKey);
            if (previous.isPresent()) {
                return replay(previous.get(), receiverUpiId, amount, request.note());
            }
        }

        Transaction pending;
        try {
            pending = transactionRecorder.createPending(senderUpiId, receiverUpiId, amount, request.note(), idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same key raced past the lookup; the unique constraint let only one insert win
            Transaction winner = idempotencyKey == null ? null
                    : transactionRepository.findBySenderUpiIdAndIdempotencyKey(senderUpiId, idempotencyKey).orElse(null);
            if (winner == null) {
                throw e;
            }
            return replay(winner, receiverUpiId, amount, request.note());
        }
        return new TransferResult(process(pending.getTransactionId()), false);
    }

    // Same key must mean same request; the stored outcome is returned without touching any balance
    private TransferResult replay(Transaction previous, String receiverUpiId, BigDecimal amount, String note) {
        boolean sameRequest = previous.getReceiverUpiId().equals(receiverUpiId)
                && previous.getAmount().compareTo(amount) == 0
                && Objects.equals(previous.getNote(), note);
        if (!sameRequest) {
            throw new IdempotencyKeyReuseException(previous.getIdempotencyKey());
        }
        transactionRecorder.recordEvent(previous.getTransactionId(), TransferActivity.REPLAYED,
                "Duplicate request with the same Idempotency-Key");
        return switch (previous.getStatus()) {
            case SUCCESS -> new TransferResult(previous, true);
            case FAILED -> throw TransferFailedException.replayOf(previous);
            case PENDING -> throw new TransferInProgressException(previous.getTransactionId());
        };
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private Transaction process(Long transactionId) {
        // Optimistic locking: conflicts are rare, so instead of holding row locks while we check the
        // balance we detect a concurrent update at commit time and retry the whole attempt.
        // ConcurrencyFailureException covers both version conflicts and database lock/deadlock errors.
        for (int attempt = 1; ; attempt++) {
            try {
                return transferProcessor.execute(transactionId);
            } catch (ConcurrencyFailureException e) {
                if (attempt >= maxAttempts) {
                    throw fail(TransferFailedException.concurrentUpdate(attempt, transactionId));
                }
                backOff(attempt);
                transactionRecorder.recordEvent(transactionId, TransferActivity.RETRIED,
                        "Attempt " + (attempt + 1) + " after concurrent update");
            } catch (TransferFailedException e) {
                throw fail(e);
            } catch (RuntimeException e) {
                log.error("Transfer {} failed unexpectedly", transactionId, e);
                throw fail(TransferFailedException.systemError(transactionId));
            }
        }
    }

    // The attempt's own transaction has already rolled back, so the FAILED status is written separately
    private TransferFailedException fail(TransferFailedException failure) {
        transactionRecorder.markFailed(failure.getTransactionId(), failure.getReason());
        return failure;
    }

    // Short randomised pause so competing requests do not collide again immediately
    private static void backOff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50L * attempt + 10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying transfer", e);
        }
    }
}
