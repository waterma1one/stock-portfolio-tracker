package com.portfoliotracker.holding;

import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingCalculatorTest {

    @Test
    void singleBuyProducesHoldingAtPurchasePrice() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.now())
        );

        List<Holding> holdings = HoldingCalculator.calculate(txs);

        assertThat(holdings).hasSize(1);
        assertThat(holdings.get(0).symbol()).isEqualTo("AAPL");
        assertThat(holdings.get(0).quantity()).isEqualByComparingTo("10");
        assertThat(holdings.get(0).avgCostBasis()).isEqualByComparingTo("100.00");
    }

    @Test
    void partialSellReducesQuantityButKeepsAvgCostBasis() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.parse("2026-01-01T00:00:00Z")),
                Transaction.of("AAPL", TransactionType.SELL, new BigDecimal("4"), new BigDecimal("150.00"), Instant.parse("2026-02-01T00:00:00Z"))
        );

        List<Holding> holdings = HoldingCalculator.calculate(txs);

        assertThat(holdings).hasSize(1);
        assertThat(holdings.get(0).quantity()).isEqualByComparingTo("6");
        assertThat(holdings.get(0).avgCostBasis()).isEqualByComparingTo("100.00");
    }

    @Test
    void sellingEntirePositionExcludesSymbolFromHoldings() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.parse("2026-01-01T00:00:00Z")),
                Transaction.of("AAPL", TransactionType.SELL, new BigDecimal("10"), new BigDecimal("150.00"), Instant.parse("2026-02-01T00:00:00Z"))
        );

        List<Holding> holdings = HoldingCalculator.calculate(txs);

        assertThat(holdings).isEmpty();
    }

    @Test
    void multipleBuysAtDifferentPricesAverageWeighted() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.parse("2026-01-01T00:00:00Z")),
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("200.00"), Instant.parse("2026-02-01T00:00:00Z"))
        );

        List<Holding> holdings = HoldingCalculator.calculate(txs);

        assertThat(holdings.get(0).quantity()).isEqualByComparingTo("20");
        assertThat(holdings.get(0).avgCostBasis()).isEqualByComparingTo("150.0000");
    }

    @Test
    void multipleSymbolsAreGroupedIndependently() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.now()),
                Transaction.of("MSFT", TransactionType.BUY, new BigDecimal("5"), new BigDecimal("300.00"), Instant.now())
        );

        List<Holding> holdings = HoldingCalculator.calculate(txs);

        assertThat(holdings).extracting(Holding::symbol).containsExactlyInAnyOrder("AAPL", "MSFT");
    }

    @Test
    void calculateRealizedPnlAccruesOnSellAtAverageCostBasis() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.parse("2026-01-01T00:00:00Z")),
                Transaction.of("AAPL", TransactionType.SELL, new BigDecimal("4"), new BigDecimal("150.00"), Instant.parse("2026-02-01T00:00:00Z")));

        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(txs);

        // avg cost 100.00, sold 4 @ 150.00 -> realized gain = (150-100)*4 = 200
        assertThat(realizedPnl).isEqualByComparingTo("200");
    }

    @Test
    void calculateRealizedPnlAccumulatesAcrossMultipleSellsAndSymbols() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.parse("2026-01-01T00:00:00Z")),
                Transaction.of("AAPL", TransactionType.SELL, new BigDecimal("5"), new BigDecimal("120.00"), Instant.parse("2026-01-02T00:00:00Z")),
                Transaction.of("MSFT", TransactionType.BUY, new BigDecimal("4"), new BigDecimal("300.00"), Instant.parse("2026-01-03T00:00:00Z")),
                Transaction.of("MSFT", TransactionType.SELL, new BigDecimal("2"), new BigDecimal("250.00"), Instant.parse("2026-01-04T00:00:00Z")));

        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(txs);

        // AAPL: (120-100)*5 = 100. MSFT: (250-300)*2 = -100. Total = 0
        assertThat(realizedPnl).isEqualByComparingTo("0");
    }

    @Test
    void calculateRealizedPnlIsZeroWhenNoSellsHaveOccurred() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.now()));

        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(txs);

        assertThat(realizedPnl).isEqualByComparingTo("0");
    }

    @Test
    void calculateRealizedPnlIsZeroForEmptyTransactionList() {
        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(List.of());

        assertThat(realizedPnl).isEqualByComparingTo("0");
    }
}
