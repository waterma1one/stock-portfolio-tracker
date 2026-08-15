package com.portfoliotracker.pricehistory;

import com.portfoliotracker.transaction.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "finnhub", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class PriceHistorySyncScheduler {

    private static final String BENCHMARK_SYMBOL = "SPY";

    private final TransactionRepository transactionRepository;
    private final PriceHistoryService priceHistoryService;
    private final int lookbackDays;

    public PriceHistorySyncScheduler(TransactionRepository transactionRepository,
                                      PriceHistoryService priceHistoryService,
                                      @Value("${finnhub.history-lookback-days}") int lookbackDays) {
        this.transactionRepository = transactionRepository;
        this.priceHistoryService = priceHistoryService;
        this.lookbackDays = lookbackDays;
    }

    @Scheduled(fixedRateString = "${finnhub.daily-sync-interval-ms}")
    public void refreshAll() {
        Set<String> symbols = new HashSet<>(transactionRepository.findDistinctSymbols());
        symbols.add(BENCHMARK_SYMBOL);

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(lookbackDays);
        for (String symbol : symbols) {
            priceHistoryService.refresh(symbol, from, to);
        }
    }
}
