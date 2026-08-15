package com.portfoliotracker.symbolprofile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class SymbolProfileRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    SymbolProfileRepository symbolProfileRepository;

    @Test
    void findBySymbolReturnsSavedProfile() {
        SymbolProfile profile = new SymbolProfile();
        profile.setSymbol("AAPL");
        profile.setSector("Technology");
        profile.setName("Apple Inc");
        profile.setFetchedAt(Instant.parse("2026-01-01T00:00:00Z"));
        symbolProfileRepository.save(profile);

        Optional<SymbolProfile> found = symbolProfileRepository.findBySymbol("AAPL");

        assertThat(found).isPresent();
        assertThat(found.get().getSector()).isEqualTo("Technology");
    }

    @Test
    void findBySymbolReturnsEmptyWhenNoProfileExists() {
        Optional<SymbolProfile> found = symbolProfileRepository.findBySymbol("MSFT");

        assertThat(found).isEmpty();
    }

    @Test
    void sectorMayBeNullWhenFinnhubHasNoClassification() {
        SymbolProfile profile = new SymbolProfile();
        profile.setSymbol("ZZZZ");
        profile.setSector(null);
        profile.setName("Unknown Corp");
        profile.setFetchedAt(Instant.now());

        SymbolProfile saved = symbolProfileRepository.save(profile);

        assertThat(saved.getSector()).isNull();
    }

    @Test
    void savingProfileNormalizesSymbolEvenWhenServiceIsBypassed() {
        SymbolProfile profile = new SymbolProfile();
        profile.setSymbol("  aapl  ");
        profile.setSector("Technology");
        profile.setName("Apple Inc");
        profile.setFetchedAt(Instant.now());

        SymbolProfile saved = symbolProfileRepository.save(profile);

        assertThat(saved.getSymbol()).isEqualTo("AAPL");
    }
}
