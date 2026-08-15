# M3: Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sector allocation (pie), performance vs SPY (line), and realized/unrealized P&L breakdown surface on the dashboard, backed by two new Finnhub-fed caches (`SymbolProfile`, `PriceHistory`) that respect the same rate-limit discipline M2 established.

**Architecture:** Two new cache-and-sync subsystems mirror M2's `PriceProvider`/`PriceSnapshot` pattern exactly: `SymbolProfileProvider`/`SymbolProfile` (sector/industry, fetched once per symbol and swept periodically only for symbols missing a profile — never re-polled) and `CandleProvider`/`PriceHistory` (daily closes, refreshed once a day for every symbol ever transacted plus the `SPY` benchmark). A new `analytics` package holds three pure calculators (`AllocationCalculator`, `PerformanceCalculator`, `PnlCalculator`) operating directly on entities, mirroring how `HoldingCalculator` already works — and a single `AnalyticsController` exposing all three as read endpoints. Frontend adds Chart.js for the pie/line visualizations.

**Tech Stack:** Same as M1/M2 (Spring Boot 3.3, Java 21, Postgres 16, Testcontainers, JUnit5+Mockito+AssertJ; React 18, Vite, TypeScript, Tailwind, Vitest) plus `chart.js` + `react-chartjs-2` (new frontend deps). `RestClient`/`MockRestServiceServer` already on the classpath.

## Global Constraints

- USD only, US-listed equities only (v1) — no currency field
- No auth in M3 — all endpoints open (M4 adds JWT + user scoping)
- Finnhub free tier: 60 calls/min — `SymbolProfileSyncScheduler` and `PriceHistorySyncScheduler` both run on a daily cadence (not per-request), same as M2's price poll respects the same budget
- Finnhub `/stock/candle` free tier returns up to ~1 year of daily history per call — performance-chart window is clamped to `max(portfolio inception date, today - finnhub.history-lookback-days)`; this is a known v1 limitation, not a bug, for portfolios older than a year
- `SymbolProfileProvider`/`CandleProvider` abstract Finnhub — real HTTP calls never happen in tests (same seam pattern as `PriceProvider`)
- Sector data cached once per symbol, refreshed only via a periodic sweep for symbols still missing a profile — never re-polled on a fixed interval, per spec's explicit scope assumption
- Local Docker-compose only — no cloud deploy config
- No `Co-Authored-By: Claude` trailer on any commit — plain commits, author only
- `HoldingCalculator`, `AllocationCalculator`, `PerformanceCalculator`, and `PnlCalculator` all stay pure/price-agnostic — they take entities/cached rows as input and return computed results; no Finnhub calls or repository access inside a calculator
- Before starting each task, check active model against the model table in the spec's "Claude Code Dev Workflow" section; if mismatched, tell the user to switch before proceeding (suggested model noted per task below)

---

### Task 1: SymbolProfile entity and repository

**Suggested model:** Haiku 4.5 (mechanical scaffold, mirrors M2 Task 1's `PriceSnapshot` pattern)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/symbolprofile/SymbolProfile.java`
- Create: `backend/src/main/java/com/portfoliotracker/symbolprofile/SymbolProfileRepository.java`
- Test: `backend/src/test/java/com/portfoliotracker/symbolprofile/SymbolProfileRepositoryTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks (first M3 entity).
- Produces: `SymbolProfile` entity (`symbol: String @Id, sector: String (nullable), name: String, fetchedAt: Instant`), `SymbolProfileRepository.findBySymbol(String): Optional<SymbolProfile>`.

- [ ] **Step 1: Write failing repository test**

```java
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=SymbolProfileRepositoryTest`
Expected: FAIL — compile error, `SymbolProfile`/`SymbolProfileRepository` don't exist yet.

- [ ] **Step 3: Write `SymbolProfile.java`**

```java
package com.portfoliotracker.symbolprofile;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "symbol_profile")
@Getter
@Setter
@NoArgsConstructor
public class SymbolProfile {

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(nullable = true)
    private String sector;

    @Column(nullable = false)
    private String name;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
```

- [ ] **Step 4: Write `SymbolProfileRepository.java`**

```java
package com.portfoliotracker.symbolprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SymbolProfileRepository extends JpaRepository<SymbolProfile, String> {
    Optional<SymbolProfile> findBySymbol(String symbol);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=SymbolProfileRepositoryTest`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/symbolprofile backend/src/test/java/com/portfoliotracker/symbolprofile
git commit -m "Add SymbolProfile entity and repository"
```

---

### Task 2: SymbolProfileProvider interface and Finnhub implementation

**Suggested model:** Sonnet 5 (external API integration, error-handling judgment)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/symbolprofile/SymbolProfileProvider.java`
- Create: `backend/src/main/java/com/portfoliotracker/symbolprofile/FinnhubSymbolProfileProvider.java`
- Create: `backend/src/main/java/com/portfoliotracker/symbolprofile/FinnhubProfileResponse.java`
- Create: `backend/src/main/java/com/portfoliotracker/symbolprofile/SymbolProfileData.java`
- Test: `backend/src/test/java/com/portfoliotracker/symbolprofile/FinnhubSymbolProfileProviderTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces: `SymbolProfileProvider.getProfile(String symbol): Optional<SymbolProfileData>` where `SymbolProfileData(String sector, String name)` — returns empty on any failure (bad symbol, network error, non-2xx, or Finnhub's own "N/A"/blank `finnhubIndustry`). This is the seam Task 3's `SymbolProfileService` depends on and the seam tests mock instead of hitting real Finnhub.

- [ ] **Step 1: Write failing test using `MockRestServiceServer`**

```java
package com.portfoliotracker.symbolprofile;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FinnhubSymbolProfileProviderTest {

    @Test
    void parsesSectorAndNameFromProfileResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/stock/profile2?symbol=AAPL&token=test-key"))
                .andRespond(withSuccess(
                        "{\"name\":\"Apple Inc\",\"finnhubIndustry\":\"Technology\",\"ticker\":\"AAPL\",\"country\":\"US\"}",
                        MediaType.APPLICATION_JSON));

        FinnhubSymbolProfileProvider provider = new FinnhubSymbolProfileProvider(builder, "https://finnhub.io/api/v1", "test-key");

        Optional<SymbolProfileData> profile = provider.getProfile("AAPL");

        assertThat(profile).isPresent();
        assertThat(profile.get().sector()).isEqualTo("Technology");
        assertThat(profile.get().name()).isEqualTo("Apple Inc");
    }

    @Test
    void returnsEmptyWhenApiCallFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/stock/profile2?symbol=BADSYM&token=test-key"))
                .andRespond(withServerError());

        FinnhubSymbolProfileProvider provider = new FinnhubSymbolProfileProvider(builder, "https://finnhub.io/api/v1", "test-key");

        Optional<SymbolProfileData> profile = provider.getProfile("BADSYM");

        assertThat(profile).isEmpty();
    }

    @Test
    void returnsEmptyWhenNameIsBlank() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/stock/profile2?symbol=UNKNOWN&token=test-key"))
                .andRespond(withSuccess("{\"name\":\"\",\"finnhubIndustry\":\"\"}", MediaType.APPLICATION_JSON));

        FinnhubSymbolProfileProvider provider = new FinnhubSymbolProfileProvider(builder, "https://finnhub.io/api/v1", "test-key");

        Optional<SymbolProfileData> profile = provider.getProfile("UNKNOWN");

        assertThat(profile).isEmpty();
    }

    @Test
    void treatsMissingFinnhubIndustryAsNullSectorRatherThanFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/stock/profile2?symbol=WEIRD&token=test-key"))
                .andRespond(withSuccess("{\"name\":\"Weird Corp\",\"finnhubIndustry\":\"N/A\"}", MediaType.APPLICATION_JSON));

        FinnhubSymbolProfileProvider provider = new FinnhubSymbolProfileProvider(builder, "https://finnhub.io/api/v1", "test-key");

        Optional<SymbolProfileData> profile = provider.getProfile("WEIRD");

        assertThat(profile).isPresent();
        assertThat(profile.get().sector()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=FinnhubSymbolProfileProviderTest`
Expected: FAIL — compile error, classes don't exist yet.

- [ ] **Step 3: Write `SymbolProfileData.java`**

```java
package com.portfoliotracker.symbolprofile;

public record SymbolProfileData(String sector, String name) {
}
```

- [ ] **Step 4: Write `SymbolProfileProvider.java`**

```java
package com.portfoliotracker.symbolprofile;

import java.util.Optional;

public interface SymbolProfileProvider {
    Optional<SymbolProfileData> getProfile(String symbol);
}
```

- [ ] **Step 5: Write `FinnhubProfileResponse.java`**

```java
package com.portfoliotracker.symbolprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubProfileResponse(String name, @JsonProperty("finnhubIndustry") String finnhubIndustry) {
}
```

- [ ] **Step 6: Write `FinnhubSymbolProfileProvider.java`**

```java
package com.portfoliotracker.symbolprofile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class FinnhubSymbolProfileProvider implements SymbolProfileProvider {

    private final RestClient restClient;
    private final String apiKey;

    public FinnhubSymbolProfileProvider(RestClient.Builder restClientBuilder,
                                         @Value("${finnhub.base-url}") String baseUrl,
                                         @Value("${finnhub.api-key}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public Optional<SymbolProfileData> getProfile(String symbol) {
        try {
            FinnhubProfileResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/stock/profile2")
                            .queryParam("symbol", symbol)
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .body(FinnhubProfileResponse.class);

            if (response == null || response.name() == null || response.name().isBlank()) {
                return Optional.empty();
            }
            String sector = normalizeSector(response.finnhubIndustry());
            return Optional.of(new SymbolProfileData(sector, response.name()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String normalizeSector(String rawSector) {
        if (rawSector == null || rawSector.isBlank() || rawSector.equalsIgnoreCase("N/A")) {
            return null;
        }
        return rawSector;
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=FinnhubSymbolProfileProviderTest`
Expected: PASS (4 tests)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/symbolprofile backend/src/test/java/com/portfoliotracker/symbolprofile
git commit -m "Add SymbolProfileProvider interface and Finnhub company-profile implementation"
```

---

### Task 3: SymbolProfileService and sweep scheduler

**Suggested model:** Sonnet 5 (scheduled-job correctness, per spec's model table)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/symbolprofile/SymbolProfileService.java`
- Create: `backend/src/main/java/com/portfoliotracker/symbolprofile/SymbolProfileSyncScheduler.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/portfoliotracker/symbolprofile/SymbolProfileServiceTest.java`
- Test: `backend/src/test/java/com/portfoliotracker/symbolprofile/SymbolProfileSyncSchedulerTest.java`

**Interfaces:**
- Consumes: `SymbolProfileProvider.getProfile(String): Optional<SymbolProfileData>` (Task 2), `SymbolProfileRepository.save`/`findBySymbol` (Task 1), `TransactionRepository.findDistinctSymbols(): List<String>` (M2).
- Produces: `SymbolProfileService.ensureCached(String symbol): void`, `SymbolProfileService.getProfile(String symbol): Optional<SymbolProfile>` — consumed by Task 4's allocation calculator. `SymbolProfileSyncScheduler.sweep(): void`, invoked automatically via `@Scheduled(fixedRateString = "${finnhub.daily-sync-interval-ms}")`, only fetching symbols that don't already have a cached profile (per the spec's "cached once per symbol" constraint — this is the key difference from M2's `PriceSyncScheduler`, which re-polls every symbol every cycle unconditionally).

- [ ] **Step 1: Write failing unit tests for the service**

```java
package com.portfoliotracker.symbolprofile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SymbolProfileServiceTest {

    @Mock
    SymbolProfileProvider symbolProfileProvider;

    @Mock
    SymbolProfileRepository symbolProfileRepository;

    @Test
    void ensureCachedSkipsFetchWhenProfileAlreadyExists() {
        SymbolProfileService service = new SymbolProfileService(symbolProfileProvider, symbolProfileRepository);
        SymbolProfile existing = new SymbolProfile();
        existing.setSymbol("AAPL");
        when(symbolProfileRepository.findBySymbol("AAPL")).thenReturn(Optional.of(existing));

        service.ensureCached("AAPL");

        verify(symbolProfileProvider, never()).getProfile(any());
    }

    @Test
    void ensureCachedFetchesAndSavesWhenProfileMissing() {
        SymbolProfileService service = new SymbolProfileService(symbolProfileProvider, symbolProfileRepository);
        when(symbolProfileRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(symbolProfileProvider.getProfile("AAPL"))
                .thenReturn(Optional.of(new SymbolProfileData("Technology", "Apple Inc")));

        service.ensureCached("AAPL");

        verify(symbolProfileRepository).save(argThatMatchesAaplProfile());
    }

    @Test
    void ensureCachedSkipsSaveWhenProviderReturnsEmpty() {
        SymbolProfileService service = new SymbolProfileService(symbolProfileProvider, symbolProfileRepository);
        when(symbolProfileRepository.findBySymbol("BADSYM")).thenReturn(Optional.empty());
        when(symbolProfileProvider.getProfile("BADSYM")).thenReturn(Optional.empty());

        service.ensureCached("BADSYM");

        verify(symbolProfileRepository, never()).save(any());
    }

    @Test
    void getProfileDelegatesToRepository() {
        SymbolProfileService service = new SymbolProfileService(symbolProfileProvider, symbolProfileRepository);
        SymbolProfile profile = new SymbolProfile();
        profile.setSymbol("AAPL");
        when(symbolProfileRepository.findBySymbol("AAPL")).thenReturn(Optional.of(profile));

        Optional<SymbolProfile> result = service.getProfile("AAPL");

        assertThat(result).contains(profile);
    }

    private SymbolProfile argThatMatchesAaplProfile() {
        return org.mockito.ArgumentMatchers.argThat(profile ->
                profile.getSymbol().equals("AAPL")
                        && profile.getSector().equals("Technology")
                        && profile.getName().equals("Apple Inc")
                        && profile.getFetchedAt() != null);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest=SymbolProfileServiceTest`
Expected: FAIL — `SymbolProfileService` doesn't exist yet.

- [ ] **Step 3: Write `SymbolProfileService.java`**

```java
package com.portfoliotracker.symbolprofile;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class SymbolProfileService {

    private final SymbolProfileProvider symbolProfileProvider;
    private final SymbolProfileRepository symbolProfileRepository;

    public SymbolProfileService(SymbolProfileProvider symbolProfileProvider, SymbolProfileRepository symbolProfileRepository) {
        this.symbolProfileProvider = symbolProfileProvider;
        this.symbolProfileRepository = symbolProfileRepository;
    }

    public void ensureCached(String symbol) {
        if (symbolProfileRepository.findBySymbol(symbol).isPresent()) {
            return;
        }
        symbolProfileProvider.getProfile(symbol).ifPresent(data -> {
            SymbolProfile profile = new SymbolProfile();
            profile.setSymbol(symbol);
            profile.setSector(data.sector());
            profile.setName(data.name());
            profile.setFetchedAt(Instant.now());
            symbolProfileRepository.save(profile);
        });
    }

    public Optional<SymbolProfile> getProfile(String symbol) {
        return symbolProfileRepository.findBySymbol(symbol);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=SymbolProfileServiceTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Write failing unit test for the sweep scheduler**

```java
package com.portfoliotracker.symbolprofile;

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
class SymbolProfileSyncSchedulerTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    SymbolProfileService symbolProfileService;

    @Test
    void sweepEnsuresCachedProfileForEachDistinctSymbol() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of("AAPL", "MSFT"));

        SymbolProfileSyncScheduler scheduler = new SymbolProfileSyncScheduler(transactionRepository, symbolProfileService);
        scheduler.sweep();

        verify(symbolProfileService).ensureCached("AAPL");
        verify(symbolProfileService).ensureCached("MSFT");
    }

    @Test
    void sweepDoesNothingWhenNoTransactionsExist() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of());

        SymbolProfileSyncScheduler scheduler = new SymbolProfileSyncScheduler(transactionRepository, symbolProfileService);
        scheduler.sweep();

        verifyNoInteractions(symbolProfileService);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=SymbolProfileSyncSchedulerTest`
Expected: FAIL — `SymbolProfileSyncScheduler` doesn't exist yet.

- [ ] **Step 7: Write `SymbolProfileSyncScheduler.java`**

```java
package com.portfoliotracker.symbolprofile;

import com.portfoliotracker.transaction.TransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "finnhub", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SymbolProfileSyncScheduler {

    private final TransactionRepository transactionRepository;
    private final SymbolProfileService symbolProfileService;

    public SymbolProfileSyncScheduler(TransactionRepository transactionRepository, SymbolProfileService symbolProfileService) {
        this.transactionRepository = transactionRepository;
        this.symbolProfileService = symbolProfileService;
    }

    @Scheduled(fixedRateString = "${finnhub.daily-sync-interval-ms}")
    public void sweep() {
        for (String symbol : transactionRepository.findDistinctSymbols()) {
            symbolProfileService.ensureCached(symbol);
        }
    }
}
```

- [ ] **Step 8: Add daily-sync interval to `application.yml`**

Add inside the existing `finnhub:` block in `backend/src/main/resources/application.yml` (alongside `poll-interval-ms`):

```yaml
  daily-sync-interval-ms: 86400000
  history-lookback-days: 365
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=SymbolProfileSyncSchedulerTest`
Expected: PASS (2 tests)

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/symbolprofile backend/src/main/resources/application.yml backend/src/test/java/com/portfoliotracker/symbolprofile
git commit -m "Add SymbolProfileService and sweep scheduler (cache-once-per-symbol semantics)"
```

---

### Task 4: Allocation calculator and endpoint

**Suggested model:** Sonnet 5 (correctness-critical grouping/percentage math, per spec's model table)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/analytics/AllocationCalculator.java`
- Create: `backend/src/main/java/com/portfoliotracker/analytics/dto/AllocationResponse.java`
- Create: `backend/src/main/java/com/portfoliotracker/analytics/dto/SectorAllocation.java`
- Create: `backend/src/main/java/com/portfoliotracker/analytics/AnalyticsController.java`
- Test: `backend/src/test/java/com/portfoliotracker/analytics/AllocationCalculatorTest.java`
- Test: `backend/src/test/java/com/portfoliotracker/analytics/AnalyticsControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `Holding` (M1, from `HoldingCalculator.calculate`), `PriceService.getLatest(String): Optional<PriceSnapshot>` (M2), `SymbolProfileService.getProfile(String): Optional<SymbolProfile>` (Task 3).
- Produces: `AllocationCalculator.calculate(List<Holding>, Function<String, Optional<BigDecimal>> priceLookup, Function<String, Optional<String>> sectorLookup): AllocationResponse`, `GET /api/portfolios/{id}/analytics/allocation` → `AllocationResponse` (`List<SectorAllocation>` where `SectorAllocation(String sector, BigDecimal marketValue, BigDecimal percent)`; holdings with no cached price are excluded from the total — same convention M2 established for null valuations; holdings with no cached sector are grouped under `"Unknown"`).

- [ ] **Step 1: Write failing unit tests for the pure calculator**

```java
package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.AllocationResponse;
import com.portfoliotracker.analytics.dto.SectorAllocation;
import com.portfoliotracker.holding.Holding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationCalculatorTest {

    @Test
    void groupsMarketValueBySectorAndComputesPercent() {
        List<Holding> holdings = List.of(
                new Holding("AAPL", new BigDecimal("10"), new BigDecimal("100")),
                new Holding("MSFT", new BigDecimal("5"), new BigDecimal("200")),
                new Holding("JPM", new BigDecimal("2"), new BigDecimal("150")));
        Map<String, BigDecimal> prices = Map.of(
                "AAPL", new BigDecimal("150"), "MSFT", new BigDecimal("300"), "JPM", new BigDecimal("175"));
        Map<String, String> sectors = Map.of("AAPL", "Technology", "MSFT", "Technology", "JPM", "Financials");

        AllocationResponse response = AllocationCalculator.calculate(holdings,
                symbol -> Optional.ofNullable(prices.get(symbol)),
                symbol -> Optional.ofNullable(sectors.get(symbol)));

        // AAPL: 10*150=1500, MSFT: 5*300=1500, JPM: 2*175=350 -- total 3350
        // Technology: 3000 (89.55%), Financials: 350 (10.45%)
        assertThat(response.sectors()).hasSize(2);
        SectorAllocation tech = response.sectors().stream().filter(s -> s.sector().equals("Technology")).findFirst().orElseThrow();
        assertThat(tech.marketValue()).isEqualByComparingTo("3000");
        assertThat(tech.percent()).isEqualByComparingTo("89.5522");
    }

    @Test
    void groupsHoldingsWithNoCachedSectorUnderUnknown() {
        List<Holding> holdings = List.of(new Holding("ZZZZ", new BigDecimal("1"), new BigDecimal("10")));
        Map<String, BigDecimal> prices = Map.of("ZZZZ", new BigDecimal("20"));

        AllocationResponse response = AllocationCalculator.calculate(holdings,
                symbol -> Optional.ofNullable(prices.get(symbol)),
                symbol -> Optional.empty());

        assertThat(response.sectors()).hasSize(1);
        assertThat(response.sectors().get(0).sector()).isEqualTo("Unknown");
        assertThat(response.sectors().get(0).percent()).isEqualByComparingTo("100.0000");
    }

    @Test
    void excludesHoldingsWithNoCachedPriceFromTotals() {
        List<Holding> holdings = List.of(
                new Holding("AAPL", new BigDecimal("10"), new BigDecimal("100")),
                new Holding("ZZZZ", new BigDecimal("5"), new BigDecimal("10")));
        Map<String, BigDecimal> prices = Map.of("AAPL", new BigDecimal("150"));
        Map<String, String> sectors = Map.of("AAPL", "Technology");

        AllocationResponse response = AllocationCalculator.calculate(holdings,
                symbol -> Optional.ofNullable(prices.get(symbol)),
                symbol -> Optional.ofNullable(sectors.get(symbol)));

        assertThat(response.sectors()).hasSize(1);
        assertThat(response.sectors().get(0).sector()).isEqualTo("Technology");
        assertThat(response.sectors().get(0).percent()).isEqualByComparingTo("100.0000");
    }

    @Test
    void returnsEmptySectorListWhenNoHoldingsHavePrices() {
        List<Holding> holdings = List.of(new Holding("ZZZZ", new BigDecimal("5"), new BigDecimal("10")));

        AllocationResponse response = AllocationCalculator.calculate(holdings, symbol -> Optional.empty(), symbol -> Optional.empty());

        assertThat(response.sectors()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=AllocationCalculatorTest`
Expected: FAIL — compile error, classes don't exist yet.

- [ ] **Step 3: Write `dto/SectorAllocation.java`**

```java
package com.portfoliotracker.analytics.dto;

import java.math.BigDecimal;

public record SectorAllocation(String sector, BigDecimal marketValue, BigDecimal percent) {
}
```

- [ ] **Step 4: Write `dto/AllocationResponse.java`**

```java
package com.portfoliotracker.analytics.dto;

import java.util.List;

public record AllocationResponse(List<SectorAllocation> sectors) {
}
```

- [ ] **Step 5: Write `AllocationCalculator.java`**

```java
package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.AllocationResponse;
import com.portfoliotracker.analytics.dto.SectorAllocation;
import com.portfoliotracker.holding.Holding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class AllocationCalculator {

    private static final int PERCENT_SCALE = 4;

    public static AllocationResponse calculate(List<Holding> holdings,
                                                 Function<String, java.util.Optional<BigDecimal>> priceLookup,
                                                 Function<String, java.util.Optional<String>> sectorLookup) {
        Map<String, BigDecimal> valueBySector = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            java.util.Optional<BigDecimal> price = priceLookup.apply(holding.symbol());
            if (price.isEmpty()) {
                continue;
            }
            BigDecimal marketValue = price.get().multiply(holding.quantity());
            String sector = sectorLookup.apply(holding.symbol()).orElse("Unknown");
            valueBySector.merge(sector, marketValue, BigDecimal::add);
            total = total.add(marketValue);
        }

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return new AllocationResponse(List.of());
        }

        BigDecimal finalTotal = total;
        List<SectorAllocation> sectors = valueBySector.entrySet().stream()
                .map(e -> new SectorAllocation(e.getKey(), e.getValue(),
                        e.getValue().multiply(BigDecimal.valueOf(100)).divide(finalTotal, PERCENT_SCALE, RoundingMode.HALF_UP)))
                .toList();

        return new AllocationResponse(sectors);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=AllocationCalculatorTest`
Expected: PASS (4 tests)

- [ ] **Step 7: Write failing integration test for the endpoint**

```java
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
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=AnalyticsControllerIntegrationTest`
Expected: FAIL — `/api/portfolios/{id}/analytics/allocation` 404s regardless of DB state, `AnalyticsController` doesn't exist.

- [ ] **Step 9: Write `AnalyticsController.java`**

```java
package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.AllocationResponse;
import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.holding.HoldingCalculator;
import com.portfoliotracker.portfolio.PortfolioRepository;
import com.portfoliotracker.price.PriceService;
import com.portfoliotracker.symbolprofile.SymbolProfileService;
import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/analytics")
public class AnalyticsController {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;
    private final SymbolProfileService symbolProfileService;

    public AnalyticsController(PortfolioRepository portfolioRepository,
                                TransactionRepository transactionRepository,
                                PriceService priceService,
                                SymbolProfileService symbolProfileService) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.priceService = priceService;
        this.symbolProfileService = symbolProfileService;
    }

    @GetMapping("/allocation")
    public AllocationResponse allocation(@PathVariable Long portfolioId) {
        List<Holding> holdings = holdingsFor(portfolioId);
        return AllocationCalculator.calculate(holdings,
                symbol -> priceService.getLatest(symbol).map(com.portfoliotracker.price.PriceSnapshot::getPrice),
                symbol -> symbolProfileService.getProfile(symbol).map(com.portfoliotracker.symbolprofile.SymbolProfile::getSector));
    }

    private List<Holding> holdingsFor(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
        List<Transaction> txs = transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        return HoldingCalculator.calculate(txs);
    }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=AnalyticsControllerIntegrationTest`
Expected: PASS (2 tests)

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/analytics backend/src/test/java/com/portfoliotracker/analytics
git commit -m "Add sector allocation calculator and GET /api/portfolios/{id}/analytics/allocation"
```

---

### Task 5: Frontend allocation pie chart

**Suggested model:** Sonnet 5 (first Chart.js integration, per spec's "React hooks/state" model tier)

**Files:**
- Modify: `frontend/package.json` (add `chart.js`, `react-chartjs-2`)
- Modify: `frontend/src/types/portfolio.ts`
- Modify: `frontend/src/api/portfolios.ts`
- Create: `frontend/src/components/AllocationChart.tsx`
- Modify: `frontend/src/pages/Dashboard.tsx`

**Interfaces:**
- Consumes: `GET /api/portfolios/{id}/analytics/allocation` (Task 4).
- Produces: `AllocationChart` component mounted in `Dashboard`, consumed visually only (no downstream task depends on its internals).

- [ ] **Step 1: Install Chart.js dependencies**

Run: `cd frontend && npm install chart.js react-chartjs-2`

- [ ] **Step 2: Add allocation types to `frontend/src/types/portfolio.ts`**

Add at the end of the file:

```ts
export interface SectorAllocation {
  sector: string;
  marketValue: number;
  percent: number;
}

export interface AllocationResponse {
  sectors: SectorAllocation[];
}
```

- [ ] **Step 3: Add API client function to `frontend/src/api/portfolios.ts`**

Add the import and function:

```ts
import type { Portfolio, Holding, Transaction, CreateTransactionInput, AllocationResponse } from '../types/portfolio';
```

```ts
export const getAllocation = (portfolioId: number) =>
  apiFetch<AllocationResponse>(`/api/portfolios/${portfolioId}/analytics/allocation`);
```

- [ ] **Step 4: Write `frontend/src/components/AllocationChart.tsx`**

```tsx
import { Pie } from 'react-chartjs-2';
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js';
import type { SectorAllocation } from '../types/portfolio';

ChartJS.register(ArcElement, Tooltip, Legend);

const COLORS = ['#2563eb', '#16a34a', '#dc2626', '#d97706', '#7c3aed', '#0891b2', '#db2777', '#65a30d'];

export function AllocationChart({ sectors }: { sectors: SectorAllocation[] }) {
  if (sectors.length === 0) {
    return null;
  }

  const data = {
    labels: sectors.map((s) => `${s.sector} (${s.percent.toFixed(1)}%)`),
    datasets: [
      {
        data: sectors.map((s) => s.marketValue),
        backgroundColor: sectors.map((_, i) => COLORS[i % COLORS.length]),
      },
    ],
  };

  return (
    <div className="mb-6 max-w-sm">
      <h2 className="text-lg font-semibold mb-2">Sector Allocation</h2>
      <Pie data={data} />
    </div>
  );
}
```

- [ ] **Step 5: Wire into `frontend/src/pages/Dashboard.tsx`**

Add the import:

```tsx
import { AllocationChart } from '../components/AllocationChart';
import { getAllocation } from '../api/portfolios';
import type { SectorAllocation } from '../types/portfolio';
```

Add state alongside the existing `holdings` state:

```tsx
const [sectors, setSectors] = useState<SectorAllocation[]>([]);
```

In the `useEffect` that loads holdings/transactions for `selectedId`, add:

```tsx
getAllocation(selectedId).then((r) => setSectors(r.sectors)).catch((e) => setError(e.message));
```

Add `<AllocationChart sectors={sectors} />` immediately after `<PortfolioSummary holdings={holdings} />` and before `<HoldingsTable holdings={holdings} />`.

- [ ] **Step 6: Verify frontend still builds and existing tests pass**

Run: `cd frontend && npx tsc -b && npx vitest run`
Expected: PASS (existing 6 tests unaffected — this task adds no new frontend unit tests since `AllocationChart` is a thin presentational wrapper, matching how `PortfolioSummary` was handled in M2)

- [ ] **Step 7: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/types/portfolio.ts frontend/src/api/portfolios.ts frontend/src/components/AllocationChart.tsx frontend/src/pages/Dashboard.tsx
git commit -m "Add sector allocation pie chart to dashboard"
```

---

### Task 6: PriceHistory entity and repository

**Suggested model:** Haiku 4.5 (mechanical scaffold, mirrors M2 Task 1's `PriceSnapshot` pattern)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/pricehistory/PriceHistory.java`
- Create: `backend/src/main/java/com/portfoliotracker/pricehistory/PriceHistoryRepository.java`
- Test: `backend/src/test/java/com/portfoliotracker/pricehistory/PriceHistoryRepositoryTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces: `PriceHistory` entity (`id: Long, symbol: String, priceDate: LocalDate, close: BigDecimal`), `PriceHistoryRepository.findBySymbolOrderByPriceDateAsc(String): List<PriceHistory>`, `PriceHistoryRepository.deleteBySymbol(String): void`.

- [ ] **Step 1: Write failing repository test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PriceHistoryRepositoryTest`
Expected: FAIL — compile error, `PriceHistory`/`PriceHistoryRepository` don't exist yet.

- [ ] **Step 3: Write `PriceHistory.java`**

```java
package com.portfoliotracker.pricehistory;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "price_history", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "price_date"}))
@Getter
@Setter
@NoArgsConstructor
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal close;
}
```

- [ ] **Step 4: Write `PriceHistoryRepository.java`**

```java
package com.portfoliotracker.pricehistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findBySymbolOrderByPriceDateAsc(String symbol);

    void deleteBySymbol(String symbol);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PriceHistoryRepositoryTest`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/pricehistory backend/src/test/java/com/portfoliotracker/pricehistory
git commit -m "Add PriceHistory entity and repository"
```

---

### Task 7: CandleProvider interface and Finnhub implementation

**Suggested model:** Sonnet 5 (external API integration, date/timestamp handling, per spec's model table)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/pricehistory/CandleProvider.java`
- Create: `backend/src/main/java/com/portfoliotracker/pricehistory/FinnhubCandleProvider.java`
- Create: `backend/src/main/java/com/portfoliotracker/pricehistory/FinnhubCandleResponse.java`
- Create: `backend/src/main/java/com/portfoliotracker/pricehistory/DailyClose.java`
- Test: `backend/src/test/java/com/portfoliotracker/pricehistory/FinnhubCandleProviderTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces: `CandleProvider.getDailyCloses(String symbol, LocalDate from, LocalDate to): List<DailyClose>` where `DailyClose(LocalDate date, BigDecimal close)` — returns an empty list on any failure (bad symbol, network error, non-2xx, or Finnhub's `"s":"no_data"` status). This is the seam Task 8's `PriceHistoryService` depends on.

- [ ] **Step 1: Write failing test using `MockRestServiceServer`**

```java
package com.portfoliotracker.pricehistory;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FinnhubCandleProviderTest {

    @Test
    void parsesDailyClosesFromCandleResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 2026-01-01T00:00:00Z = 1767225600, 2026-01-02T00:00:00Z = 1767312000
        server.expect(requestTo("https://finnhub.io/api/v1/stock/candle?symbol=AAPL&resolution=D&from=1767225600&to=1767312000&token=test-key"))
                .andRespond(withSuccess(
                        "{\"c\":[150.00,151.50],\"t\":[1767225600,1767312000],\"s\":\"ok\"}",
                        MediaType.APPLICATION_JSON));

        FinnhubCandleProvider provider = new FinnhubCandleProvider(builder, "https://finnhub.io/api/v1", "test-key");

        List<DailyClose> closes = provider.getDailyCloses("AAPL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        assertThat(closes).hasSize(2);
        assertThat(closes.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(closes.get(0).close()).isEqualByComparingTo("150.00");
        assertThat(closes.get(1).date()).isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @Test
    void returnsEmptyListWhenStatusIsNoData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/stock/candle?symbol=BADSYM&resolution=D&from=1767225600&to=1767312000&token=test-key"))
                .andRespond(withSuccess("{\"s\":\"no_data\"}", MediaType.APPLICATION_JSON));

        FinnhubCandleProvider provider = new FinnhubCandleProvider(builder, "https://finnhub.io/api/v1", "test-key");

        List<DailyClose> closes = provider.getDailyCloses("BADSYM", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        assertThat(closes).isEmpty();
    }

    @Test
    void returnsEmptyListWhenApiCallFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://finnhub.io/api/v1/stock/candle?symbol=BADSYM&resolution=D&from=1767225600&to=1767312000&token=test-key"))
                .andRespond(withServerError());

        FinnhubCandleProvider provider = new FinnhubCandleProvider(builder, "https://finnhub.io/api/v1", "test-key");

        List<DailyClose> closes = provider.getDailyCloses("BADSYM", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        assertThat(closes).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=FinnhubCandleProviderTest`
Expected: FAIL — compile error, classes don't exist yet.

- [ ] **Step 3: Write `DailyClose.java`**

```java
package com.portfoliotracker.pricehistory;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyClose(LocalDate date, BigDecimal close) {
}
```

- [ ] **Step 4: Write `CandleProvider.java`**

```java
package com.portfoliotracker.pricehistory;

import java.time.LocalDate;
import java.util.List;

public interface CandleProvider {
    List<DailyClose> getDailyCloses(String symbol, LocalDate from, LocalDate to);
}
```

- [ ] **Step 5: Write `FinnhubCandleResponse.java`**

```java
package com.portfoliotracker.pricehistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubCandleResponse(List<BigDecimal> c, List<Long> t, String s) {
}
```

- [ ] **Step 6: Write `FinnhubCandleProvider.java`**

```java
package com.portfoliotracker.pricehistory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class FinnhubCandleProvider implements CandleProvider {

    private final RestClient restClient;
    private final String apiKey;

    public FinnhubCandleProvider(RestClient.Builder restClientBuilder,
                                  @Value("${finnhub.base-url}") String baseUrl,
                                  @Value("${finnhub.api-key}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<DailyClose> getDailyCloses(String symbol, LocalDate from, LocalDate to) {
        try {
            long fromEpoch = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            long toEpoch = to.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

            FinnhubCandleResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/stock/candle")
                            .queryParam("symbol", symbol)
                            .queryParam("resolution", "D")
                            .queryParam("from", fromEpoch)
                            .queryParam("to", toEpoch)
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .body(FinnhubCandleResponse.class);

            if (response == null || !"ok".equals(response.s()) || response.c() == null || response.t() == null) {
                return List.of();
            }

            List<DailyClose> closes = new ArrayList<>();
            for (int i = 0; i < response.c().size() && i < response.t().size(); i++) {
                LocalDate date = java.time.Instant.ofEpochSecond(response.t().get(i)).atZone(ZoneOffset.UTC).toLocalDate();
                closes.add(new DailyClose(date, response.c().get(i)));
            }
            return closes;
        } catch (Exception e) {
            return List.of();
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=FinnhubCandleProviderTest`
Expected: PASS (3 tests)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/pricehistory backend/src/test/java/com/portfoliotracker/pricehistory
git commit -m "Add CandleProvider interface and Finnhub daily-candle implementation"
```

---

### Task 8: PriceHistoryService and daily refresh scheduler

**Suggested model:** Sonnet 5 (scheduled-job correctness, per spec's model table)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/pricehistory/PriceHistoryService.java`
- Create: `backend/src/main/java/com/portfoliotracker/pricehistory/PriceHistorySyncScheduler.java`
- Test: `backend/src/test/java/com/portfoliotracker/pricehistory/PriceHistoryServiceTest.java`
- Test: `backend/src/test/java/com/portfoliotracker/pricehistory/PriceHistorySyncSchedulerTest.java`

**Interfaces:**
- Consumes: `CandleProvider.getDailyCloses(String, LocalDate, LocalDate): List<DailyClose>` (Task 7), `PriceHistoryRepository` (Task 6), `TransactionRepository.findDistinctSymbols(): List<String>` (M2).
- Produces: `PriceHistoryService.refresh(String symbol, LocalDate from, LocalDate to): void`, `PriceHistoryService.getHistory(String symbol): List<PriceHistory>` — consumed by Task 9's `PerformanceCalculator`/endpoint. `PriceHistorySyncScheduler.refreshAll(): void`, invoked via `@Scheduled(fixedRateString = "${finnhub.daily-sync-interval-ms}")`, always including the literal benchmark symbol `"SPY"` alongside every distinct transacted symbol.

- [ ] **Step 1: Write failing unit tests for the service**

```java
package com.portfoliotracker.pricehistory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock
    CandleProvider candleProvider;

    @Mock
    PriceHistoryRepository priceHistoryRepository;

    @Test
    void refreshReplacesExistingHistoryForSymbol() {
        PriceHistoryService service = new PriceHistoryService(candleProvider, priceHistoryRepository);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 2);
        when(candleProvider.getDailyCloses("AAPL", from, to)).thenReturn(List.of(
                new DailyClose(LocalDate.of(2026, 1, 1), new BigDecimal("150.00")),
                new DailyClose(LocalDate.of(2026, 1, 2), new BigDecimal("151.50"))));

        service.refresh("AAPL", from, to);

        verify(priceHistoryRepository).deleteBySymbol("AAPL");
        verify(priceHistoryRepository).saveAll(argThat((Iterable<PriceHistory> rows) -> {
            List<PriceHistory> list = new java.util.ArrayList<>();
            rows.forEach(list::add);
            return list.size() == 2 && list.get(0).getSymbol().equals("AAPL");
        }));
    }

    @Test
    void refreshSkipsSaveWhenProviderReturnsNoData() {
        PriceHistoryService service = new PriceHistoryService(candleProvider, priceHistoryRepository);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 2);
        when(candleProvider.getDailyCloses("BADSYM", from, to)).thenReturn(List.of());

        service.refresh("BADSYM", from, to);

        verify(priceHistoryRepository, never()).deleteBySymbol(any());
        verify(priceHistoryRepository, never()).saveAll(any());
    }

    @Test
    void getHistoryDelegatesToRepository() {
        PriceHistoryService service = new PriceHistoryService(candleProvider, priceHistoryRepository);
        PriceHistory row = new PriceHistory();
        row.setSymbol("AAPL");
        when(priceHistoryRepository.findBySymbolOrderByPriceDateAsc("AAPL")).thenReturn(List.of(row));

        List<PriceHistory> result = service.getHistory("AAPL");

        assertThat(result).containsExactly(row);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest=PriceHistoryServiceTest`
Expected: FAIL — `PriceHistoryService` doesn't exist yet.

- [ ] **Step 3: Write `PriceHistoryService.java`**

```java
package com.portfoliotracker.pricehistory;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PriceHistoryService {

    private final CandleProvider candleProvider;
    private final PriceHistoryRepository priceHistoryRepository;

    public PriceHistoryService(CandleProvider candleProvider, PriceHistoryRepository priceHistoryRepository) {
        this.candleProvider = candleProvider;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    public void refresh(String symbol, LocalDate from, LocalDate to) {
        List<DailyClose> closes = candleProvider.getDailyCloses(symbol, from, to);
        if (closes.isEmpty()) {
            return;
        }
        priceHistoryRepository.deleteBySymbol(symbol);
        List<PriceHistory> rows = closes.stream().map(dc -> {
            PriceHistory row = new PriceHistory();
            row.setSymbol(symbol);
            row.setPriceDate(dc.date());
            row.setClose(dc.close());
            return row;
        }).toList();
        priceHistoryRepository.saveAll(rows);
    }

    public List<PriceHistory> getHistory(String symbol) {
        return priceHistoryRepository.findBySymbolOrderByPriceDateAsc(symbol);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=PriceHistoryServiceTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Write failing unit test for the refresh scheduler**

```java
package com.portfoliotracker.pricehistory;

import com.portfoliotracker.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceHistorySyncSchedulerTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    PriceHistoryService priceHistoryService;

    @Test
    void refreshAllCoversEveryDistinctSymbolPlusSpyBenchmark() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of("AAPL", "MSFT"));

        PriceHistorySyncScheduler scheduler = new PriceHistorySyncScheduler(transactionRepository, priceHistoryService, 365);
        scheduler.refreshAll();

        ArgumentCaptor<String> symbolCaptor = ArgumentCaptor.forClass(String.class);
        verify(priceHistoryService, org.mockito.Mockito.times(3))
                .refresh(symbolCaptor.capture(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(symbolCaptor.getAllValues()).containsExactlyInAnyOrder("AAPL", "MSFT", "SPY");
    }

    @Test
    void refreshAllStillRefreshesSpyWhenNoTransactionsExist() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of());

        PriceHistorySyncScheduler scheduler = new PriceHistorySyncScheduler(transactionRepository, priceHistoryService, 365);
        scheduler.refreshAll();

        verify(priceHistoryService).refresh(org.mockito.ArgumentMatchers.eq("SPY"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshAllUsesLookbackWindowEndingToday() {
        when(transactionRepository.findDistinctSymbols()).thenReturn(List.of());
        LocalDate today = LocalDate.now();

        PriceHistorySyncScheduler scheduler = new PriceHistorySyncScheduler(transactionRepository, priceHistoryService, 365);
        scheduler.refreshAll();

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(priceHistoryService).refresh(org.mockito.ArgumentMatchers.eq("SPY"), fromCaptor.capture(), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(today);
        assertThat(fromCaptor.getValue()).isEqualTo(today.minusDays(365));
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PriceHistorySyncSchedulerTest`
Expected: FAIL — `PriceHistorySyncScheduler` doesn't exist yet.

- [ ] **Step 7: Write `PriceHistorySyncScheduler.java`**

```java
package com.portfoliotracker.pricehistory;

import com.portfoliotracker.transaction.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "finnhub", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class PriceHistorySyncScheduler {

    private static final String BENCHMARK_SYMBOL = "SPY";

    private final TransactionRepository transactionRepository;
    private final PriceHistoryService priceHistoryService;
    private final int lookbackDays;

    public PriceHistorySyncScheduler(TransactionRepository transactionRepository,
                                      PriceHistoryService priceHistoryService,
                                      @Value("${finnhub.history-lookback-days}") int lookbackDays) {
        this.transactionRepository = transactionRepository;
        this.priceHistoryService = priceHistoryService;
        this.lookbackDays = lookbackDays;
    }

    @Scheduled(fixedRateString = "${finnhub.daily-sync-interval-ms}")
    public void refreshAll() {
        Set<String> symbols = new HashSet<>(transactionRepository.findDistinctSymbols());
        symbols.add(BENCHMARK_SYMBOL);

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(lookbackDays);
        for (String symbol : symbols) {
            priceHistoryService.refresh(symbol, from, to);
        }
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PriceHistorySyncSchedulerTest`
Expected: PASS (3 tests)

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/pricehistory backend/src/test/java/com/portfoliotracker/pricehistory
git commit -m "Add PriceHistoryService and daily refresh scheduler (all transacted symbols + SPY)"
```

---

### Task 9: Performance calculator and endpoint

**Suggested model:** Opus 5 (benchmark/performance-math correctness-critical, per spec's "High-stakes/tricky" model tier)

**Files:**
- Create: `backend/src/main/java/com/portfoliotracker/analytics/PerformanceCalculator.java`
- Create: `backend/src/main/java/com/portfoliotracker/analytics/dto/PerformanceResponse.java`
- Create: `backend/src/main/java/com/portfoliotracker/analytics/dto/PerformancePoint.java`
- Modify: `backend/src/main/java/com/portfoliotracker/analytics/AnalyticsController.java`
- Test: `backend/src/test/java/com/portfoliotracker/analytics/PerformanceCalculatorTest.java`
- Test: `backend/src/test/java/com/portfoliotracker/analytics/AnalyticsControllerIntegrationTest.java` (extend existing file)

**Interfaces:**
- Consumes: `Transaction` entities (M1), `PriceHistory` entities (Task 6), `HoldingCalculator.calculate(List<Transaction>): List<Holding>` (M1) — used internally per-date to reconstruct holdings-as-of a given day.
- Produces: `PerformanceCalculator.calculate(List<Transaction> transactions, Map<String, List<PriceHistory>> historyBySymbol, List<PriceHistory> spyHistory): PerformanceResponse` where `PerformanceResponse(List<PerformancePoint> points)`, `PerformancePoint(LocalDate date, BigDecimal portfolioChangePercent, BigDecimal spyChangePercent)`. `GET /api/portfolios/{id}/analytics/performance` → `PerformanceResponse`.

**Algorithm (documented here since it's the trickiest logic in M3):** Use SPY's history dates as the canonical trading-day timeline (SPY is always fully covered by the scheduler; individual holdings may have gaps/holidays that don't align). For each SPY date `d` in ascending order, compute portfolio value as of `d` by running `HoldingCalculator` over transactions with `executedAt <= d`, pricing each resulting holding at its most recent available close on or before `d` (a holding with no history yet — freshly polled, not yet synced — contributes `0` for that date rather than throwing). The first date where portfolio value is `> 0` becomes the baseline (0%) for both series; SPY's close on that same date is SPY's baseline, so both percentages are comparable from the same starting line. Skip dates before the baseline entirely.

- [ ] **Step 1: Write failing unit tests for the pure calculator**

```java
package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.PerformanceResponse;
import com.portfoliotracker.pricehistory.PriceHistory;
import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceCalculatorTest {

    private PriceHistory row(String symbol, LocalDate date, String close) {
        PriceHistory row = new PriceHistory();
        row.setSymbol(symbol);
        row.setPriceDate(date);
        row.setClose(new BigDecimal(close));
        return row;
    }

    private Transaction buy(String symbol, String qty, String price, LocalDate date) {
        return Transaction.of(symbol, TransactionType.BUY, new BigDecimal(qty), new BigDecimal(price),
                date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void computesPercentChangeRelativeToFirstDateWithNonZeroPortfolioValue() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        LocalDate d3 = LocalDate.of(2026, 1, 3);

        List<Transaction> transactions = List.of(buy("AAPL", "10", "100.00", d1));
        Map<String, List<PriceHistory>> historyBySymbol = Map.of(
                "AAPL", List.of(row("AAPL", d1, "100.00"), row("AAPL", d2, "110.00"), row("AAPL", d3, "120.00")));
        List<PriceHistory> spyHistory = List.of(row("SPY", d1, "500.00"), row("SPY", d2, "510.00"), row("SPY", d3, "505.00"));

        PerformanceResponse response = PerformanceCalculator.calculate(transactions, historyBySymbol, spyHistory);

        assertThat(response.points()).hasSize(3);
        assertThat(response.points().get(0).portfolioChangePercent()).isEqualByComparingTo("0");
        assertThat(response.points().get(0).spyChangePercent()).isEqualByComparingTo("0");
        // AAPL 100 -> 110 = +10%
        assertThat(response.points().get(1).portfolioChangePercent()).isEqualByComparingTo("10.0000");
        // SPY 500 -> 510 = +2%
        assertThat(response.points().get(1).spyChangePercent()).isEqualByComparingTo("2.0000");
        // AAPL 100 -> 120 = +20%
        assertThat(response.points().get(2).portfolioChangePercent()).isEqualByComparingTo("20.0000");
        // SPY 500 -> 505 = +1%
        assertThat(response.points().get(2).spyChangePercent()).isEqualByComparingTo("1.0000");
    }

    @Test
    void skipsDatesBeforeFirstTransaction() {
        LocalDate before = LocalDate.of(2025, 12, 31);
        LocalDate txDate = LocalDate.of(2026, 1, 1);

        List<Transaction> transactions = List.of(buy("AAPL", "10", "100.00", txDate));
        Map<String, List<PriceHistory>> historyBySymbol = Map.of("AAPL", List.of(row("AAPL", txDate, "100.00")));
        List<PriceHistory> spyHistory = List.of(row("SPY", before, "500.00"), row("SPY", txDate, "505.00"));

        PerformanceResponse response = PerformanceCalculator.calculate(transactions, historyBySymbol, spyHistory);

        assertThat(response.points()).hasSize(1);
        assertThat(response.points().get(0).date()).isEqualTo(txDate);
    }

    @Test
    void usesMostRecentCloseOnOrBeforeDateWhenExactDateMissing() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        LocalDate d3 = LocalDate.of(2026, 1, 3);

        List<Transaction> transactions = List.of(buy("AAPL", "10", "100.00", d1));
        // No AAPL close recorded for d3 -- should fall back to d1's close (last known before d3)
        Map<String, List<PriceHistory>> historyBySymbol = Map.of("AAPL", List.of(row("AAPL", d1, "100.00")));
        List<PriceHistory> spyHistory = List.of(row("SPY", d1, "500.00"), row("SPY", d2, "510.00"), row("SPY", d3, "520.00"));

        PerformanceResponse response = PerformanceCalculator.calculate(transactions, historyBySymbol, spyHistory);

        assertThat(response.points()).hasSize(3);
        assertThat(response.points().get(2).portfolioChangePercent()).isEqualByComparingTo("0");
    }

    @Test
    void returnsEmptyPointsWhenNoTransactionsExist() {
        List<PriceHistory> spyHistory = List.of(row("SPY", LocalDate.of(2026, 1, 1), "500.00"));

        PerformanceResponse response = PerformanceCalculator.calculate(List.of(), Map.of(), spyHistory);

        assertThat(response.points()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PerformanceCalculatorTest`
Expected: FAIL — compile error, classes don't exist yet.

- [ ] **Step 3: Write `dto/PerformancePoint.java`**

```java
package com.portfoliotracker.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PerformancePoint(LocalDate date, BigDecimal portfolioChangePercent, BigDecimal spyChangePercent) {
}
```

- [ ] **Step 4: Write `dto/PerformanceResponse.java`**

```java
package com.portfoliotracker.analytics.dto;

import java.util.List;

public record PerformanceResponse(List<PerformancePoint> points) {
}
```

- [ ] **Step 5: Write `PerformanceCalculator.java`**

```java
package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.PerformancePoint;
import com.portfoliotracker.analytics.dto.PerformanceResponse;
import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.holding.HoldingCalculator;
import com.portfoliotracker.pricehistory.PriceHistory;
import com.portfoliotracker.transaction.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PerformanceCalculator {

    private static final int PERCENT_SCALE = 4;

    public static PerformanceResponse calculate(List<Transaction> transactions,
                                                  Map<String, List<PriceHistory>> historyBySymbol,
                                                  List<PriceHistory> spyHistory) {
        List<LocalDate> tradingDays = spyHistory.stream().map(PriceHistory::getPriceDate).sorted().toList();

        BigDecimal baselinePortfolioValue = null;
        BigDecimal baselineSpyClose = null;
        List<PerformancePoint> points = new ArrayList<>();

        for (LocalDate date : tradingDays) {
            List<Transaction> asOfDate = transactions.stream()
                    .filter(tx -> !tx.getExecutedAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(date))
                    .toList();
            BigDecimal portfolioValue = portfolioValueAsOf(asOfDate, historyBySymbol, date);

            if (baselinePortfolioValue == null) {
                if (portfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                baselinePortfolioValue = portfolioValue;
                baselineSpyClose = closeOnOrBefore(spyHistory, date);
            }

            BigDecimal spyClose = closeOnOrBefore(spyHistory, date);
            BigDecimal portfolioChangePercent = percentChange(baselinePortfolioValue, portfolioValue);
            BigDecimal spyChangePercent = percentChange(baselineSpyClose, spyClose);

            points.add(new PerformancePoint(date, portfolioChangePercent, spyChangePercent));
        }

        return new PerformanceResponse(points);
    }

    private static BigDecimal portfolioValueAsOf(List<Transaction> asOfDate,
                                                  Map<String, List<PriceHistory>> historyBySymbol,
                                                  LocalDate date) {
        List<Holding> holdings = HoldingCalculator.calculate(asOfDate);
        BigDecimal total = BigDecimal.ZERO;
        for (Holding holding : holdings) {
            List<PriceHistory> history = historyBySymbol.getOrDefault(holding.symbol(), List.of());
            BigDecimal close = closeOnOrBefore(history, date);
            if (close == null) {
                continue;
            }
            total = total.add(close.multiply(holding.quantity()));
        }
        return total;
    }

    private static BigDecimal closeOnOrBefore(List<PriceHistory> history, LocalDate date) {
        BigDecimal result = null;
        LocalDate resultDate = null;
        for (PriceHistory row : history) {
            if (!row.getPriceDate().isAfter(date) && (resultDate == null || row.getPriceDate().isAfter(resultDate))) {
                result = row.getClose();
                resultDate = row.getPriceDate();
            }
        }
        return result;
    }

    private static BigDecimal percentChange(BigDecimal baseline, BigDecimal current) {
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0 || current == null) {
            return BigDecimal.ZERO;
        }
        return current.subtract(baseline).multiply(BigDecimal.valueOf(100)).divide(baseline, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PerformanceCalculatorTest`
Expected: PASS (4 tests)

- [ ] **Step 7: Add failing integration test to `AnalyticsControllerIntegrationTest.java`**

Add this field alongside the existing autowired fields:

```java
    @Autowired
    com.portfoliotracker.pricehistory.PriceHistoryRepository priceHistoryRepository;
```

Add this test method:

```java
    @Test
    void performanceReturnsPercentChangeSeriesRelativeToSpy() {
        ResponseEntity<PortfolioResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios", new CreatePortfolioRequest("Performance Test"), PortfolioResponse.class);
        Long portfolioId = createResp.getBody().id();

        java.time.LocalDate day1 = java.time.LocalDate.of(2026, 1, 1);
        java.time.LocalDate day2 = java.time.LocalDate.of(2026, 1, 2);

        restTemplate.postForEntity(
                "/api/portfolios/" + portfolioId + "/transactions",
                new com.portfoliotracker.transaction.dto.CreateTransactionRequest(
                        "AAPL", com.portfoliotracker.transaction.TransactionType.BUY,
                        new BigDecimal("10"), new BigDecimal("100.00"),
                        day1.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()),
                com.portfoliotracker.transaction.dto.TransactionResponse.class);

        saveHistory("AAPL", day1, "100.00");
        saveHistory("AAPL", day2, "110.00");
        saveHistory("SPY", day1, "500.00");
        saveHistory("SPY", day2, "510.00");

        ResponseEntity<com.portfoliotracker.analytics.dto.PerformanceResponse> resp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolioId + "/analytics/performance",
                com.portfoliotracker.analytics.dto.PerformanceResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().points()).hasSize(2);
        assertThat(resp.getBody().points().get(1).portfolioChangePercent()).isEqualByComparingTo("10.0000");
        assertThat(resp.getBody().points().get(1).spyChangePercent()).isEqualByComparingTo("2.0000");
    }

    private void saveHistory(String symbol, java.time.LocalDate date, String close) {
        com.portfoliotracker.pricehistory.PriceHistory row = new com.portfoliotracker.pricehistory.PriceHistory();
        row.setSymbol(symbol);
        row.setPriceDate(date);
        row.setClose(new BigDecimal(close));
        priceHistoryRepository.save(row);
    }
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=AnalyticsControllerIntegrationTest`
Expected: FAIL — `/api/portfolios/{id}/analytics/performance` doesn't exist yet, `PerformanceResponse` not wired.

- [ ] **Step 9: Add the performance endpoint to `AnalyticsController.java`**

Add the new dependency to the constructor:

```java
    private final com.portfoliotracker.pricehistory.PriceHistoryService priceHistoryService;
```

Update the constructor signature and assignment accordingly (add `com.portfoliotracker.pricehistory.PriceHistoryService priceHistoryService` as the last parameter, assign it the same way as the other fields).

Add the endpoint method:

```java
    @GetMapping("/performance")
    public com.portfoliotracker.analytics.dto.PerformanceResponse performance(@PathVariable Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
        List<Transaction> transactions = transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        Map<String, List<com.portfoliotracker.pricehistory.PriceHistory>> historyBySymbol = transactions.stream()
                .map(Transaction::getSymbol)
                .distinct()
                .collect(java.util.stream.Collectors.toMap(symbol -> symbol, priceHistoryService::getHistory));
        List<com.portfoliotracker.pricehistory.PriceHistory> spyHistory = priceHistoryService.getHistory("SPY");
        return PerformanceCalculator.calculate(transactions, historyBySymbol, spyHistory);
    }
```

Add the missing import:

```java
import java.util.Map;
```

- [ ] **Step 10: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=AnalyticsControllerIntegrationTest`
Expected: PASS (3 tests: the existing 2 allocation tests plus the new performance test)

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/analytics backend/src/test/java/com/portfoliotracker/analytics
git commit -m "Add performance-vs-SPY calculator and GET /api/portfolios/{id}/analytics/performance"
```

---

### Task 10: Frontend performance line chart

**Suggested model:** Sonnet 5 (Chart.js line chart with dual series, per spec's "React hooks/state" model tier)

**Files:**
- Modify: `frontend/src/types/portfolio.ts`
- Modify: `frontend/src/api/portfolios.ts`
- Create: `frontend/src/components/PerformanceChart.tsx`
- Modify: `frontend/src/pages/Dashboard.tsx`

**Interfaces:**
- Consumes: `GET /api/portfolios/{id}/analytics/performance` (Task 9).
- Produces: `PerformanceChart` component mounted in `Dashboard`.

- [ ] **Step 1: Add performance types to `frontend/src/types/portfolio.ts`**

Add at the end of the file:

```ts
export interface PerformancePoint {
  date: string;
  portfolioChangePercent: number;
  spyChangePercent: number;
}

export interface PerformanceResponse {
  points: PerformancePoint[];
}
```

- [ ] **Step 2: Add API client function to `frontend/src/api/portfolios.ts`**

Update the type import to include `PerformanceResponse`:

```ts
import type { Portfolio, Holding, Transaction, CreateTransactionInput, AllocationResponse, PerformanceResponse } from '../types/portfolio';
```

```ts
export const getPerformance = (portfolioId: number) =>
  apiFetch<PerformanceResponse>(`/api/portfolios/${portfolioId}/analytics/performance`);
```

- [ ] **Step 3: Write `frontend/src/components/PerformanceChart.tsx`**

```tsx
import { Line } from 'react-chartjs-2';
import {
  Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend,
} from 'chart.js';
import type { PerformancePoint } from '../types/portfolio';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend);

export function PerformanceChart({ points }: { points: PerformancePoint[] }) {
  if (points.length === 0) {
    return null;
  }

  const data = {
    labels: points.map((p) => p.date),
    datasets: [
      {
        label: 'Portfolio',
        data: points.map((p) => p.portfolioChangePercent),
        borderColor: '#2563eb',
        backgroundColor: '#2563eb',
      },
      {
        label: 'SPY',
        data: points.map((p) => p.spyChangePercent),
        borderColor: '#6b7280',
        backgroundColor: '#6b7280',
      },
    ],
  };

  return (
    <div className="mb-6 max-w-2xl">
      <h2 className="text-lg font-semibold mb-2">Performance vs SPY</h2>
      <Line data={data} options={{ scales: { y: { ticks: { callback: (v) => `${v}%` } } } }} />
    </div>
  );
}
```

- [ ] **Step 4: Wire into `frontend/src/pages/Dashboard.tsx`**

Add the import:

```tsx
import { PerformanceChart } from '../components/PerformanceChart';
import { getPerformance } from '../api/portfolios';
import type { PerformancePoint } from '../types/portfolio';
```

Add state:

```tsx
const [performance, setPerformance] = useState<PerformancePoint[]>([]);
```

In the same `useEffect` that loads allocation, add:

```tsx
getPerformance(selectedId).then((r) => setPerformance(r.points)).catch((e) => setError(e.message));
```

Add `<PerformanceChart points={performance} />` immediately after `<AllocationChart sectors={sectors} />`.

- [ ] **Step 5: Verify frontend still builds and existing tests pass**

Run: `cd frontend && npx tsc -b && npx vitest run`
Expected: PASS (existing 6 tests unaffected — thin presentational wrapper, same convention as Task 5)

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/portfolio.ts frontend/src/api/portfolios.ts frontend/src/components/PerformanceChart.tsx frontend/src/pages/Dashboard.tsx
git commit -m "Add performance-vs-SPY line chart to dashboard"
```

---

### Task 11: P&L calculator (realized + unrealized)

**Suggested model:** Opus 5 (correctness-critical realized-gain math over sequential BUY/SELL history, per spec's "High-stakes/tricky" model tier)

**Design note — no duplicated replay logic:** Realized P&L requires replaying transactions in order maintaining a running average cost basis per symbol -- the exact same accounting `HoldingCalculator.calculate` (M1) already performs to compute final holdings. Rather than reimplementing that replay loop a second time in a new `PnlCalculator` (which would leave two independent copies of the same BUY/SELL average-cost algorithm to keep in sync), this task extends `HoldingCalculator` itself with a second entry point, `calculateRealizedPnl`, that shares one private replay method with the existing `calculate`. `PnlCalculator` then only orchestrates: delegate the realized leg to `HoldingCalculator.calculateRealizedPnl`, compute the unrealized leg from `HoldingCalculator.calculate`'s output (same per-holding math `HoldingResponse.from` already does in M2, summed here instead of returned per-row).

**Files:**
- Modify: `backend/src/main/java/com/portfoliotracker/holding/HoldingCalculator.java`
- Modify: `backend/src/test/java/com/portfoliotracker/holding/HoldingCalculatorTest.java`
- Create: `backend/src/main/java/com/portfoliotracker/analytics/PnlCalculator.java`
- Create: `backend/src/main/java/com/portfoliotracker/analytics/dto/PnlResponse.java`
- Test: `backend/src/test/java/com/portfoliotracker/analytics/PnlCalculatorTest.java`

**Interfaces:**
- Consumes: `Transaction` entities (M1, ordered by `executedAt` ascending — same ordering `TransactionRepository.findByPortfolioIdOrderByExecutedAtAsc` already guarantees).
- Produces: `HoldingCalculator.calculateRealizedPnl(List<Transaction>): BigDecimal` (new, alongside the existing `calculate`). `PnlCalculator.calculate(List<Transaction> transactions, java.util.function.Function<String, java.util.Optional<BigDecimal>> priceLookup): PnlResponse` where `PnlResponse(BigDecimal realizedPnl, BigDecimal unrealizedPnl)`. `GET /api/portfolios/{id}/analytics/pnl` → `PnlResponse` (Task 12).

- [ ] **Step 1: Write failing unit tests for `HoldingCalculator.calculateRealizedPnl`**

Add these test methods inside the existing `HoldingCalculatorTest` class (do not modify the 5 existing test methods):

```java
    @Test
    void calculateRealizedPnlAccruesOnSellAtAverageCostBasis() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.parse("2026-01-01T00:00:00Z")),
                Transaction.of("AAPL", TransactionType.SELL, new BigDecimal("4"), new BigDecimal("150.00"), Instant.parse("2026-02-01T00:00:00Z")));

        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(txs);

        // avg cost 100.00, sold 4 @ 150.00 -> realized gain = (150-100)*4 = 200
        assertThat(realizedPnl).isEqualByComparingTo("200");
    }

    @Test
    void calculateRealizedPnlAccumulatesAcrossMultipleSellsAndSymbols() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.parse("2026-01-01T00:00:00Z")),
                Transaction.of("AAPL", TransactionType.SELL, new BigDecimal("5"), new BigDecimal("120.00"), Instant.parse("2026-01-02T00:00:00Z")),
                Transaction.of("MSFT", TransactionType.BUY, new BigDecimal("4"), new BigDecimal("300.00"), Instant.parse("2026-01-03T00:00:00Z")),
                Transaction.of("MSFT", TransactionType.SELL, new BigDecimal("2"), new BigDecimal("250.00"), Instant.parse("2026-01-04T00:00:00Z")));

        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(txs);

        // AAPL: (120-100)*5 = 100. MSFT: (250-300)*2 = -100. Total = 0
        assertThat(realizedPnl).isEqualByComparingTo("0");
    }

    @Test
    void calculateRealizedPnlIsZeroWhenNoSellsHaveOccurred() {
        List<Transaction> txs = List.of(
                Transaction.of("AAPL", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100.00"), Instant.now()));

        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(txs);

        assertThat(realizedPnl).isEqualByComparingTo("0");
    }

    @Test
    void calculateRealizedPnlIsZeroForEmptyTransactionList() {
        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(List.of());

        assertThat(realizedPnl).isEqualByComparingTo("0");
    }
```

- [ ] **Step 2: Run tests to verify the new ones fail**

Run: `cd backend && mvn test -Dtest=HoldingCalculatorTest`
Expected: FAIL — the 4 new tests fail with a compile error (`calculateRealizedPnl` doesn't exist yet); the 5 pre-existing tests still pass once the compile error is fixed enough to run (so on the first attempt the whole file fails to compile — that's the expected RED for this step).

- [ ] **Step 3: Rewrite `HoldingCalculator.java`** — extract the shared replay loop, add `calculateRealizedPnl`

```java
package com.portfoliotracker.holding;

import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HoldingCalculator {

    private static final int INTERNAL_SCALE = 10;
    private static final int RESULT_SCALE = 4;

    public static List<Holding> calculate(List<Transaction> transactions) {
        ReplayResult result = replay(transactions);

        List<Holding> holdings = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : result.qtyBySymbol().entrySet()) {
            String symbol = entry.getKey();
            BigDecimal qty = entry.getValue();
            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal cost = result.costBySymbol().get(symbol);
                BigDecimal avgCostBasis = cost.divide(qty, RESULT_SCALE, RoundingMode.HALF_UP);
                holdings.add(new Holding(symbol, qty, avgCostBasis));
            }
        }
        return holdings;
    }

    public static BigDecimal calculateRealizedPnl(List<Transaction> transactions) {
        return replay(transactions).realizedPnl();
    }

    private static ReplayResult replay(List<Transaction> transactions) {
        Map<String, BigDecimal> qtyBySymbol = new LinkedHashMap<>();
        Map<String, BigDecimal> costBySymbol = new LinkedHashMap<>();
        BigDecimal realizedPnl = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            String symbol = tx.getSymbol();
            BigDecimal qty = qtyBySymbol.getOrDefault(symbol, BigDecimal.ZERO);
            BigDecimal cost = costBySymbol.getOrDefault(symbol, BigDecimal.ZERO);

            if (tx.getType() == TransactionType.BUY) {
                cost = cost.add(tx.getQuantity().multiply(tx.getPrice()));
                qty = qty.add(tx.getQuantity());
            } else {
                if (qty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal avgCost = cost.divide(qty, INTERNAL_SCALE, RoundingMode.HALF_UP);
                    realizedPnl = realizedPnl.add(tx.getPrice().subtract(avgCost).multiply(tx.getQuantity()));
                    cost = cost.subtract(avgCost.multiply(tx.getQuantity()));
                }
                qty = qty.subtract(tx.getQuantity());
            }

            qtyBySymbol.put(symbol, qty);
            costBySymbol.put(symbol, cost);
        }

        return new ReplayResult(qtyBySymbol, costBySymbol, realizedPnl);
    }

    private record ReplayResult(Map<String, BigDecimal> qtyBySymbol, Map<String, BigDecimal> costBySymbol, BigDecimal realizedPnl) {
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=HoldingCalculatorTest`
Expected: PASS (9 tests: the 5 pre-existing `calculate` tests plus the 4 new `calculateRealizedPnl` tests — this proves the extraction preserved `calculate`'s existing behavior exactly)

- [ ] **Step 5: Commit the `HoldingCalculator` extension**

```bash
git add backend/src/main/java/com/portfoliotracker/holding/HoldingCalculator.java backend/src/test/java/com/portfoliotracker/holding/HoldingCalculatorTest.java
git commit -m "Extend HoldingCalculator with calculateRealizedPnl, sharing the existing replay loop"
```

- [ ] **Step 6: Write failing unit tests for `PnlCalculator`**

```java
package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.PnlResponse;
import com.portfoliotracker.transaction.Transaction;
import com.portfoliotracker.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PnlCalculatorTest {

    private Transaction tx(String symbol, TransactionType type, String qty, String price) {
        return Transaction.of(symbol, type, new BigDecimal(qty), new BigDecimal(price), Instant.now());
    }

    @Test
    void realizedLegDelegatesToHoldingCalculator() {
        List<Transaction> transactions = List.of(
                tx("AAPL", TransactionType.BUY, "10", "100.00"),
                tx("AAPL", TransactionType.SELL, "4", "150.00"));

        PnlResponse response = PnlCalculator.calculate(transactions, symbol -> Optional.empty());

        // avg cost 100.00, sold 4 @ 150.00 -> realized gain = (150-100)*4 = 200
        assertThat(response.realizedPnl()).isEqualByComparingTo("200.0000");
    }

    @Test
    void unrealizedPnlSumsAcrossHoldingsWithCachedPrices() {
        List<Transaction> transactions = List.of(
                tx("AAPL", TransactionType.BUY, "10", "100.00"),
                tx("MSFT", TransactionType.BUY, "5", "300.00"));
        Map<String, BigDecimal> prices = Map.of("AAPL", new BigDecimal("150.00"), "MSFT", new BigDecimal("280.00"));

        PnlResponse response = PnlCalculator.calculate(transactions, symbol -> Optional.ofNullable(prices.get(symbol)));

        // AAPL: (150-100)*10 = 500. MSFT: (280-300)*5 = -100. Total = 400
        assertThat(response.unrealizedPnl()).isEqualByComparingTo("400.0000");
    }

    @Test
    void unrealizedPnlExcludesHoldingsWithNoCachedPrice() {
        List<Transaction> transactions = List.of(tx("ZZZZ", TransactionType.BUY, "5", "10.00"));

        PnlResponse response = PnlCalculator.calculate(transactions, symbol -> Optional.empty());

        assertThat(response.unrealizedPnl()).isEqualByComparingTo("0.0000");
    }

    @Test
    void returnsZeroForBothWhenNoTransactionsExist() {
        PnlResponse response = PnlCalculator.calculate(List.of(), symbol -> Optional.empty());

        assertThat(response.realizedPnl()).isEqualByComparingTo("0");
        assertThat(response.unrealizedPnl()).isEqualByComparingTo("0");
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PnlCalculatorTest`
Expected: FAIL — compile error, `PnlCalculator`/`PnlResponse` don't exist yet.

- [ ] **Step 8: Write `dto/PnlResponse.java`**

```java
package com.portfoliotracker.analytics.dto;

import java.math.BigDecimal;

public record PnlResponse(BigDecimal realizedPnl, BigDecimal unrealizedPnl) {
}
```

- [ ] **Step 9: Write `PnlCalculator.java`**

```java
package com.portfoliotracker.analytics;

import com.portfoliotracker.analytics.dto.PnlResponse;
import com.portfoliotracker.holding.Holding;
import com.portfoliotracker.holding.HoldingCalculator;
import com.portfoliotracker.transaction.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class PnlCalculator {

    private static final int RESULT_SCALE = 4;

    public static PnlResponse calculate(List<Transaction> transactions, Function<String, Optional<BigDecimal>> priceLookup) {
        BigDecimal realizedPnl = HoldingCalculator.calculateRealizedPnl(transactions);
        BigDecimal unrealizedPnl = calculateUnrealizedPnl(transactions, priceLookup);
        return new PnlResponse(realizedPnl.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                unrealizedPnl.setScale(RESULT_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal calculateUnrealizedPnl(List<Transaction> transactions, Function<String, Optional<BigDecimal>> priceLookup) {
        List<Holding> holdings = HoldingCalculator.calculate(transactions);
        BigDecimal unrealized = BigDecimal.ZERO;
        for (Holding holding : holdings) {
            Optional<BigDecimal> price = priceLookup.apply(holding.symbol());
            if (price.isEmpty()) {
                continue;
            }
            unrealized = unrealized.add(price.get().subtract(holding.avgCostBasis()).multiply(holding.quantity()));
        }
        return unrealized;
    }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=PnlCalculatorTest`
Expected: PASS (4 tests)

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/analytics/PnlCalculator.java backend/src/main/java/com/portfoliotracker/analytics/dto/PnlResponse.java backend/src/test/java/com/portfoliotracker/analytics/PnlCalculatorTest.java
git commit -m "Add PnlCalculator: orchestrates realized (via HoldingCalculator) and unrealized gain"
```

---

### Task 12: P&L endpoint

**Suggested model:** Haiku 4.5 (wiring only — logic already tested in Task 11)

**Files:**
- Modify: `backend/src/main/java/com/portfoliotracker/analytics/AnalyticsController.java`
- Test: `backend/src/test/java/com/portfoliotracker/analytics/AnalyticsControllerIntegrationTest.java` (extend existing file)

**Interfaces:**
- Consumes: `PnlCalculator.calculate(List<Transaction>, Function<String, Optional<BigDecimal>>): PnlResponse` (Task 11), `PriceService.getLatest(String): Optional<PriceSnapshot>` (M2, already injected into `AnalyticsController`).
- Produces: `GET /api/portfolios/{id}/analytics/pnl` → `PnlResponse`.

- [ ] **Step 1: Add failing integration test to `AnalyticsControllerIntegrationTest.java`**

```java
    @Test
    void pnlReturnsRealizedAndUnrealizedGainSummary() {
        ResponseEntity<PortfolioResponse> createResp = restTemplate.postForEntity(
                "/api/portfolios", new CreatePortfolioRequest("Pnl Test"), PortfolioResponse.class);
        Long portfolioId = createResp.getBody().id();

        restTemplate.postForEntity(
                "/api/portfolios/" + portfolioId + "/transactions",
                new com.portfoliotracker.transaction.dto.CreateTransactionRequest(
                        "AAPL", com.portfoliotracker.transaction.TransactionType.BUY,
                        new BigDecimal("10"), new BigDecimal("100.00"), Instant.now()),
                com.portfoliotracker.transaction.dto.TransactionResponse.class);
        restTemplate.postForEntity(
                "/api/portfolios/" + portfolioId + "/transactions",
                new com.portfoliotracker.transaction.dto.CreateTransactionRequest(
                        "AAPL", com.portfoliotracker.transaction.TransactionType.SELL,
                        new BigDecimal("4"), new BigDecimal("150.00"), Instant.now()),
                com.portfoliotracker.transaction.dto.TransactionResponse.class);

        com.portfoliotracker.price.PriceSnapshot snapshot = new com.portfoliotracker.price.PriceSnapshot();
        snapshot.setSymbol("AAPL");
        snapshot.setPrice(new BigDecimal("120.00"));
        snapshot.setFetchedAt(Instant.now());
        priceSnapshotRepository.save(snapshot);

        ResponseEntity<com.portfoliotracker.analytics.dto.PnlResponse> resp = restTemplate.getForEntity(
                "/api/portfolios/" + portfolioId + "/analytics/pnl",
                com.portfoliotracker.analytics.dto.PnlResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // realized: (150-100)*4 = 200. remaining 6 @ avg cost 100, unrealized: (120-100)*6 = 120
        assertThat(resp.getBody().realizedPnl()).isEqualByComparingTo("200.0000");
        assertThat(resp.getBody().unrealizedPnl()).isEqualByComparingTo("120.0000");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=AnalyticsControllerIntegrationTest`
Expected: FAIL — `/api/portfolios/{id}/analytics/pnl` doesn't exist yet.

- [ ] **Step 3: Add the pnl endpoint to `AnalyticsController.java`**

```java
    @GetMapping("/pnl")
    public com.portfoliotracker.analytics.dto.PnlResponse pnl(@PathVariable Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
        List<Transaction> transactions = transactionRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        return PnlCalculator.calculate(transactions,
                symbol -> priceService.getLatest(symbol).map(com.portfoliotracker.price.PriceSnapshot::getPrice));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=AnalyticsControllerIntegrationTest`
Expected: PASS (4 tests: 2 allocation + 1 performance + this new pnl test)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/portfoliotracker/analytics/AnalyticsController.java backend/src/test/java/com/portfoliotracker/analytics/AnalyticsControllerIntegrationTest.java
git commit -m "Add GET /api/portfolios/{id}/analytics/pnl endpoint"
```

---

### Task 13: Frontend P&L breakdown display

**Suggested model:** Haiku 4.5 (mechanical — mirrors PortfolioSummary's presentational pattern)

**Files:**
- Modify: `frontend/src/types/portfolio.ts`
- Modify: `frontend/src/api/portfolios.ts`
- Create: `frontend/src/components/PnlBreakdown.tsx`
- Modify: `frontend/src/pages/Dashboard.tsx`

**Interfaces:**
- Consumes: `GET /api/portfolios/{id}/analytics/pnl` (Task 12).
- Produces: `PnlBreakdown` component mounted in `Dashboard`.

- [ ] **Step 1: Add pnl types to `frontend/src/types/portfolio.ts`**

Add at the end of the file:

```ts
export interface PnlResponse {
  realizedPnl: number;
  unrealizedPnl: number;
}
```

- [ ] **Step 2: Add API client function to `frontend/src/api/portfolios.ts`**

Update the type import to include `PnlResponse`:

```ts
import type { Portfolio, Holding, Transaction, CreateTransactionInput, AllocationResponse, PerformanceResponse, PnlResponse } from '../types/portfolio';
```

```ts
export const getPnl = (portfolioId: number) =>
  apiFetch<PnlResponse>(`/api/portfolios/${portfolioId}/analytics/pnl`);
```

- [ ] **Step 3: Write `frontend/src/components/PnlBreakdown.tsx`**

```tsx
import type { PnlResponse } from '../types/portfolio';

function formatPnl(value: number): string {
  return `${value >= 0 ? '+' : ''}$${value.toFixed(2)}`;
}

export function PnlBreakdown({ pnl }: { pnl: PnlResponse | null }) {
  if (pnl === null) {
    return null;
  }

  const totalPnl = pnl.realizedPnl + pnl.unrealizedPnl;

  return (
    <div className="flex gap-6 mb-6">
      <div>
        <div className="text-xs text-gray-500">Realized P&L</div>
        <div className={`text-lg font-semibold ${pnl.realizedPnl < 0 ? 'text-red-600' : 'text-green-600'}`}>
          {formatPnl(pnl.realizedPnl)}
        </div>
      </div>
      <div>
        <div className="text-xs text-gray-500">Unrealized P&L</div>
        <div className={`text-lg font-semibold ${pnl.unrealizedPnl < 0 ? 'text-red-600' : 'text-green-600'}`}>
          {formatPnl(pnl.unrealizedPnl)}
        </div>
      </div>
      <div>
        <div className="text-xs text-gray-500">Total P&L</div>
        <div className={`text-lg font-semibold ${totalPnl < 0 ? 'text-red-600' : 'text-green-600'}`}>
          {formatPnl(totalPnl)}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Wire into `frontend/src/pages/Dashboard.tsx`**

Add the import:

```tsx
import { PnlBreakdown } from '../components/PnlBreakdown';
import { getPnl } from '../api/portfolios';
import type { PnlResponse } from '../types/portfolio';
```

Add state:

```tsx
const [pnl, setPnl] = useState<PnlResponse | null>(null);
```

In the same `useEffect` that loads allocation/performance, add:

```tsx
getPnl(selectedId).then(setPnl).catch((e) => setError(e.message));
```

Add `<PnlBreakdown pnl={pnl} />` immediately after `<PerformanceChart points={performance} />` and before `<HoldingsTable holdings={holdings} />`.

- [ ] **Step 5: Verify frontend still builds and existing tests pass**

Run: `cd frontend && npx tsc -b && npx vitest run`
Expected: PASS (existing 6 tests unaffected)

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/portfolio.ts frontend/src/api/portfolios.ts frontend/src/components/PnlBreakdown.tsx frontend/src/pages/Dashboard.tsx
git commit -m "Add realized/unrealized/total P&L breakdown to dashboard"
```

---

### Task 14: Docker-compose smoke test

**Suggested model:** Sonnet 5 (cross-service debugging likely, per spec's model table)

**Files:**
- No new source files — verification task.

**Interfaces:**
- Consumes: everything from Tasks 1-13.
- Produces: verified running stack with sector allocation, performance-vs-SPY, and P&L all populated from real Finnhub data (manual smoke test, no automated test — matches M2 Task 9's precedent).

- [ ] **Step 1: Bring up full stack with a real Finnhub key**

Run: `FINNHUB_API_KEY=<your-real-key> docker compose up --build`
Expected: three containers start, backend logs show `Started PortfolioTrackerApplication`.

- [ ] **Step 2: Manual smoke test**

1. Open `http://localhost:5173`, create a portfolio, add a BUY transaction for a real symbol (e.g. AAPL, 10, 150.00)
2. Wait up to `finnhub.daily-sync-interval-ms` for the two new schedulers to run (in practice, for immediate manual verification, temporarily lower `daily-sync-interval-ms` in `application.yml` to e.g. `60000` before this step, then revert afterward — the 24h default is correct for production but impractical to wait out manually)
3. Refresh the dashboard — sector allocation pie chart shows AAPL under "Technology" (or whatever Finnhub's `finnhubIndustry` returns), performance-vs-SPY line chart shows two series starting at 0%, P&L breakdown shows realized $0.00 and unrealized matching the cached price
4. Call `curl http://localhost:8080/api/portfolios/{id}/analytics/allocation`, `.../performance`, and `.../pnl` directly — expect 200 with populated data on all three
5. Check browser devtools Network/Console tabs — no failed requests, no console errors

Expected: all steps work cleanly.

- [ ] **Step 3: Revert any temporary interval changes made for manual testing**

If `daily-sync-interval-ms` was lowered in Step 2, restore it to `86400000` before committing.

- [ ] **Step 4: Commit (only if anything changed, e.g. reverting a temp config tweak)**

```bash
git add backend/src/main/resources/application.yml
git commit -m "Verify M3 analytics end-to-end against live Finnhub data"
```

---

## M3 Completion Checklist

- [ ] All 14 tasks committed
- [ ] `docker compose up --build` runs postgres + backend + frontend cleanly
- [ ] Manual smoke test (Task 14, Step 2) passes with a real Finnhub key
- [ ] `mvn test` passes (all Testcontainers integration tests + Mockito unit tests, M1 + M2 + M3)
- [ ] `npx vitest run` passes (all prior tests, no new frontend unit tests added in M3 beyond what M2 already had — new components are thin presentational wrappers, consistent with `PortfolioSummary`'s precedent)
- [ ] Ready for `/clear` — next session resumes at M4 (Google OAuth2 + JWT + multi-user scoping) per spec
