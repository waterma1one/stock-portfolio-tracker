package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.PnlResponse;
import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PnlCalculatorTest {

    private Transaction tx(String symbol, TransactionType type, String qty, String price) {
        return Transaction.of(symbol, type, new BigDecimal(qty), new BigDecimal(price), Instant.now());
    }

    @Test
    void realizedLegDelegatesToHoldingCalculator() {
        List<Transaction> transactions = List.of(
                tx("AAPL", TransactionType.BUY, "10", "100.00"),
                tx("AAPL", TransactionType.SELL, "4", "150.00"));

        PnlResponse response = PnlCalculator.calculate(transactions, symbol -> Optional.empty());

        // avg cost 100.00, sold 4 @ 150.00 -> realized gain = (150-100)*4 = 200
        assertThat(response.realizedPnl()).isEqualByComparingTo("200.0000");
    }

    @Test
    void unrealizedPnlSumsAcrossHoldingsWithCachedPrices() {
        List<Transaction> transactions = List.of(
                tx("AAPL", TransactionType.BUY, "10", "100.00"),
                tx("MSFT", TransactionType.BUY, "5", "300.00"));
        Map<String, BigDecimal> prices = Map.of("AAPL", new BigDecimal("150.00"), "MSFT", new BigDecimal("280.00"));

        PnlResponse response = PnlCalculator.calculate(transactions, symbol -> Optional.ofNullable(prices.get(symbol)));

        // AAPL: (150-100)*10 = 500. MSFT: (280-300)*5 = -100. Total = 400
        assertThat(response.unrealizedPnl()).isEqualByComparingTo("400.0000");
    }

    @Test
    void unrealizedPnlExcludesHoldingsWithNoCachedPrice() {
        List<Transaction> transactions = List.of(tx("ZZZZ", TransactionType.BUY, "5", "10.00"));

        PnlResponse response = PnlCalculator.calculate(transactions, symbol -> Optional.empty());

        assertThat(response.unrealizedPnl()).isEqualByComparingTo("0.0000");
    }

    @Test
    void returnsZeroForBothWhenNoTransactionsExist() {
        PnlResponse response = PnlCalculator.calculate(List.of(), symbol -> Optional.empty());

        assertThat(response.realizedPnl()).isEqualByComparingTo("0");
        assertThat(response.unrealizedPnl()).isEqualByComparingTo("0");
    }
}
