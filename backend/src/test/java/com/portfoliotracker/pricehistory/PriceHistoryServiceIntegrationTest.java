package com.portfoliotracker.pricehistory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link PriceHistoryService#refresh} against a real database from a context that is
 * NOT wrapped in an ambient test transaction, which is how the scheduler actually calls it.
 * {@code PriceHistoryRepositoryTest} (@DataJpaTest) and {@code PriceHistoryServiceTest} (mocked
 * repository) both hide the fact that the delete + re-insert needs its own transaction.
 */
@SpringBootTest
@Testcontainers
class PriceHistoryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    CandleProvider candleProvider;

    @Autowired
    PriceHistoryService priceHistoryService;

    @Autowired
    PriceHistoryRepository priceHistoryRepository;

    @Test
    void refreshPersistsRowsWithoutAnAmbientTransaction() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 3);
        when(candleProvider.getDailyCloses("AAPL", from, to)).thenReturn(List.of(
                new DailyClose(LocalDate.of(2026, 1, 2), new BigDecimal("150.00")),
                new DailyClose(LocalDate.of(2026, 1, 3), new BigDecimal("151.50"))));

        priceHistoryService.refresh("AAPL", from, to);

        List<PriceHistory> stored = priceHistoryRepository.findBySymbolOrderByPriceDateAsc("AAPL");
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).getPriceDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(stored.get(0).getClose()).isEqualByComparingTo("150.00");
        assertThat(stored.get(1).getPriceDate()).isEqualTo(LocalDate.of(2026, 1, 3));
        assertThat(stored.get(1).getClose()).isEqualByComparingTo("151.50");
    }

    @Test
    void refreshReplacesPreviouslyStoredRowsForTheSameSymbol() {
        LocalDate day = LocalDate.of(2026, 2, 2);
        PriceHistory stale = new PriceHistory();
        stale.setSymbol("MSFT");
        stale.setPriceDate(day);
        stale.setClose(new BigDecimal("1.00"));
        priceHistoryRepository.save(stale);

        LocalDate from = LocalDate.of(2026, 2, 1);
        LocalDate to = LocalDate.of(2026, 2, 2);
        when(candleProvider.getDailyCloses("MSFT", from, to)).thenReturn(List.of(
                new DailyClose(day, new BigDecimal("400.00"))));

        // Without a transaction spanning delete + insert, the delete either blows up or no-ops
        // and the re-insert then violates the (symbol, price_date) unique constraint.
        priceHistoryService.refresh("MSFT", from, to);

        List<PriceHistory> stored = priceHistoryRepository.findBySymbolOrderByPriceDateAsc("MSFT");
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getClose()).isEqualByComparingTo("400.00");
    }
}
