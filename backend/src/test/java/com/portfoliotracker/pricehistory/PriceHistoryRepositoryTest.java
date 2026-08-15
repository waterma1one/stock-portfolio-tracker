package com.portfoliotracker.pricehistory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class PriceHistoryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PriceHistoryRepository priceHistoryRepository;

    @Test
    void findBySymbolOrderByPriceDateAscReturnsInAscendingOrder() {
        save("AAPL", LocalDate.of(2026, 1, 3), "150.00");
        save("AAPL", LocalDate.of(2026, 1, 1), "148.00");
        save("AAPL", LocalDate.of(2026, 1, 2), "149.00");

        List<PriceHistory> history = priceHistoryRepository.findBySymbolOrderByPriceDateAsc("AAPL");

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getPriceDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(history.get(2).getPriceDate()).isEqualTo(LocalDate.of(2026, 1, 3));
    }

    @Test
    void deleteBySymbolRemovesAllRowsForThatSymbolOnly() {
        save("AAPL", LocalDate.of(2026, 1, 1), "148.00");
        save("MSFT", LocalDate.of(2026, 1, 1), "300.00");

        priceHistoryRepository.deleteBySymbol("AAPL");

        assertThat(priceHistoryRepository.findBySymbolOrderByPriceDateAsc("AAPL")).isEmpty();
        assertThat(priceHistoryRepository.findBySymbolOrderByPriceDateAsc("MSFT")).hasSize(1);
    }

    private void save(String symbol, LocalDate date, String close) {
        PriceHistory row = new PriceHistory();
        row.setSymbol(symbol);
        row.setPriceDate(date);
        row.setClose(new BigDecimal(close));
        priceHistoryRepository.save(row);
    }
}
