package com.portfoliotracker.holding;

import java.math.BigDecimal;

public record Holding(String symbol, BigDecimal quantity, BigDecimal avgCostBasis) {
}
