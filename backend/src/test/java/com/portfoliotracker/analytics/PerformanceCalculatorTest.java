package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.PerformanceResponse;
import com.portfoliotracker.pricehistory.PriceHistory;
import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceCalculatorTest {

    private PriceHistory row(String symbol, LocalDate date, String close) {
        PriceHistory row = new PriceHistory();
        row.setSymbol(symbol);
        row.setPriceDate(date);
        row.setClose(new BigDecimal(close));
        return row;
    }

    private Transaction buy(String symbol, String qty, String price, LocalDate date) {
        return Transaction.of(symbol, TransactionType.BUY, new BigDecimal(qty), new BigDecimal(price),
                date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void computesPercentChangeRelativeToFirstDateWithNonZeroPortfolioValue() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        LocalDate d3 = LocalDate.of(2026, 1, 3);

        List<Transaction> transactions = List.of(buy("AAPL", "10", "100.00", d1));
        Map<String, List<PriceHistory>> historyBySymbol = Map.of(
                "AAPL", List.of(row("AAPL", d1, "100.00"), row("AAPL", d2, "110.00"), row("AAPL", d3, "120.00")));
        List<PriceHistory> spyHistory = List.of(row("SPY", d1, "500.00"), row("SPY", d2, "510.00"), row("SPY", d3, "505.00"));

        PerformanceResponse response = PerformanceCalculator.calculate(transactions, historyBySymbol, spyHistory);

        assertThat(response.points()).hasSize(3);
        assertThat(response.points().get(0).portfolioChangePercent()).isEqualByComparingTo("0");
        assertThat(response.points().get(0).spyChangePercent()).isEqualByComparingTo("0");
        // AAPL 100 -> 110 = +10%
        assertThat(response.points().get(1).portfolioChangePercent()).isEqualByComparingTo("10.0000");
        // SPY 500 -> 510 = +2%
        assertThat(response.points().get(1).spyChangePercent()).isEqualByComparingTo("2.0000");
        // AAPL 100 -> 120 = +20%
        assertThat(response.points().get(2).portfolioChangePercent()).isEqualByComparingTo("20.0000");
        // SPY 500 -> 505 = +1%
        assertThat(response.points().get(2).spyChangePercent()).isEqualByComparingTo("1.0000");
    }

    @Test
    void skipsDatesBeforeFirstTransaction() {
        LocalDate before = LocalDate.of(2025, 12, 31);
        LocalDate txDate = LocalDate.of(2026, 1, 1);

        List<Transaction> transactions = List.of(buy("AAPL", "10", "100.00", txDate));
        Map<String, List<PriceHistory>> historyBySymbol = Map.of("AAPL", List.of(row("AAPL", txDate, "100.00")));
        List<PriceHistory> spyHistory = List.of(row("SPY", before, "500.00"), row("SPY", txDate, "505.00"));

        PerformanceResponse response = PerformanceCalculator.calculate(transactions, historyBySymbol, spyHistory);

        assertThat(response.points()).hasSize(1);
        assertThat(response.points().get(0).date()).isEqualTo(txDate);
    }

    @Test
    void usesMostRecentCloseOnOrBeforeDateWhenExactDateMissing() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        LocalDate d3 = LocalDate.of(2026, 1, 3);

        List<Transaction> transactions = List.of(buy("AAPL", "10", "100.00", d1));
        // No AAPL close recorded for d3 -- should fall back to d1's close (last known before d3)
        Map<String, List<PriceHistory>> historyBySymbol = Map.of("AAPL", List.of(row("AAPL", d1, "100.00")));
        List<PriceHistory> spyHistory = List.of(row("SPY", d1, "500.00"), row("SPY", d2, "510.00"), row("SPY", d3, "520.00"));

        PerformanceResponse response = PerformanceCalculator.calculate(transactions, historyBySymbol, spyHistory);

        assertThat(response.points()).hasSize(3);
        assertThat(response.points().get(2).portfolioChangePercent()).isEqualByComparingTo("0");
    }

    @Test
    void returnsEmptyPointsWhenNoTransactionsExist() {
        List<PriceHistory> spyHistory = List.of(row("SPY", LocalDate.of(2026, 1, 1), "500.00"));

        PerformanceResponse response = PerformanceCalculator.calculate(List.of(), Map.of(), spyHistory);

        assertThat(response.points()).isEmpty();
    }
}
