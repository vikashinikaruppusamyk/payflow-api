package com.example.payflow.controller;

import com.example.payflow.dto.ErrorResponse;
import com.example.payflow.dto.TransactionEventResponse;
import com.example.payflow.dto.TransactionResponse;
import com.example.payflow.dto.TransferRequest;
import com.example.payflow.entity.Transaction;
import com.example.payflow.exception.TransactionNotFoundException;
import com.example.payflow.security.AccessGuard;
import com.example.payflow.service.EventLogService;
import com.example.payflow.service.TransactionService;
import com.example.payflow.service.TransferResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final AccessGuard accessGuard;

    public TransactionController(TransactionService transactionService, EventLogService eventLogService,
                                 AccessGuard accessGuard) {
        this.transactionService = transactionService;
        this.eventLogService = eventLogService;
        this.accessGuard = accessGuard;
    }

    /**
     * Sends money. Clients should pass a unique Idempotency-Key (e.g. a UUID) per transfer and reuse it
     * when retrying after a timeout: the first request returns 201, retries return 200 with the same body.
     */
    @Operation(summary = "Send money", description = """
            Moves money from the logged-in user's account to another registered user in one atomic database \
            transaction; senderUpiId must be your own UPI ID. \
            Send a unique Idempotency-Key (e.g. a UUID) per transfer and reuse it when retrying: \
            a retry returns the original result instead of moving money again.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer completed",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "200", description = """
                    Idempotent replay: this Idempotency-Key was already used for the same request, \
                    so the original result is returned and no money moves""",
                    headers = @Header(name = IDEMPOTENT_REPLAY_HEADER, description = "Always \"true\" on a replay",
                            schema = @Schema(type = "string", example = "true")),
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = """
                    Invalid input: field validation errors, malformed JSON, invalid Idempotency-Key, \
                    or sender and receiver are the same account""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                            name = "Validation error", value = """
                                    {"timestamp": "2026-09-18T08:00:00Z", "status": 400, "error": "Bad Request",
                                     "message": "Request validation failed", "path": "/transactions",
                                     "fieldErrors": {"amount": "Amount must be at least 0.01"}}"""))),
            @ApiResponse(responseCode = "403", description = "senderUpiId is not the logged-in user's UPI ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Receiver UPI ID is not registered; the attempt is recorded as FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                            name = "Unknown receiver", value = """
                                    {"timestamp": "2026-09-18T08:00:00Z", "status": 404, "error": "Not Found",
                                     "message": "Receiver UPI ID not found: ghost@oksbi", "path": "/transactions",
                                     "failureReason": "RECEIVER_NOT_FOUND", "transactionId": 3}"""))),
            @ApiResponse(responseCode = "409", description = """
                    Account was updated concurrently and retries were exhausted (retry with a new key), \
                    or a request with the same Idempotency-Key is still being processed (retry shortly)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = """
                    Insufficient balance (recorded as FAILED), or the Idempotency-Key was already used \
                    for a different request body""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(
                            name = "Insufficient balance", value = """
                                    {"timestamp": "2026-09-18T08:00:00Z", "status": 422, "error": "Unprocessable Entity",
                                     "message": "Insufficient balance", "path": "/transactions",
                                     "failureReason": "INSUFFICIENT_BALANCE", "transactionId": 2}""")))
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> sendMoney(
            @Parameter(description = "Unique key per transfer (e.g. a UUID); reuse it when retrying the same transfer",
                    example = "3f6c1a52-9d4e-4b8a-a1c2-7e5f0b9d2c10")
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false)
            @Pattern(regexp = "^[A-Za-z0-9._:-]{1,100}$",
                    message = "Idempotency-Key must be 1-100 characters of letters, digits, '.', '_', ':' or '-'")
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        accessGuard.requireSender(request.senderUpiId());
        TransferResult result = transactionService.sendMoney(request, idempotencyKey);
        TransactionResponse body = TransactionResponse.from(result.transaction());
        if (result.replayed()) {
            return ResponseEntity.ok().header(IDEMPOTENT_REPLAY_HEADER, "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // Look up any recorded transfer, including FAILED ones
    @Operation(summary = "Get a transfer by id", description = """
            Includes PENDING and FAILED transfers with their failure reason. Visible to the sender, the receiver \
            and admins; for anyone else it is reported as not found, so transfer ids cannot be probed.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer found",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Transaction id is not a number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No transfer with this id that you are allowed to see",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{transactionId}")
    public TransactionResponse getTransaction(@PathVariable Long transactionId) {
        return TransactionResponse.from(findVisibleTransaction(transactionId));
    }

    @Operation(summary = "Life-cycle events of a transfer", description = """
            Ordered steps, e.g. INITIATED, VALIDATED, DEBITED, CREDITED, COMPLETED or INITIATED, FAILED. \
            Visible to the sender, the receiver and admins.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events in the order they happened",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TransactionEventResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Transaction id is not a number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No transfer with this id that you are allowed to see",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{transactionId}/events")
    public List<TransactionEventResponse> getTransactionEvents(@PathVariable Long transactionId) {
        findVisibleTransaction(transactionId);
        return eventLogService.eventsFor(transactionId).stream().map(TransactionEventResponse::from).toList();
    }

    // 404 rather than 403 for other people's transfers: a 403 would confirm that the id exists
    private Transaction findVisibleTransaction(Long transactionId) {
        Transaction transaction = transactionService.getTransaction(transactionId);
        if (!accessGuard.canView(transaction)) {
            throw new TransactionNotFoundException(transactionId);
        }
        return transaction;
    }
}
