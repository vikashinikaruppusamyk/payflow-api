package com.example.payflow.controller;

import com.example.payflow.dto.TransactionResponse;
import com.example.payflow.dto.TransferRequest;
import com.example.payflow.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse sendMoney(@Valid @RequestBody TransferRequest request) {
        return TransactionResponse.from(transactionService.sendMoney(request));
    }

    // Look up any recorded transfer, including FAILED ones
    @GetMapping("/{transactionId}")
    public TransactionResponse getTransaction(@PathVariable Long transactionId) {
        return TransactionResponse.from(transactionService.getTransaction(transactionId));
    }
}
