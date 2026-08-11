package com.portfoliotracker.pricehistory;

import com.portfoliotracker.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceHistorySyncSchedulerTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    PriceHistoryService priceHistoryService;

    @Test
    void refreshAllCoversEveryDistinctSymbolPlusSpyBenchmark() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of("AAPL", "MSFT"));

        PriceHistorySyncScheduler scheduler = new PriceHistorySyncScheduler(transactionRepository, priceHistoryService, 365);
        scheduler.refreshAll();

        ArgumentCaptor<String> symbolCaptor = ArgumentCaptor.forClass(String.class);
        verify(priceHistoryService, org.mockito.Mockito.times(3))
                .refresh(symbolCaptor.capture(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(symbolCaptor.getAllValues()).containsExactlyInAnyOrder("AAPL", "MSFT", "SPY");
    }

    @Test
    void refreshAllStillRefreshesSpyWhenNoTransactionsExist() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of());

        PriceHistorySyncScheduler scheduler = new PriceHistorySyncScheduler(transactionRepository, priceHistoryService, 365);
        scheduler.refreshAll();

        verify(priceHistoryService).refresh(org.mockito.ArgumentMatchers.eq("SPY"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshAllUsesLookbackWindowEndingToday() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of());
        LocalDate today = LocalDate.now();

        PriceHistorySyncScheduler scheduler = new PriceHistorySyncScheduler(transactionRepository, priceHistoryService, 365);
        scheduler.refreshAll();

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(priceHistoryService).refresh(org.mockito.ArgumentMatchers.eq("SPY"), fromCaptor.capture(), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(today);
        assertThat(fromCaptor.getValue()).isEqualTo(today.minusDays(365));
    }
}
