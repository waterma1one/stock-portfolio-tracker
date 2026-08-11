package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.PerformancePoint;
import com.portfoliotracker.analytics.dto.PerformanceResponse;
import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.holding.HoldingCalculator;
import com.portfoliotracker.pricehistory.PriceHistory;
import com.portfoliotracker.transaction.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a portfolio-vs-SPY percent-change series.
 *
 * <p>Pure and price-agnostic: it takes entities and already-cached history rows and returns computed
 * results. All I/O (loading transactions, loading history) belongs to the caller.
 *
 * <p>SPY's history dates are the canonical trading-day timeline -- SPY is always fully covered by the
 * history sync scheduler, whereas individual holdings may have gaps that do not align with each other.
 * For each SPY date the portfolio is re-derived from the transactions executed on or before that date
 * and priced at each symbol's most recent close on or before that date. The first date with a non-zero
 * portfolio value becomes the 0% baseline for <em>both</em> series, so the two lines start from the same
 * point and are directly comparable.
 */
public class PerformanceCalculator {

    private static final int PERCENT_SCALE = 4;

    public static PerformanceResponse calculate(List<Transaction> transactions,
                                                  Map<String, List<PriceHistory>> historyBySymbol,
                                                  List<PriceHistory> spyHistory) {
        List<LocalDate> tradingDays = spyHistory.stream().map(PriceHistory::getPriceDate).sorted().toList();

        BigDecimal baselinePortfolioValue = null;
        BigDecimal baselineSpyClose = null;
        List<PerformancePoint> points = new ArrayList<>();

        for (LocalDate date : tradingDays) {
            List<Transaction> asOfDate = transactions.stream()
                    .filter(tx -> !tx.getExecutedAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(date))
                    .toList();
            BigDecimal portfolioValue = portfolioValueAsOf(asOfDate, historyBySymbol, date);

            if (baselinePortfolioValue == null) {
                // Dates before the portfolio holds anything are dropped entirely: a 0-value portfolio
                // has no meaningful percent change, and dividing by it would blow up.
                if (portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                baselinePortfolioValue = portfolioValue;
                // SPY's baseline is deliberately its close on the *portfolio's* first funded day, not
                // SPY's own earliest date. Both series therefore read 0% on the same row.
                baselineSpyClose = closeOnOrBefore(spyHistory, date);
            }

            BigDecimal spyClose = closeOnOrBefore(spyHistory, date);
            BigDecimal portfolioChangePercent = percentChange(baselinePortfolioValue, portfolioValue);
            BigDecimal spyChangePercent = percentChange(baselineSpyClose, spyClose);

            points.add(new PerformancePoint(date, portfolioChangePercent, spyChangePercent));
        }

        return new PerformanceResponse(points);
    }

    private static BigDecimal portfolioValueAsOf(List<Transaction> asOfDate,
                                                  Map<String, List<PriceHistory>> historyBySymbol,
                                                  LocalDate date) {
        List<Holding> holdings = HoldingCalculator.calculate(asOfDate);
        BigDecimal total = BigDecimal.ZERO;
        for (Holding holding : holdings) {
            List<PriceHistory> history = historyBySymbol.getOrDefault(holding.symbol(), List.of());
            BigDecimal close = closeOnOrBefore(history, date);
            if (close == null) {
                // A symbol whose history has not been synced yet contributes 0 rather than throwing,
                // so one un-synced holding cannot take down the whole chart.
                continue;
            }
            total = total.add(close.multiply(holding.quantity()));
        }
        return total;
    }

    /**
     * Most recent close on or before {@code date}, or {@code null} when the symbol has no such row.
     * Carrying the last known close forward covers market holidays, per-symbol gaps, and symbols whose
     * history simply starts later than SPY's.
     */
    private static BigDecimal closeOnOrBefore(List<PriceHistory> history, LocalDate date) {
        BigDecimal result = null;
        LocalDate resultDate = null;
        for (PriceHistory row : history) {
            if (!row.getPriceDate().isAfter(date) && (resultDate == null || row.getPriceDate().isAfter(resultDate))) {
                result = row.getClose();
                resultDate = row.getPriceDate();
            }
        }
        return result;
    }

    private static BigDecimal percentChange(BigDecimal baseline, BigDecimal current) {
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0 || current == null) {
            return BigDecimal.ZERO;
        }
        return current.subtract(baseline).multiply(BigDecimal.valueOf(100)).divide(baseline, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
