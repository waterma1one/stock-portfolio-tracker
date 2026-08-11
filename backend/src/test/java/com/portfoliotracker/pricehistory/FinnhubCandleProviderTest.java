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
