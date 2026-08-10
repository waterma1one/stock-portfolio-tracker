package com.portfoliotracker.holding.dto;

import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.price.PriceSnapshot;

import java.math.BigDecimal;
import java.util.Optional;

public record HoldingResponse(String symbol, BigDecimal quantity, BigDecimal avgCostBasis,
                               BigDecimal currentPrice, BigDecimal marketValue, BigDecimal unrealizedPnl) {

    public static HoldingResponse from(Holding holding, Optional<PriceSnapshot> latestPrice) {
        BigDecimal currentPrice = latestPrice.map(PriceSnapshot::getPrice).orElse(null);
        BigDecimal marketValue = currentPrice != null ? currentPrice.multiply(holding.quantity()) : null;
        BigDecimal unrealizedPnl = currentPrice != null
                ? currentPrice.subtract(holding.avgCostBasis()).multiply(holding.quantity())
                : null;
        return new HoldingResponse(holding.symbol(), holding.quantity(), holding.avgCostBasis(),
                currentPrice, marketValue, unrealizedPnl);
    }
}
