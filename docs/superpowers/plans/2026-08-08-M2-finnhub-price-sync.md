# M2: Finnhub Price Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scheduled Finnhub price-poll job caches latest prices in a `PriceSnapshot` table; holdings endpoint and dashboard UI surface live market value and unrealized P&L per symbol.

**Architecture:** A `PriceProvider` interface abstracts Finnhub so tests never hit the real API; `FinnhubPriceProvider` implements it via Spring's `RestClient`. A `@Scheduled` job polls every distinct symbol across all portfolios' transactions and writes `PriceSnapshot` rows. The existing holdings endpoint is enriched (not replaced) with `currentPrice`/`marketValue`/`unrealizedPnl`, computed from the latest cached snapshot — `HoldingCalculator` itself stays a pure, price-agnostic function.

**Tech Stack:** Same as M1 (Spring Boot 3.3, Java 21, Postgres 16, Testcontainers, JUnit5+Mockito+AssertJ; React 18, Vite, TypeScript, Tailwind, Vitest) — no new dependencies. `RestClient` and `MockRestServiceServer` are already on the classpath via `spring-boot-starter-web`/`spring-boot-starter-test`.

## Global Constraints

- USD only, US-listed equities only (v1) — no currency field
- No auth in M2 — all endpoints open (M4 adds JWT + user scoping)
- Finnhub free tier: 60 calls/min — poll interval and symbol-dedup must respect this
- `PriceProvider` interface abstracts Finnhub — real HTTP calls never happen in tests
- Local Docker-compose only — no cloud deploy config
- No `Co-Authored-By: Claude` trailer on any commit — plain commits, author only
- Holdings computation (`HoldingCalculator`) stays pure/price-agnostic — price enrichment happens only in the response-mapping layer
- Before starting each task, check active model against the model table in the spec's "Claude Code Dev Workflow" section; if mismatched, tell the user to switch before proceeding (suggested model noted per task below)

---

### Task 1: PriceSnapshot entity and repository

**Suggested model:** Haiku 4.5 (mechanical scaffold, mirrors M1's entity pattern)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/price/PriceSnapshot.java`
- Create: `backend/src/main/java/com/portfoliotracker/price/PriceSnapshotRepository.java`
- Test: `backend/src/test/java/com/portfoliotracker/price/PriceSnapshotRepositoryTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks (first M2 entity).
- Produces: `PriceSnapshot` entity (`id: Long, symbol: String, price: BigDecimal, fetchedAt: Instant`), `PriceSnapshotRepository.findTopBySymbolOrderByFetchedAtDesc(String): Optional<PriceSnapshot>`.

- [ ] **Step 1: Write failing repository test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PriceSnapshotRepositoryTest`
Expected: FAIL — compile error, `PriceSnapshot`/`PriceSnapshotRepository` don't exist yet.

- [ ] **Step 3: Write `PriceSnapshot.java`**

```java
package com.portfoliotracker.price;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
```

- [ ] **Step 4: Write `PriceSnapshotRepository.java`**

```java
package com.portfoliotracker.price;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {
    Optional<PriceSnapshot> findTopBySymbolOrderByFetchedAtDesc(String symbol);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PriceSnapshotRepositoryTest`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/price backend/src/test/java/com/portfoliotracker/price
git commit -m "Add PriceSnapshot entity and repository"
```

---

### Task 2: PriceProvider interface and Finnhub implementation

**Suggested model:** Sonnet 5 (external API integration, error-handling judgment)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/price/PriceProvider.java`
- Create: `backend/src/main/java/com/portfoliotracker/price/FinnhubPriceProvider.java`
- Create: `backend/src/main/java/com/portfoliotracker/price/FinnhubQuoteResponse.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/portfoliotracker/price/FinnhubPriceProviderTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces: `PriceProvider.getPrice(String symbol): Optional<BigDecimal>` — returns empty on any failure (bad symbol, network error, non-2xx). This is the seam Task 3's `PriceService` depends on and the seam tests mock instead of hitting real Finnhub.

- [ ] **Step 1: Write failing test using `MockRestServiceServer`**

```java
package com.portfoliotracker.price;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FinnhubPriceProviderTest {

    @Test
    void parsesCurrentPriceFromQuoteResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/quote?symbol=AAPL&token=test-key"))
                .andRespond(withSuccess(
                        "{\"c\":151.12,\"d\":1.2,\"dp\":0.8,\"h\":152.0,\"l\":149.5,\"o\":150.0,\"pc\":149.9,\"t\":1700000000}",
                        MediaType.APPLICATION_JSON));

        FinnhubPriceProvider provider = new FinnhubPriceProvider(builder, "https://finnhub.io/api/v1", "test-key");

        Optional<BigDecimal> price = provider.getPrice("AAPL");

        assertThat(price).isPresent();
        assertThat(price.get()).isEqualByComparingTo("151.12");
    }

    @Test
    void returnsEmptyWhenApiCallFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/quote?symbol=BADSYM&token=test-key"))
                .andRespond(withServerError());

        FinnhubPriceProvider provider = new FinnhubPriceProvider(builder, "https://finnhub.io/api/v1", "test-key");

        Optional<BigDecimal> price = provider.getPrice("BADSYM");

        assertThat(price).isEmpty();
    }

    @Test
    void returnsEmptyWhenQuoteHasZeroPrice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/quote?symbol=UNKNOWN&token=test-key"))
                .andRespond(withSuccess("{\"c\":0,\"d\":0,\"dp\":0,\"h\":0,\"l\":0,\"o\":0,\"pc\":0,\"t\":0}",
                        MediaType.APPLICATION_JSON));

        FinnhubPriceProvider provider = new FinnhubPriceProvider(builder, "https://finnhub.io/api/v1", "test-key");

        Optional<BigDecimal> price = provider.getPrice("UNKNOWN");

        assertThat(price).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=FinnhubPriceProviderTest`
Expected: FAIL — compile error, classes don't exist yet.

- [ ] **Step 3: Write `PriceProvider.java`**

```java
package com.portfoliotracker.price;

import java.math.BigDecimal;
import java.util.Optional;

public interface PriceProvider {
    Optional<BigDecimal> getPrice(String symbol);
}
```

- [ ] **Step 4: Write `FinnhubQuoteResponse.java`**

```java
package com.portfoliotracker.price;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubQuoteResponse(BigDecimal c) {
}
```

- [ ] **Step 5: Write `FinnhubPriceProvider.java`**

```java
package com.portfoliotracker.price;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class FinnhubPriceProvider implements PriceProvider {

    private final RestClient restClient;
    private final String apiKey;

    public FinnhubPriceProvider(RestClient.Builder restClientBuilder,
                                 @Value("${finnhub.base-url}") String baseUrl,
                                 @Value("${finnhub.api-key}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public Optional<BigDecimal> getPrice(String symbol) {
        try {
            FinnhubQuoteResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/quote")
                            .queryParam("symbol", symbol)
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .body(FinnhubQuoteResponse.class);

            if (response == null || response.c() == null || response.c().compareTo(BigDecimal.ZERO) == 0) {
                return Optional.empty();
            }
            return Optional.of(response.c());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 6: Add Finnhub config to `application.yml`**

Add at the end of `backend/src/main/resources/application.yml`:

```yaml

finnhub:
  base-url: https://finnhub.io/api/v1
  api-key: ${FINNHUB_API_KEY:demo}
  poll-interval-ms: 60000
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=FinnhubPriceProviderTest`
Expected: PASS (3 tests)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/price backend/src/main/resources/application.yml backend/src/test/java/com/portfoliotracker/price
git commit -m "Add PriceProvider interface and Finnhub REST client implementation"
```

---

### Task 3: PriceService — fetch-and-cache, read-latest

**Suggested model:** Haiku 4.5 (thin service, straightforward wiring over Tasks 1-2)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/price/PriceService.java`
- Test: `backend/src/test/java/com/portfoliotracker/price/PriceServiceTest.java`

**Interfaces:**
- Consumes: `PriceProvider.getPrice(String): Optional<BigDecimal>` (Task 2), `PriceSnapshotRepository.save`/`findTopBySymbolOrderByFetchedAtDesc` (Task 1).
- Produces: `PriceService.fetchAndStore(String symbol): void`, `PriceService.getLatest(String symbol): Optional<PriceSnapshot>` — consumed by Task 4's scheduler and Task 5's controller.

- [ ] **Step 1: Write failing unit tests**

```java
package com.portfoliotracker.price;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    PriceProvider priceProvider;

    @Mock
    PriceSnapshotRepository priceSnapshotRepository;

    @Test
    void fetchAndStoreSavesSnapshotWhenPriceAvailable() {
        PriceService service = new PriceService(priceProvider, priceSnapshotRepository);
        when(priceProvider.getPrice("AAPL")).thenReturn(Optional.of(new BigDecimal("151.12")));

        service.fetchAndStore("AAPL");

        verify(priceSnapshotRepository).save(argThatMatchesAaplSnapshot());
    }

    @Test
    void fetchAndStoreSkipsSaveWhenPriceUnavailable() {
        PriceService service = new PriceService(priceProvider, priceSnapshotRepository);
        when(priceProvider.getPrice("BADSYM")).thenReturn(Optional.empty());

        service.fetchAndStore("BADSYM");

        verify(priceSnapshotRepository, never()).save(any());
    }

    @Test
    void getLatestDelegatesToRepository() {
        PriceService service = new PriceService(priceProvider, priceSnapshotRepository);
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setSymbol("AAPL");
        snapshot.setPrice(new BigDecimal("151.12"));
        when(priceSnapshotRepository.findTopBySymbolOrderByFetchedAtDesc("AAPL")).thenReturn(Optional.of(snapshot));

        Optional<PriceSnapshot> result = service.getLatest("AAPL");

        assertThat(result).contains(snapshot);
    }

    private PriceSnapshot argThatMatchesAaplSnapshot() {
        return org.mockito.ArgumentMatchers.argThat(snapshot ->
                snapshot.getSymbol().equals("AAPL")
                        && snapshot.getPrice().compareTo(new BigDecimal("151.12")) == 0
                        && snapshot.getFetchedAt() != null);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest=PriceServiceTest`
Expected: FAIL — `PriceService` doesn't exist yet.

- [ ] **Step 3: Write `PriceService.java`**

```java
package com.portfoliotracker.price;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class PriceService {

    private final PriceProvider priceProvider;
    private final PriceSnapshotRepository priceSnapshotRepository;

    public PriceService(PriceProvider priceProvider, PriceSnapshotRepository priceSnapshotRepository) {
        this.priceProvider = priceProvider;
        this.priceSnapshotRepository = priceSnapshotRepository;
    }

    public void fetchAndStore(String symbol) {
        priceProvider.getPrice(symbol).ifPresent(price -> {
            PriceSnapshot snapshot = new PriceSnapshot();
            snapshot.setSymbol(symbol);
            snapshot.setPrice(price);
            snapshot.setFetchedAt(Instant.now());
            priceSnapshotRepository.save(snapshot);
        });
    }

    public Optional<PriceSnapshot> getLatest(String symbol) {
        return priceSnapshotRepository.findTopBySymbolOrderByFetchedAtDesc(symbol);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=PriceServiceTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/price/PriceService.java backend/src/test/java/com/portfoliotracker/price/PriceServiceTest.java
git commit -m "Add PriceService: fetch-and-cache and read-latest over PriceProvider"
```

---

### Task 4: Distinct-symbol query and scheduled poll job

**Suggested model:** Sonnet 5 (scheduled-job correctness, per spec's model table)

**Files:**
- Modify: `backend/src/main/java/com/portfoliotracker/transaction/TransactionRepository.java`
- Create: `backend/src/main/java/com/portfoliotracker/price/PriceSyncScheduler.java`
- Modify: `backend/src/main/java/com/portfoliotracker/PortfolioTrackerApplication.java` (add `@EnableScheduling`)
- Create: `backend/src/test/resources/application.yml` (disable scheduling during tests)
- Test: `backend/src/test/java/com/portfoliotracker/price/PriceSyncSchedulerTest.java`

**Interfaces:**
- Consumes: `TransactionRepository.findDistinctSymbols(): List<String>` (added this task), `PriceService.fetchAndStore(String)` (Task 3).
- Produces: `PriceSyncScheduler.pollAll(): void`, invoked automatically via `@Scheduled(fixedRateString = "${finnhub.poll-interval-ms}")`.

- [ ] **Step 1: Write failing unit test for the scheduler**

```java
package com.portfoliotracker.price;

import com.portfoliotracker.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceSyncSchedulerTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    PriceService priceService;

    @Test
    void pollAllFetchesPriceForEachDistinctSymbol() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of("AAPL", "MSFT"));

        PriceSyncScheduler scheduler = new PriceSyncScheduler(transactionRepository, priceService);
        scheduler.pollAll();

        verify(priceService).fetchAndStore("AAPL");
        verify(priceService).fetchAndStore("MSFT");
    }

    @Test
    void pollAllDoesNothingWhenNoTransactionsExist() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of());

        PriceSyncScheduler scheduler = new PriceSyncScheduler(transactionRepository, priceService);
        scheduler.pollAll();

        verifyNoInteractions(priceService);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PriceSyncSchedulerTest`
Expected: FAIL — `PriceSyncScheduler`/`findDistinctSymbols` don't exist yet.

- [ ] **Step 3: Modify `TransactionRepository.java`** — add distinct-symbol query

```java
package com.portfoliotracker.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByPortfolioIdOrderByExecutedAtAsc(Long portfolioId);

    @Query("SELECT DISTINCT t.symbol FROM Transaction t")
    List<String> findDistinctSymbols();
}
```

- [ ] **Step 4: Write `PriceSyncScheduler.java`**

```java
package com.portfoliotracker.price;

import com.portfoliotracker.transaction.TransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "finnhub", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class PriceSyncScheduler {

    private final TransactionRepository transactionRepository;
    private final PriceService priceService;

    public PriceSyncScheduler(TransactionRepository transactionRepository, PriceService priceService) {
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
    }

    @Scheduled(fixedRateString = "${finnhub.poll-interval-ms}")
    public void pollAll() {
        for (String symbol : transactionRepository.findDistinctSymbols()) {
            priceService.fetchAndStore(symbol);
        }
    }
}
```

- [ ] **Step 5: Add `@EnableScheduling` to `PortfolioTrackerApplication.java`**

```java
package com.portfoliotracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PortfolioTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortfolioTrackerApplication.class, args);
    }
}
```

- [ ] **Step 6: Write `backend/src/test/resources/application.yml`** — keeps `@SpringBootTest` runs from firing real scheduled polls against Finnhub

```yaml
finnhub:
  scheduling-enabled: false
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PriceSyncSchedulerTest`
Expected: PASS (2 tests)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/transaction/TransactionRepository.java backend/src/main/java/com/portfoliotracker/price/PriceSyncScheduler.java backend/src/main/java/com/portfoliotracker/PortfolioTrackerApplication.java backend/src/test/resources/application.yml backend/src/test/java/com/portfoliotracker/price/PriceSyncSchedulerTest.java
git commit -m "Add scheduled Finnhub poll job over all distinct transaction symbols"
```

---

### Task 5: GET /api/prices/{symbol} endpoint

**Suggested model:** Haiku 4.5 (wiring only — logic already tested in Task 3)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/price/dto/PriceResponse.java`
- Create: `backend/src/main/java/com/portfoliotracker/price/PriceController.java`
- Test: `backend/src/test/java/com/portfoliotracker/price/PriceControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `PriceService.getLatest(String): Optional<PriceSnapshot>` (Task 3).
- Produces: `GET /api/prices/{symbol}` → `PriceResponse` (200) or 404 if no snapshot cached yet.

- [ ] **Step 1: Write failing integration test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PriceControllerIntegrationTest`
Expected: FAIL — `PriceResponse`/`PriceController` don't exist, `/api/prices/{symbol}` 404s regardless of DB state.

- [ ] **Step 3: Write `dto/PriceResponse.java`**

```java
package com.portfoliotracker.price.dto;

import com.portfoliotracker.price.PriceSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceResponse(String symbol, BigDecimal price, Instant fetchedAt) {
    public static PriceResponse from(PriceSnapshot snapshot) {
        return new PriceResponse(snapshot.getSymbol(), snapshot.getPrice(), snapshot.getFetchedAt());
    }
}
```

- [ ] **Step 4: Write `PriceController.java`**

```java
package com.portfoliotracker.price;

import com.portfoliotracker.price.dto.PriceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/{symbol}")
    public PriceResponse getPrice(@PathVariable String symbol) {
        return priceService.getLatest(symbol)
                .map(PriceResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No price snapshot for symbol: " + symbol));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PriceControllerIntegrationTest`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/price/dto backend/src/main/java/com/portfoliotracker/price/PriceController.java backend/src/test/java/com/portfoliotracker/price/PriceControllerIntegrationTest.java
git commit -m "Add GET /api/prices/{symbol} endpoint for cached price lookups"
```

---

### Task 6: Enrich holdings endpoint with live valuation

**Suggested model:** Sonnet 5 (correctness-critical valuation math, per spec's model table)

**Files:**
- Modify: `backend/src/main/java/com/portfoliotracker/holding/dto/HoldingResponse.java`
- Modify: `backend/src/main/java/com/portfoliotracker/portfolio/PortfolioController.java`
- Test: `backend/src/test/java/com/portfoliotracker/portfolio/PortfolioControllerIntegrationTest.java` (extend existing file)

**Interfaces:**
- Consumes: `PriceService.getLatest(String): Optional<PriceSnapshot>` (Task 3), `HoldingCalculator.calculate` (M1, unchanged).
- Produces: `HoldingResponse` gains `currentPrice`, `marketValue`, `unrealizedPnl` (all `BigDecimal`, `null` when no price snapshot exists yet for that symbol).

- [ ] **Step 1: Add failing tests to `PortfolioControllerIntegrationTest.java`**

Add these two test methods inside the existing class, and add this field alongside the existing `@Autowired TestRestTemplate restTemplate;`:

```java
    @Autowired
    com.portfoliotracker.price.PriceSnapshotRepository priceSnapshotRepository;
```

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest=PortfolioControllerIntegrationTest`
Expected: FAIL — `HoldingResponse` has no `currentPrice`/`marketValue`/`unrealizedPnl` accessors yet.

- [ ] **Step 3: Rewrite `HoldingResponse.java`**

```java
package com.portfoliotracker.holding.dto;

import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.price.PriceSnapshot;

import java.math.BigDecimal;
import java.util.Optional;

public record HoldingResponse(String symbol, BigDecimal quantity, BigDecimal avgCostBasis,
                               BigDecimal currentPrice, BigDecimal marketValue, BigDecimal unrealizedPnl) {

    public static HoldingResponse from(Holding holding, Optional<PriceSnapshot> latestPrice) {
        BigDecimal currentPrice = latestPrice.map(PriceSnapshot::getPrice).orElse(null);
        BigDecimal marketValue = currentPrice != null ? currentPrice.multiply(holding.quantity()) : null;
        BigDecimal unrealizedPnl = currentPrice != null
                ? currentPrice.subtract(holding.avgCostBasis()).multiply(holding.quantity())
                : null;
        return new HoldingResponse(holding.symbol(), holding.quantity(), holding.avgCostBasis(),
                currentPrice, marketValue, unrealizedPnl);
    }
}
```

- [ ] **Step 4: Rewrite `PortfolioController.java`** — inject `PriceService`, pass latest price into `HoldingResponse.from`

```java
package com.portfoliotracker.portfolio;

import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.holding.HoldingCalculator;
import com.portfoliotracker.holding.dto.HoldingResponse;
import com.portfoliotracker.portfolio.dto.CreatePortfolioRequest;
import com.portfoliotracker.portfolio.dto.PortfolioResponse;
import com.portfoliotracker.price.PriceService;
import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;

    public PortfolioController(PortfolioService portfolioService,
                                PortfolioRepository portfolioRepository,
                                TransactionRepository transactionRepository,
                                PriceService priceService) {
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
    }

    @GetMapping
    public List<PortfolioResponse> list() {
        return portfolioService.findAll().stream().map(PortfolioResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse create(@Valid @RequestBody CreatePortfolioRequest request) {
        return PortfolioResponse.from(portfolioService.create(request.name()));
    }

    @GetMapping("/{portfolioId}/holdings")
    public List<HoldingResponse> holdings(@PathVariable Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
        List<Transaction> txs = transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        List<Holding> holdings = HoldingCalculator.calculate(txs);
        return holdings.stream()
                .map(h -> HoldingResponse.from(h, priceService.getLatest(h.symbol())))
                .toList();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=PortfolioControllerIntegrationTest`
Expected: PASS (all tests in the file, including M1's pre-existing ones)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/holding/dto/HoldingResponse.java backend/src/main/java/com/portfoliotracker/portfolio/PortfolioController.java backend/src/test/java/com/portfoliotracker/portfolio/PortfolioControllerIntegrationTest.java
git commit -m "Enrich holdings endpoint with live current price, market value, and unrealized P&L"
```

---

### Task 7: Frontend types and holdings table valuation columns

**Suggested model:** Haiku 4.5 (mechanical — mirrors backend DTO shape)

**Files:**
- Modify: `frontend/src/types/portfolio.ts`
- Modify: `frontend/src/components/HoldingsTable.tsx`

**Interfaces:**
- Consumes: enriched `Holding` shape from Task 6's `HoldingResponse` (already delivered by the existing `getHoldings` call — no API client change needed).
- Produces: `Holding` type carries `currentPrice`/`marketValue`/`unrealizedPnl: number | null`, consumed by Task 8's summary component.

- [ ] **Step 1: Modify `Holding` interface in `frontend/src/types/portfolio.ts`**

```ts
export interface Holding {
  symbol: string;
  quantity: number;
  avgCostBasis: number;
  currentPrice: number | null;
  marketValue: number | null;
  unrealizedPnl: number | null;
}
```

- [ ] **Step 2: Rewrite `frontend/src/components/HoldingsTable.tsx`**

```tsx
import type { Holding } from '../types/portfolio';

function formatCurrency(value: number | null): string {
  return value === null ? '—' : `$${value.toFixed(2)}`;
}

export function HoldingsTable({ holdings }: { holdings: Holding[] }) {
  if (holdings.length === 0) {
    return <p className="text-gray-500 text-sm">No holdings yet — add a transaction below.</p>;
  }

  return (
    <table className="w-full text-sm mb-6">
      <thead>
        <tr className="text-left border-b">
          <th className="py-1">Symbol</th>
          <th className="py-1">Quantity</th>
          <th className="py-1">Avg Cost Basis</th>
          <th className="py-1">Current Price</th>
          <th className="py-1">Market Value</th>
          <th className="py-1">Unrealized P&L</th>
        </tr>
      </thead>
      <tbody>
        {holdings.map((h) => (
          <tr key={h.symbol} className="border-b">
            <td className="py-1">{h.symbol}</td>
            <td className="py-1">{h.quantity}</td>
            <td className="py-1">${h.avgCostBasis.toFixed(2)}</td>
            <td className="py-1">{formatCurrency(h.currentPrice)}</td>
            <td className="py-1">{formatCurrency(h.marketValue)}</td>
            <td className={`py-1 ${h.unrealizedPnl !== null && h.unrealizedPnl < 0 ? 'text-red-600' : 'text-green-600'}`}>
              {h.unrealizedPnl === null ? '—' : `${h.unrealizedPnl >= 0 ? '+' : ''}$${h.unrealizedPnl.toFixed(2)}`}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/portfolio.ts frontend/src/components/HoldingsTable.tsx
git commit -m "Surface live current price, market value, and unrealized P&L in holdings table"
```

---

### Task 8: Portfolio summary totals — pure calc plus display component

**Suggested model:** Sonnet 5 (small but real aggregation logic, per spec's "React hooks/state" model tier)

**Files:**
- Create: `frontend/src/utils/portfolioSummary.ts`
- Create: `frontend/src/utils/portfolioSummary.test.ts`
- Create: `frontend/src/components/PortfolioSummary.tsx`
- Modify: `frontend/src/pages/Dashboard.tsx`

**Interfaces:**
- Consumes: `Holding[]` (Task 7).
- Produces: `computePortfolioSummary(holdings: Holding[]): { totalMarketValue: number | null; totalUnrealizedPnl: number | null }`, `PortfolioSummary` component mounted above `HoldingsTable` in `Dashboard`.

- [ ] **Step 1: Write failing test `frontend/src/utils/portfolioSummary.test.ts`**

```ts
import { describe, it, expect } from 'vitest';
import { computePortfolioSummary } from './portfolioSummary';
import type { Holding } from '../types/portfolio';

describe('computePortfolioSummary', () => {
  it('sums market value and unrealized P&L across holdings with prices', () => {
    const holdings: Holding[] = [
      { symbol: 'AAPL', quantity: 10, avgCostBasis: 100, currentPrice: 150, marketValue: 1500, unrealizedPnl: 500 },
      { symbol: 'MSFT', quantity: 5, avgCostBasis: 300, currentPrice: 280, marketValue: 1400, unrealizedPnl: -100 },
    ];

    const summary = computePortfolioSummary(holdings);

    expect(summary.totalMarketValue).toBe(2900);
    expect(summary.totalUnrealizedPnl).toBe(400);
  });

  it('excludes holdings with no price snapshot from the totals', () => {
    const holdings: Holding[] = [
      { symbol: 'AAPL', quantity: 10, avgCostBasis: 100, currentPrice: 150, marketValue: 1500, unrealizedPnl: 500 },
      { symbol: 'ZZZZ', quantity: 5, avgCostBasis: 10, currentPrice: null, marketValue: null, unrealizedPnl: null },
    ];

    const summary = computePortfolioSummary(holdings);

    expect(summary.totalMarketValue).toBe(1500);
    expect(summary.totalUnrealizedPnl).toBe(500);
  });

  it('returns null totals when no holdings have a price snapshot', () => {
    const holdings: Holding[] = [
      { symbol: 'ZZZZ', quantity: 5, avgCostBasis: 10, currentPrice: null, marketValue: null, unrealizedPnl: null },
    ];

    const summary = computePortfolioSummary(holdings);

    expect(summary.totalMarketValue).toBeNull();
    expect(summary.totalUnrealizedPnl).toBeNull();
  });

  it('returns null totals for an empty holdings list', () => {
    const summary = computePortfolioSummary([]);

    expect(summary.totalMarketValue).toBeNull();
    expect(summary.totalUnrealizedPnl).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/utils/portfolioSummary.test.ts`
Expected: FAIL — `./portfolioSummary` module doesn't exist.

- [ ] **Step 3: Write `frontend/src/utils/portfolioSummary.ts`**

```ts
import type { Holding } from '../types/portfolio';

export interface PortfolioSummaryTotals {
  totalMarketValue: number | null;
  totalUnrealizedPnl: number | null;
}

export function computePortfolioSummary(holdings: Holding[]): PortfolioSummaryTotals {
  const withPrices = holdings.filter((h) => h.marketValue !== null && h.unrealizedPnl !== null);

  if (withPrices.length === 0) {
    return { totalMarketValue: null, totalUnrealizedPnl: null };
  }

  const totalMarketValue = withPrices.reduce((sum, h) => sum + (h.marketValue ?? 0), 0);
  const totalUnrealizedPnl = withPrices.reduce((sum, h) => sum + (h.unrealizedPnl ?? 0), 0);

  return { totalMarketValue, totalUnrealizedPnl };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/utils/portfolioSummary.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 5: Write `frontend/src/components/PortfolioSummary.tsx`** (no test — thin presentational wrapper over the tested calc)

```tsx
import type { Holding } from '../types/portfolio';
import { computePortfolioSummary } from '../utils/portfolioSummary';

export function PortfolioSummary({ holdings }: { holdings: Holding[] }) {
  const { totalMarketValue, totalUnrealizedPnl } = computePortfolioSummary(holdings);

  if (totalMarketValue === null || totalUnrealizedPnl === null) {
    return null;
  }

  const pnlClass = totalUnrealizedPnl < 0 ? 'text-red-600' : 'text-green-600';

  return (
    <div className="flex gap-6 mb-4">
      <div>
        <div className="text-xs text-gray-500">Total Market Value</div>
        <div className="text-lg font-semibold">${totalMarketValue.toFixed(2)}</div>
      </div>
      <div>
        <div className="text-xs text-gray-500">Unrealized P&L</div>
        <div className={`text-lg font-semibold ${pnlClass}`}>
          {totalUnrealizedPnl >= 0 ? '+' : ''}${totalUnrealizedPnl.toFixed(2)}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 6: Modify `frontend/src/pages/Dashboard.tsx`** — mount `PortfolioSummary` above `HoldingsTable`

Add the import:

```tsx
import { PortfolioSummary } from '../components/PortfolioSummary';
```

Add `<PortfolioSummary holdings={holdings} />` immediately before the existing `<HoldingsTable holdings={holdings} />` line, inside the `selectedId !== null` branch.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/utils frontend/src/components/PortfolioSummary.tsx frontend/src/pages/Dashboard.tsx
git commit -m "Add portfolio summary totals (market value, unrealized P&L) to dashboard"
```

---

### Task 9: Wire FINNHUB_API_KEY through Docker-compose — smoke test

**Suggested model:** Sonnet 5 (cross-service debugging likely, per spec's model table)

**Files:**
- Modify: `docker-compose.yml`
- No new source files — verification task.

**Interfaces:**
- Consumes: everything from Tasks 1-8.
- Produces: verified running stack with live price polling (manual smoke test, no automated test — full e2e deferred per spec).

- [ ] **Step 1: Pass `FINNHUB_API_KEY` through to the backend container**

Add to the `backend` service's `environment` block in `docker-compose.yml` (alongside the existing `SPRING_DATASOURCE_*` entries):

```yaml
      FINNHUB_API_KEY: ${FINNHUB_API_KEY:-demo}
```

This reads `FINNHUB_API_KEY` from the host shell environment when running `docker compose up`; falls back to `demo` (a non-empty placeholder so Spring's property placeholder resolution doesn't fail) if unset. A real key is required to see genuine live prices — sign up free at finnhub.io.

- [ ] **Step 2: Bring up full stack with a real Finnhub key**

Run: `FINNHUB_API_KEY=<your-real-key> docker compose up --build`
Expected: three containers start, backend logs show `Started PortfolioTrackerApplication`, frontend logs show Vite dev server ready.

- [ ] **Step 3: Manual smoke test**

1. Open `http://localhost:5173`, create a portfolio, add a BUY transaction for a real symbol (e.g. AAPL, 10, 150.00) — holdings table shows the row with Current Price / Market Value / Unrealized P&L as `—` (no snapshot yet)
2. Wait up to `finnhub.poll-interval-ms` (60s default) for the scheduled job to run
3. Call `curl http://localhost:8080/api/prices/AAPL` — expect 200 with a real cached price
4. Refresh the dashboard — holdings row now shows a real Current Price, Market Value, and colored Unrealized P&L; the new summary bar above the table shows Total Market Value and Unrealized P&L

Expected: all four steps work with no console errors, no failed network requests (check browser devtools Network tab).

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml
git commit -m "Wire FINNHUB_API_KEY through Docker-compose; verify live price polling end-to-end"
```

---

## M2 Completion Checklist

- [ ] All 9 tasks committed
- [ ] `docker compose up --build` runs postgres + backend + frontend cleanly
- [ ] Manual smoke test (Task 9, Step 3) passes with a real Finnhub key
- [ ] `mvn test` passes (all Testcontainers integration tests + Mockito unit tests, M1 and M2)
- [ ] `npx vitest run` passes (client.test.ts + portfolioSummary.test.ts)
- [ ] Ready for `/clear` — next session resumes at M3 (analytics) per spec
