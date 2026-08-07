package com.portfoliotracker.transaction;

import com.portfoliotracker.portfolio.Portfolio;
import com.portfoliotracker.portfolio.PortfolioRepository;
import com.portfoliotracker.transaction.dto.CreateTransactionRequest;
import com.portfoliotracker.transaction.dto.TransactionResponse;
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
class TransactionControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PortfolioRepository portfolioRepository;

    @Test
    void createListAndDeleteTransaction() {
        Portfolio portfolio = new Portfolio();
        portfolio.setName("Growth");
        portfolio.setCreatedAt(Instant.now());
        portfolio = portfolioRepository.save(portfolio);

        CreateTransactionRequest request = new CreateTransactionRequest(
                "AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("150.00"), Instant.now());

        ResponseEntity<TransactionResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios/" + portfolio.getId() + "/transactions", request, TransactionResponse.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long txId = createResp.getBody().id();

        ResponseEntity<TransactionResponse[]> listResp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolio.getId() + "/transactions", TransactionResponse[].class);
        assertThat(listResp.getBody()).hasSize(1);

        restTemplate.delete("/api/transactions/" + txId);

        ResponseEntity<TransactionResponse[]> afterDelete = restTemplate.getForEntity(
                "/api/portfolios/" + portfolio.getId() + "/transactions", TransactionResponse[].class);
        assertThat(afterDelete.getBody()).isEmpty();
    }

    @Test
    void createTransactionWithNonexistentPortfolioReturns404() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("150.00"), Instant.now());

        ResponseEntity<TransactionResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios/" + Long.MAX_VALUE + "/transactions", request, TransactionResponse.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
