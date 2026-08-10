package com.portfoliotracker.price;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class PriceSnapshotRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PriceSnapshotRepository priceSnapshotRepository;

    @Test
    void findTopBySymbolOrderByFetchedAtDescReturnsMostRecent() {
        PriceSnapshot older = new PriceSnapshot();
        older.setSymbol("AAPL");
        older.setPrice(new BigDecimal("140.00"));
        older.setFetchedAt(Instant.parse("2026-01-01T00:00:00Z"));
        priceSnapshotRepository.save(older);

        PriceSnapshot newer = new PriceSnapshot();
        newer.setSymbol("AAPL");
        newer.setPrice(new BigDecimal("151.12"));
        newer.setFetchedAt(Instant.parse("2026-02-01T00:00:00Z"));
        priceSnapshotRepository.save(newer);

        Optional<PriceSnapshot> latest = priceSnapshotRepository.findTopBySymbolOrderByFetchedAtDesc("AAPL");

        assertThat(latest).isPresent();
        assertThat(latest.get().getPrice()).isEqualByComparingTo("151.12");
    }

    @Test
    void findTopBySymbolReturnsEmptyWhenNoSnapshotsExist() {
        Optional<PriceSnapshot> latest = priceSnapshotRepository.findTopBySymbolOrderByFetchedAtDesc("MSFT");

        assertThat(latest).isEmpty();
    }
}
