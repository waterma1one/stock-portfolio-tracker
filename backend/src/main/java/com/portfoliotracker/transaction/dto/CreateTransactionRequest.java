package com.portfoliotracker.transaction.dto;

import com.portfoliotracker.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateTransactionRequest(
        @NotBlank String symbol,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal price,
        @NotNull Instant executedAt
) {
}
