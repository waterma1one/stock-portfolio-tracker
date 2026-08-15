package com.portfoliotracker.symbolprofile;

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
class SymbolProfileSyncSchedulerTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    SymbolProfileService symbolProfileService;

    @Test
    void sweepEnsuresCachedProfileForEachDistinctSymbol() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of("AAPL", "MSFT"));

        SymbolProfileSyncScheduler scheduler = new SymbolProfileSyncScheduler(transactionRepository, symbolProfileService);
        scheduler.sweep();

        verify(symbolProfileService).ensureCached("AAPL");
        verify(symbolProfileService).ensureCached("MSFT");
    }

    @Test
    void sweepDoesNothingWhenNoTransactionsExist() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of());

        SymbolProfileSyncScheduler scheduler = new SymbolProfileSyncScheduler(transactionRepository, symbolProfileService);
        scheduler.sweep();

        verifyNoInteractions(symbolProfileService);
    }
}
