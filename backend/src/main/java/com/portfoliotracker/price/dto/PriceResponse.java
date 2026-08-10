package com.portfoliotracker.price.dto;

import com.portfoliotracker.price.PriceSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceResponse(String symbol, BigDecimal price, Instant fetchedAt) {
    public static PriceResponse from(PriceSnapshot snapshot) {
        return new PriceResponse(snapshot.getSymbol(), snapshot.getPrice(), snapshot.getFetchedAt());
    }
}
