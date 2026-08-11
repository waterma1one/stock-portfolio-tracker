package com.portfoliotracker.analytics;

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

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AnalyticsControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    com.portfoliotracker.price.PriceSnapshotRepository priceSnapshotRepository;

    @Autowired
    com.portfoliotracker.symbolprofile.SymbolProfileRepository symbolProfileRepository;

    @Test
    void allocationGroupsHoldingsBySectorWithPricesAndProfilesCached() {
        ResponseEntity<PortfolioResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios", new CreatePortfolioRequest("Allocation Test"), PortfolioResponse.class);
        Long portfolioId = createResp.getBody().id();

        restTemplate.postForEntity(
                "/api/portfolios/" + portfolioId + "/transactions",
                new com.portfoliotracker.transaction.dto.CreateTransactionRequest(
                        "AAPL", com.portfoliotracker.transaction.TransactionType.BUY,
                        new BigDecimal("10"), new BigDecimal("100.00"), Instant.now()),
                com.portfoliotracker.transaction.dto.TransactionResponse.class);

        com.portfoliotracker.price.PriceSnapshot snapshot = new com.portfoliotracker.price.PriceSnapshot();
        snapshot.setSymbol("AAPL");
        snapshot.setPrice(new BigDecimal("150.00"));
        snapshot.setFetchedAt(Instant.now());
        priceSnapshotRepository.save(snapshot);

        com.portfoliotracker.symbolprofile.SymbolProfile profile = new com.portfoliotracker.symbolprofile.SymbolProfile();
        profile.setSymbol("AAPL");
        profile.setSector("Technology");
        profile.setName("Apple Inc");
        profile.setFetchedAt(Instant.now());
        symbolProfileRepository.save(profile);

        ResponseEntity<com.portfoliotracker.analytics.dto.AllocationResponse> resp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolioId + "/analytics/allocation",
                com.portfoliotracker.analytics.dto.AllocationResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().sectors()).hasSize(1);
        assertThat(resp.getBody().sectors().get(0).sector()).isEqualTo("Technology");
        assertThat(resp.getBody().sectors().get(0).percent()).isEqualByComparingTo("100.0000");
    }

    @Test
    void allocationForNonexistentPortfolioReturns404() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/api/portfolios/999999999/analytics/allocation", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
