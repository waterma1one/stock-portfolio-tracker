package com.portfoliotracker.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class TransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    TransactionRepository transactionRepository;

    /**
     * DTO-level validation already rejects symbols this long via {@code @Pattern}, but the
     * column itself must independently reject oversized values too -- any write path that
     * bypasses the DTO (batch import, admin tooling, a future non-REST entry point) must not
     * be able to blow past a bounded varchar and surface a raw DB error.
     */
    @Test
    void savingTransactionWithOversizedSymbolIsRejectedAtTheColumnLevel() {
        Transaction transaction = Transaction.of(
                "A".repeat(21), TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("100.00"), Instant.now());
        transaction.setPortfolioId(1L);

        assertThatThrownBy(() -> {
            transactionRepository.saveAndFlush(transaction);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingTransactionWithSymbolAtMaxLengthSucceeds() {
        Transaction transaction = Transaction.of(
                "A".repeat(20), TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("100.00"), Instant.now());
        transaction.setPortfolioId(1L);

        Transaction saved = transactionRepository.saveAndFlush(transaction);

        org.assertj.core.api.Assertions.assertThat(saved.getId()).isNotNull();
    }

    /**
     * DTO-level normalization (CreateTransactionRequest's compact constructor) only protects
     * requests that go through the REST API. Any direct save -- exactly what this test does,
     * bypassing the DTO entirely -- must still end up with a canonical symbol, or the
     * case-fragmentation bug this normalization was meant to fix can resurface via any future
     * non-REST write path (batch import, admin tooling, a migration script).
     */
    @Test
    void savingTransactionNormalizesSymbolEvenWhenDtoIsBypassed() {
        Transaction transaction = Transaction.of(
                "  aapl  ", TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("100.00"), Instant.now());
        transaction.setPortfolioId(1L);

        Transaction saved = transactionRepository.saveAndFlush(transaction);

        org.assertj.core.api.Assertions.assertThat(saved.getSymbol()).isEqualTo("AAPL");
    }
}
