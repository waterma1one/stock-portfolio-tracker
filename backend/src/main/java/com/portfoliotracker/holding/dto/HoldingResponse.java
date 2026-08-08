package com.portfoliotracker.holding.dto;

import com.portfoliotracker.holding.Holding;

import java.math.BigDecimal;

public record HoldingResponse(String symbol, BigDecimal quantity, BigDecimal avgCostBasis) {
    public static HoldingResponse from(Holding holding) {
        return new HoldingResponse(holding.symbol(), holding.quantity(), holding.avgCostBasis());
    }
}
