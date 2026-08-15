package com.portfoliotracker.pricehistory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PriceHistoryService {

    private final CandleProvider candleProvider;
    private final PriceHistoryRepository priceHistoryRepository;

    public PriceHistoryService(CandleProvider candleProvider, PriceHistoryRepository priceHistoryRepository) {
        this.candleProvider = candleProvider;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
    public void refresh(String symbol, LocalDate from, LocalDate to) {
        List<DailyClose> closes = candleProvider.getDailyCloses(symbol, from, to);
        if (closes.isEmpty()) {
            return;
        }
        priceHistoryRepository.deleteBySymbol(symbol);
        // deleteBySymbol only queues em.remove() calls, and Hibernate flushes inserts before
        // deletes. Without an explicit flush the re-inserted rows below collide with the rows
        // being deleted on the (symbol, price_date) unique constraint, since refresh() is
        // normally called with a date range that overlaps what is already stored.
        priceHistoryRepository.flush();
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
