package com.portfoliotracker.transaction;

import com.portfoliotracker.transaction.dto.CreateTransactionRequest;
import com.portfoliotracker.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/api/portfolios/{portfolioId}/transactions")
    public List<TransactionResponse> list(@PathVariable Long portfolioId) {
        return transactionService.findByPortfolio(portfolioId).stream()
                .map(TransactionResponse::from).toList();
    }

    @PostMapping("/api/portfolios/{portfolioId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@PathVariable Long portfolioId,
                                       @Valid @RequestBody CreateTransactionRequest request) {
        return TransactionResponse.from(transactionService.create(portfolioId, request));
    }

    @DeleteMapping("/api/transactions/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long transactionId) {
        transactionService.delete(transactionId);
    }
}
