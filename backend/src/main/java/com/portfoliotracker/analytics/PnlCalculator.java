package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.PnlResponse;
import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.holding.HoldingCalculator;
import com.portfoliotracker.transaction.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class PnlCalculator {

    private static final int RESULT_SCALE = 4;

    public static PnlResponse calculate(List<Transaction> transactions, Function<String, Optional<BigDecimal>> priceLookup) {
        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(transactions);
        BigDecimal unrealizedPnl = calculateUnrealizedPnl(transactions, priceLookup);
        return new PnlResponse(realizedPnl.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                unrealizedPnl.setScale(RESULT_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal calculateUnrealizedPnl(List<Transaction> transactions, Function<String, Optional<BigDecimal>> priceLookup) {
        List<Holding> holdings = HoldingCalculator.calculate(transactions);
        BigDecimal unrealized = BigDecimal.ZERO;
        for (Holding holding : holdings) {
            Optional<BigDecimal> price = priceLookup.apply(holding.symbol());
            if (price.isEmpty()) {
                continue;
            }
            unrealized = unrealized.add(price.get().subtract(holding.avgCostBasis()).multiply(holding.quantity()));
        }
        return unrealized;
    }
}
