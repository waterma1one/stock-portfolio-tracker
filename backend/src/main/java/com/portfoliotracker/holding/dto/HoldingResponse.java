package com.portfoliotracker.holding.dto;

import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.price.PriceSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public record HoldingResponse(String symbol, BigDecimal quantity, BigDecimal avgCostBasis,
                               BigDecimal currentPrice, BigDecimal marketValue, BigDecimal unrealizedPnl) {

    private static final int MONEY_SCALE = 4;

    public static HoldingResponse from(Holding holding, Optional<PriceSnapshot> latestPrice) {
        BigDecimal currentPrice = latestPrice.map(PriceSnapshot::getPrice).orElse(null);
        BigDecimal marketValue = currentPrice != null ? scale(currentPrice.multiply(holding.quantity())) : null;
        BigDecimal unrealizedPnl = currentPrice != null
                ? scale(currentPrice.subtract(holding.avgCostBasis()).multiply(holding.quantity()))
                : null;
        return new HoldingResponse(holding.symbol(), holding.quantity(), holding.avgCostBasis(),
                currentPrice, marketValue, unrealizedPnl);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
