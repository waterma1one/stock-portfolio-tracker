package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.AllocationResponse;
import com.portfoliotracker.analytics.dto.SectorAllocation;
import com.portfoliotracker.holding.Holding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationCalculatorTest {

    @Test
    void groupsMarketValueBySectorAndComputesPercent() {
        List<Holding> holdings = List.of(
                new Holding("AAPL", new BigDecimal("10"), new BigDecimal("100")),
                new Holding("MSFT", new BigDecimal("5"), new BigDecimal("200")),
                new Holding("JPM", new BigDecimal("2"), new BigDecimal("150")));
        Map<String, BigDecimal> prices = Map.of(
                "AAPL", new BigDecimal("150"), "MSFT", new BigDecimal("300"), "JPM", new BigDecimal("175"));
        Map<String, String> sectors = Map.of("AAPL", "Technology", "MSFT", "Technology", "JPM", "Financials");

        AllocationResponse response = AllocationCalculator.calculate(holdings,
                symbol -> Optional.ofNullable(prices.get(symbol)),
                symbol -> Optional.ofNullable(sectors.get(symbol)));

        // AAPL: 10*150=1500, MSFT: 5*300=1500, JPM: 2*175=350 -- total 3350
        // Technology: 3000 (89.55%), Financials: 350 (10.45%)
        assertThat(response.sectors()).hasSize(2);
        SectorAllocation tech = response.sectors().stream().filter(s -> s.sector().equals("Technology")).findFirst().orElseThrow();
        assertThat(tech.marketValue()).isEqualByComparingTo("3000");
        assertThat(tech.percent()).isEqualByComparingTo("89.5522");
    }

    @Test
    void groupsHoldingsWithNoCachedSectorUnderUnknown() {
        List<Holding> holdings = List.of(new Holding("ZZZZ", new BigDecimal("1"), new BigDecimal("10")));
        Map<String, BigDecimal> prices = Map.of("ZZZZ", new BigDecimal("20"));

        AllocationResponse response = AllocationCalculator.calculate(holdings,
                symbol -> Optional.ofNullable(prices.get(symbol)),
                symbol -> Optional.empty());

        assertThat(response.sectors()).hasSize(1);
        assertThat(response.sectors().get(0).sector()).isEqualTo("Unknown");
        assertThat(response.sectors().get(0).percent()).isEqualByComparingTo("100.0000");
    }

    @Test
    void excludesHoldingsWithNoCachedPriceFromTotals() {
        List<Holding> holdings = List.of(
                new Holding("AAPL", new BigDecimal("10"), new BigDecimal("100")),
                new Holding("ZZZZ", new BigDecimal("5"), new BigDecimal("10")));
        Map<String, BigDecimal> prices = Map.of("AAPL", new BigDecimal("150"));
        Map<String, String> sectors = Map.of("AAPL", "Technology");

        AllocationResponse response = AllocationCalculator.calculate(holdings,
                symbol -> Optional.ofNullable(prices.get(symbol)),
                symbol -> Optional.ofNullable(sectors.get(symbol)));

        assertThat(response.sectors()).hasSize(1);
        assertThat(response.sectors().get(0).sector()).isEqualTo("Technology");
        assertThat(response.sectors().get(0).percent()).isEqualByComparingTo("100.0000");
    }

    @Test
    void returnsEmptySectorListWhenNoHoldingsHavePrices() {
        List<Holding> holdings = List.of(new Holding("ZZZZ", new BigDecimal("5"), new BigDecimal("10")));

        AllocationResponse response = AllocationCalculator.calculate(holdings, symbol -> Optional.empty(), symbol -> Optional.empty());

        assertThat(response.sectors()).isEmpty();
    }
}
