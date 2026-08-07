package com.portfoliotracker.transaction.dto;

import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id, Long portfolioId, String symbol, TransactionType type,
        BigDecimal quantity, BigDecimal price, Instant executedAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getId(), t.getPortfolioId(), t.getSymbol(), t.getType(),
                t.getQuantity(), t.getPrice(), t.getExecutedAt());
    }
}
