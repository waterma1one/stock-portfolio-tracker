package com.portfoliotracker.pricehistory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

@Service
public class PriceHistoryService {

    private final CandleProvider candleProvider;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TransactionTemplate transactionTemplate;

    public PriceHistoryService(CandleProvider candleProvider, PriceHistoryRepository priceHistoryRepository,
                                PlatformTransactionManager transactionManager) {
        this.candleProvider = candleProvider;
        this.priceHistoryRepository = priceHistoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void refresh(String symbol, LocalDate from, LocalDate to) {
        List<DailyClose> closes = candleProvider.getDailyCloses(symbol, from, to);
        if (closes.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> persist(symbol, closes));
    }

    private void persist(String symbol, List<DailyClose> closes) {
        // deleteBySymbol is a bulk @Modifying delete, so it executes immediately against the
        // DB instead of queuing an em.remove() in the persistence context. That guarantees it
        // runs before the IDENTITY-generated inserts below, which Hibernate must also execute
        // immediately (IDENTITY ids aren't known until the INSERT runs, so they can't be
        // deferred to flush time). No explicit flush is needed to keep the two in order.
        priceHistoryRepository.deleteBySymbol(symbol);
        List<PriceHistory> rows = closes.stream().map(dc -> {
            PriceHistory row = new PriceHistory();
            row.setSymbol(symbol);
            row.setPriceDate(dc.date());
            row.setClose(dc.close());
            return row;
        }).toList();
        priceHistoryRepository.saveAll(rows);
    }

    public List<PriceHistory> getHistory(String symbol) {
        return priceHistoryRepository.findBySymbolOrderByPriceDateAsc(symbol);
    }
}
