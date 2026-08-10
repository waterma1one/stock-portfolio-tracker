package com.portfoliotracker.price;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    PriceProvider priceProvider;

    @Mock
    PriceSnapshotRepository priceSnapshotRepository;

    @Test
    void fetchAndStoreSavesSnapshotWhenPriceAvailable() {
        PriceService service = new PriceService(priceProvider, priceSnapshotRepository);
        when(priceProvider.getPrice("AAPL")).thenReturn(Optional.of(new BigDecimal("151.12")));

        service.fetchAndStore("AAPL");

        verify(priceSnapshotRepository).save(argThatMatchesAaplSnapshot());
    }

    @Test
    void fetchAndStoreSkipsSaveWhenPriceUnavailable() {
        PriceService service = new PriceService(priceProvider, priceSnapshotRepository);
        when(priceProvider.getPrice("BADSYM")).thenReturn(Optional.empty());

        service.fetchAndStore("BADSYM");

        verify(priceSnapshotRepository, never()).save(any());
    }

    @Test
    void getLatestDelegatesToRepository() {
        PriceService service = new PriceService(priceProvider, priceSnapshotRepository);
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setSymbol("AAPL");
        snapshot.setPrice(new BigDecimal("151.12"));
        when(priceSnapshotRepository.findTopBySymbolOrderByFetchedAtDesc("AAPL")).thenReturn(Optional.of(snapshot));

        Optional<PriceSnapshot> result = service.getLatest("AAPL");

        assertThat(result).contains(snapshot);
    }

    private PriceSnapshot argThatMatchesAaplSnapshot() {
        return org.mockito.ArgumentMatchers.argThat(snapshot ->
                snapshot.getSymbol().equals("AAPL")
                        && snapshot.getPrice().compareTo(new BigDecimal("151.12")) == 0
                        && snapshot.getFetchedAt() != null);
    }
}
