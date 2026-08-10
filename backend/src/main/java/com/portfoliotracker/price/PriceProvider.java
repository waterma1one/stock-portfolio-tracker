package com.portfoliotracker.price;

import java.math.BigDecimal;
import java.util.Optional;

public interface PriceProvider {
    Optional<BigDecimal> getPrice(String symbol);
}
