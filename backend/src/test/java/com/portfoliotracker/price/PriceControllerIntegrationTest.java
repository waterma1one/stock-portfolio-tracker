package com.portfoliotracker.price;

import com.portfoliotracker.price.dto.PriceResponse;
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
class PriceControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PriceSnapshotRepository priceSnapshotRepository;

    @Test
    void returnsLatestCachedPrice() {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setSymbol("AAPL");
        snapshot.setPrice(new BigDecimal("151.12"));
        snapshot.setFetchedAt(Instant.now());
        priceSnapshotRepository.save(snapshot);

        ResponseEntity<PriceResponse> resp = restTemplate.getForEntity("/api/prices/AAPL", PriceResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().symbol()).isEqualTo("AAPL");
        assertThat(resp.getBody().price()).isEqualByComparingTo("151.12");
    }

    @Test
    void returns404WhenNoPriceSnapshotExists() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/api/prices/NOPE", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
