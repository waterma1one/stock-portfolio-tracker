package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.AllocationResponse;
import com.portfoliotracker.analytics.dto.SectorAllocation;
import com.portfoliotracker.holding.Holding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class AllocationCalculator {

    private static final int PERCENT_SCALE = 4;

    public static AllocationResponse calculate(List<Holding> holdings,
                                                 Function<String, Optional<BigDecimal>> priceLookup,
                                                 Function<String, Optional<String>> sectorLookup) {
        Map<String, BigDecimal> valueBySector = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            Optional<BigDecimal> price = priceLookup.apply(holding.symbol());
            if (price.isEmpty()) {
                continue;
            }
            BigDecimal marketValue = price.get().multiply(holding.quantity());
            String sector = sectorLookup.apply(holding.symbol()).orElse("Unknown");
            valueBySector.merge(sector, marketValue, BigDecimal::add);
            total = total.add(marketValue);
        }

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return new AllocationResponse(List.of());
        }

        BigDecimal finalTotal = total;
        List<SectorAllocation> sectors = valueBySector.entrySet().stream()
                .map(e -> new SectorAllocation(e.getKey(), e.getValue(),
                        e.getValue().multiply(BigDecimal.valueOf(100)).divide(finalTotal, PERCENT_SCALE, RoundingMode.HALF_UP)))
                .toList();

        return new AllocationResponse(sectors);
    }
}
