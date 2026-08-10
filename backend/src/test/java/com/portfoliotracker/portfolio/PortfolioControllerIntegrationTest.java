package com.portfoliotracker.portfolio;

import com.portfoliotracker.portfolio.dto.CreatePortfolioRequest;
import com.portfoliotracker.portfolio.dto.PortfolioResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PortfolioControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    com.portfoliotracker.price.PriceSnapshotRepository priceSnapshotRepository;

    @Test
    void createAndListPortfolio() {
        ResponseEntity<PortfolioResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios", new CreatePortfolioRequest("Retirement"), PortfolioResponse.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResp.getBody().name()).isEqualTo("Retirement");

        ResponseEntity<PortfolioResponse[]> listResp = restTemplate.getForEntity(
                "/api/portfolios", PortfolioResponse[].class);

        assertThat(listResp.getBody())
                .extracting(PortfolioResponse::name)
                .contains("Retirement");
    }

    @Test
    void holdingsReflectTransactionHistory() {
        ResponseEntity<PortfolioResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios", new CreatePortfolioRequest("Holdings Test"), PortfolioResponse.class);
        Long portfolioId = createResp.getBody().id();

        restTemplate.postForEntity(
                "/api/portfolios/" + portfolioId + "/transactions",
                new com.portfoliotracker.transaction.dto.CreateTransactionRequest(
                        "AAPL", com.portfoliotracker.transaction.TransactionType.BUY,
                        new java.math.BigDecimal("10"), new java.math.BigDecimal("100.00"), java.time.Instant.now()),
                com.portfoliotracker.transaction.dto.TransactionResponse.class);

        ResponseEntity<com.portfoliotracker.holding.dto.HoldingResponse[]> holdingsResp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolioId + "/holdings",
                com.portfoliotracker.holding.dto.HoldingResponse[].class);

        assertThat(holdingsResp.getBody()).hasSize(1);
        assertThat(holdingsResp.getBody()[0].symbol()).isEqualTo("AAPL");
    }

    @Test
    void deletingTransactionRecomputesHoldings() {
        ResponseEntity<PortfolioResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios", new CreatePortfolioRequest("Delete Recompute Test"), PortfolioResponse.class);
        Long portfolioId = createResp.getBody().id();

        ResponseEntity<com.portfoliotracker.transaction.dto.TransactionResponse> txResp = restTemplate.postForEntity(
                "/api/portfolios/" + portfolioId + "/transactions",
                new com.portfoliotracker.transaction.dto.CreateTransactionRequest(
                        "AAPL", com.portfoliotracker.transaction.TransactionType.BUY,
                        new java.math.BigDecimal("10"), new java.math.BigDecimal("100.00"), java.time.Instant.now()),
                com.portfoliotracker.transaction.dto.TransactionResponse.class);
        Long transactionId = txResp.getBody().id();

        ResponseEntity<com.portfoliotracker.holding.dto.HoldingResponse[]> holdingsResp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolioId + "/holdings",
                com.portfoliotracker.holding.dto.HoldingResponse[].class);
        assertThat(holdingsResp.getBody()).hasSize(1);
        assertThat(holdingsResp.getBody()[0].symbol()).isEqualTo("AAPL");
        assertThat(holdingsResp.getBody()[0].quantity()).isEqualByComparingTo(new java.math.BigDecimal("10"));

        restTemplate.delete("/api/transactions/" + transactionId);

        ResponseEntity<com.portfoliotracker.holding.dto.HoldingResponse[]> afterDeleteResp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolioId + "/holdings",
                com.portfoliotracker.holding.dto.HoldingResponse[].class);
        assertThat(afterDeleteResp.getBody()).isEmpty();
    }

    @Test
    void holdingsForNonexistentPortfolioReturns404() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/api/portfolios/999999999/holdings", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void holdingsIncludeLiveValuationWhenPriceSnapshotExists() {
        ResponseEntity<PortfolioResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios", new CreatePortfolioRequest("Valuation Test"), PortfolioResponse.class);
        Long portfolioId = createResp.getBody().id();

        restTemplate.postForEntity(
                "/api/portfolios/" + portfolioId + "/transactions",
                new com.portfoliotracker.transaction.dto.CreateTransactionRequest(
                        "AAPL", com.portfoliotracker.transaction.TransactionType.BUY,
                        new java.math.BigDecimal("10"), new java.math.BigDecimal("100.00"), java.time.Instant.now()),
                com.portfoliotracker.transaction.dto.TransactionResponse.class);

        com.portfoliotracker.price.PriceSnapshot snapshot = new com.portfoliotracker.price.PriceSnapshot();
        snapshot.setSymbol("AAPL");
        snapshot.setPrice(new java.math.BigDecimal("150.00"));
        snapshot.setFetchedAt(java.time.Instant.now());
        priceSnapshotRepository.save(snapshot);

        ResponseEntity<com.portfoliotracker.holding.dto.HoldingResponse[]> holdingsResp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolioId + "/holdings",
                com.portfoliotracker.holding.dto.HoldingResponse[].class);

        assertThat(holdingsResp.getBody()).hasSize(1);
        com.portfoliotracker.holding.dto.HoldingResponse holding = holdingsResp.getBody()[0];
        assertThat(holding.currentPrice()).isEqualByComparingTo("150.00");
        assertThat(holding.marketValue()).isEqualByComparingTo("1500.00");
        assertThat(holding.unrealizedPnl()).isEqualByComparingTo("500.00");
    }

    @Test
    void holdingsShowNullValuationWhenNoPriceSnapshotExists() {
        ResponseEntity<PortfolioResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios", new CreatePortfolioRequest("No Price Test"), PortfolioResponse.class);
        Long portfolioId = createResp.getBody().id();

        restTemplate.postForEntity(
                "/api/portfolios/" + portfolioId + "/transactions",
                new com.portfoliotracker.transaction.dto.CreateTransactionRequest(
                        "ZZZZ", com.portfoliotracker.transaction.TransactionType.BUY,
                        new java.math.BigDecimal("5"), new java.math.BigDecimal("10.00"), java.time.Instant.now()),
                com.portfoliotracker.transaction.dto.TransactionResponse.class);

        ResponseEntity<com.portfoliotracker.holding.dto.HoldingResponse[]> holdingsResp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolioId + "/holdings",
                com.portfoliotracker.holding.dto.HoldingResponse[].class);

        assertThat(holdingsResp.getBody()).hasSize(1);
        com.portfoliotracker.holding.dto.HoldingResponse holding = holdingsResp.getBody()[0];
        assertThat(holding.currentPrice()).isNull();
        assertThat(holding.marketValue()).isNull();
        assertThat(holding.unrealizedPnl()).isNull();
    }
}
