package com.example.payflow.controller;

import com.example.payflow.dto.TransactionEventResponse;
import com.example.payflow.dto.TransactionResponse;
import com.example.payflow.dto.TransferRequest;
import com.example.payflow.service.EventLogService;
import com.example.payflow.service.TransactionService;
import com.example.payflow.service.TransferResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

@Tag(name = "Transactions", description = "Send money and look up transfers")
@RestController
@RequestMapping("/transactions")
public class TransactionController {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replayed";

    private final TransactionService transactionService;
    private final EventLogService eventLogService;

    public TransactionController(TransactionService transactionService, EventLogService eventLogService) {
        this.transactionService = transactionService;
        this.eventLogService = eventLogService;
    }

    /**
     * Sends money. Clients should pass a unique Idempotency-Key (e.g. a UUID) per transfer and reuse it
     * when retrying after a timeout: the first request returns 201, retries return 200 with the same body.
     */
    @Operation(summary = "Send money", description = """
            201 on success. 200 with header Idempotent-Replayed: true when the Idempotency-Key was already used \
            for the same request. 400 invalid input, 404 unknown sender/receiver, 409 concurrent update or request \
            still in progress, 422 insufficient balance or key reused with a different body.""")
    @PostMapping
    public ResponseEntity<TransactionResponse> sendMoney(
            @Parameter(description = "Unique key per transfer (e.g. a UUID); reuse it when retrying the same transfer")
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
    @Operation(summary = "Get a transfer by id", description = "Includes PENDING and FAILED transfers with their failure reason")
    @GetMapping("/{transactionId}")
    public TransactionResponse getTransaction(@PathVariable Long transactionId) {
        return TransactionResponse.from(transactionService.getTransaction(transactionId));
    }

    @Operation(summary = "Life-cycle events of a transfer",
            description = "Ordered steps, e.g. INITIATED, VALIDATED, DEBITED, CREDITED, COMPLETED or INITIATED, FAILED")
    @GetMapping("/{transactionId}/events")
    public List<TransactionEventResponse> getTransactionEvents(@PathVariable Long transactionId) {
        return eventLogService.eventsFor(transactionId).stream().map(TransactionEventResponse::from).toList();
    }
}
