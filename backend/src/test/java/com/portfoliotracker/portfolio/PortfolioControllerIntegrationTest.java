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
}
