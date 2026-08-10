package com.portfoliotracker.transaction.dto;

import com.portfoliotracker.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateTransactionRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z]{1,10}(\\.[A-Z]{1,2})?$",
                message = "must be 1-10 uppercase letters, optionally with a .X suffix (e.g. AAPL, BRK.B)")
        String symbol,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal price,
        @NotNull Instant executedAt
) {
    public CreateTransactionRequest {
        symbol = symbol == null ? null : symbol.trim().toUpperCase();
    }
}
