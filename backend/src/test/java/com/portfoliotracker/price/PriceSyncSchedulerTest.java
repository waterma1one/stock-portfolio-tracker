package com.portfoliotracker.price;

import com.portfoliotracker.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceSyncSchedulerTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    PriceService priceService;

    @Test
    void pollAllFetchesPriceForEachDistinctSymbol() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of("AAPL", "MSFT"));

        PriceSyncScheduler scheduler = new PriceSyncScheduler(transactionRepository, priceService);
        scheduler.pollAll();

        verify(priceService).fetchAndStore("AAPL");
        verify(priceService).fetchAndStore("MSFT");
    }

    @Test
    void pollAllDoesNothingWhenNoTransactionsExist() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of());

        PriceSyncScheduler scheduler = new PriceSyncScheduler(transactionRepository, priceService);
        scheduler.pollAll();

        verifyNoInteractions(priceService);
    }
}
