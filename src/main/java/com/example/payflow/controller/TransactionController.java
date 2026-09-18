package com.example.payflow.controller;

import com.example.payflow.dto.TransactionResponse;
import com.example.payflow.dto.TransferRequest;
import com.example.payflow.service.TransactionService;
import com.example.payflow.service.TransferResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replayed";

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Sends money. Clients should pass a unique Idempotency-Key (e.g. a UUID) per transfer and reuse it
     * when retrying after a timeout: the first request returns 201, retries return 200 with the same body.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> sendMoney(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false)
            @Pattern(regexp = "^[A-Za-z0-9._:-]{1,100}$",
                    message = "Idempotency-Key must be 1-100 characters of letters, digits, '.', '_', ':' or '-'")
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResult result = transactionService.sendMoney(request, idempotencyKey);
        TransactionResponse body = TransactionResponse.from(result.transaction());
        if (result.replayed()) {
            return ResponseEntity.ok().header(IDEMPOTENT_REPLAY_HEADER, "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // Look up any recorded transfer, including FAILED ones
    @GetMapping("/{transactionId}")
    public TransactionResponse getTransaction(@PathVariable Long transactionId) {
        return TransactionResponse.from(transactionService.getTransaction(transactionId));
    }
}
