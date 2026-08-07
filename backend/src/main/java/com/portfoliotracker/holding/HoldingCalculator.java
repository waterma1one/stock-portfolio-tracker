package com.portfoliotracker.holding;

import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HoldingCalculator {

    private static final int INTERNAL_SCALE = 10;
    private static final int RESULT_SCALE = 4;

    public static List<Holding> calculate(List<Transaction> transactions) {
        Map<String, BigDecimal> qtyBySymbol = new LinkedHashMap<>();
        Map<String, BigDecimal> costBySymbol = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            String symbol = tx.getSymbol();
            BigDecimal qty = qtyBySymbol.getOrDefault(symbol, BigDecimal.ZERO);
            BigDecimal cost = costBySymbol.getOrDefault(symbol, BigDecimal.ZERO);

            if (tx.getType() == TransactionType.BUY) {
                cost = cost.add(tx.getQuantity().multiply(tx.getPrice()));
                qty = qty.add(tx.getQuantity());
            } else {
                if (qty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal avgCost = cost.divide(qty, INTERNAL_SCALE, RoundingMode.HALF_UP);
                    cost = cost.subtract(avgCost.multiply(tx.getQuantity()));
                }
                qty = qty.subtract(tx.getQuantity());
            }

            qtyBySymbol.put(symbol, qty);
            costBySymbol.put(symbol, cost);
        }

        List<Holding> holdings = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : qtyBySymbol.entrySet()) {
            String symbol = entry.getKey();
            BigDecimal qty = entry.getValue();
            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal cost = costBySymbol.get(symbol);
                BigDecimal avgCostBasis = cost.divide(qty, RESULT_SCALE, RoundingMode.HALF_UP);
                holdings.add(new Holding(symbol, qty, avgCostBasis));
            }
        }
        return holdings;
    }
}
