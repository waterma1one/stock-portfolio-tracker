package com.portfoliotracker.price;

import com.portfoliotracker.transaction.TransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "finnhub", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class PriceSyncScheduler {

    private final TransactionRepository transactionRepository;
    private final PriceService priceService;

    public PriceSyncScheduler(TransactionRepository transactionRepository, PriceService priceService) {
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
    }

    @Scheduled(fixedRateString = "${finnhub.poll-interval-ms}")
    public void pollAll() {
        for (String symbol : transactionRepository.findDistinctSymbols()) {
            priceService.fetchAndStore(symbol);
        }
    }
}
