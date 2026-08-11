package com.portfoliotracker.pricehistory;

import org.springframework.stereotype.Service;

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

    public void refresh(String symbol, LocalDate from, LocalDate to) {
        List<DailyClose> closes = candleProvider.getDailyCloses(symbol, from, to);
        if (closes.isEmpty()) {
            return;
        }
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
