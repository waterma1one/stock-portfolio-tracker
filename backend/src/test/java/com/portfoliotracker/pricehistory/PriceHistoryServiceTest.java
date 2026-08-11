package com.portfoliotracker.pricehistory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock
    CandleProvider candleProvider;

    @Mock
    PriceHistoryRepository priceHistoryRepository;

    @Test
    void refreshReplacesExistingHistoryForSymbol() {
        PriceHistoryService service = new PriceHistoryService(candleProvider, priceHistoryRepository);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 2);
        when(candleProvider.getDailyCloses("AAPL", from, to)).thenReturn(List.of(
                new DailyClose(LocalDate.of(2026, 1, 1), new BigDecimal("150.00")),
                new DailyClose(LocalDate.of(2026, 1, 2), new BigDecimal("151.50"))));

        service.refresh("AAPL", from, to);

        verify(priceHistoryRepository).deleteBySymbol("AAPL");
        verify(priceHistoryRepository).saveAll(argThat((Iterable<PriceHistory> rows) -> {
            List<PriceHistory> list = new java.util.ArrayList<>();
            rows.forEach(list::add);
            return list.size() == 2 && list.get(0).getSymbol().equals("AAPL");
        }));
    }

    @Test
    void refreshSkipsSaveWhenProviderReturnsNoData() {
        PriceHistoryService service = new PriceHistoryService(candleProvider, priceHistoryRepository);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 2);
        when(candleProvider.getDailyCloses("BADSYM", from, to)).thenReturn(List.of());

        service.refresh("BADSYM", from, to);

        verify(priceHistoryRepository, never()).deleteBySymbol(any());
        verify(priceHistoryRepository, never()).saveAll(any());
    }

    @Test
    void getHistoryDelegatesToRepository() {
        PriceHistoryService service = new PriceHistoryService(candleProvider, priceHistoryRepository);
        PriceHistory row = new PriceHistory();
        row.setSymbol("AAPL");
        when(priceHistoryRepository.findBySymbolOrderByPriceDateAsc("AAPL")).thenReturn(List.of(row));

        List<PriceHistory> result = service.getHistory("AAPL");

        assertThat(result).containsExactly(row);
    }
}
