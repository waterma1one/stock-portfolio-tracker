package com.portfoliotracker.symbolprofile;

import com.portfoliotracker.transaction.TransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "finnhub", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SymbolProfileSyncScheduler {

    private final TransactionRepository transactionRepository;
    private final SymbolProfileService symbolProfileService;

    public SymbolProfileSyncScheduler(TransactionRepository transactionRepository, SymbolProfileService symbolProfileService) {
        this.transactionRepository = transactionRepository;
        this.symbolProfileService = symbolProfileService;
    }

    @Scheduled(fixedRateString = "${finnhub.daily-sync-interval-ms}")
    public void sweep() {
        for (String symbol : transactionRepository.findDistinctSymbols()) {
            symbolProfileService.ensureCached(symbol);
        }
    }
}
