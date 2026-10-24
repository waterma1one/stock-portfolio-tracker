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

class YahooCandleProviderTest {

    private static final String BASE = "https://query1.finance.yahoo.com";

    @Test
    void parsesDailyClosesAndSkipsNullCloses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 2026-01-01T00:00:00Z = 1767225600; period2 is the day after `to`: 2026-01-04 = 1767484800
        server.expect(requestTo(BASE + "/v8/finance/chart/AAPL?period1=1767225600&period2=1767484800&interval=1d"))
                .andRespond(withSuccess(
                        "{\"chart\":{\"result\":[{\"timestamp\":[1767225600,1767312000,1767398400],"
                                + "\"indicators\":{\"quote\":[{\"close\":[150.00,null,152.25]}]}}],\"error\":null}}",
                        MediaType.APPLICATION_JSON));

        YahooCandleProvider provider = new YahooCandleProvider(builder, BASE);

        List<DailyClose> closes = provider.getDailyCloses("AAPL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

        assertThat(closes).hasSize(2);
        assertThat(closes.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(closes.get(0).close()).isEqualByComparingTo("150.00");
        assertThat(closes.get(1).date()).isEqualTo(LocalDate.of(2026, 1, 3));
        assertThat(closes.get(1).close()).isEqualByComparingTo("152.25");
    }

    @Test
    void returnsEmptyListWhenSymbolHasNoResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/v8/finance/chart/BADSYM?period1=1767225600&period2=1767398400&interval=1d"))
                .andRespond(withSuccess("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\"}}}",
                        MediaType.APPLICATION_JSON));

        YahooCandleProvider provider = new YahooCandleProvider(builder, BASE);

        assertThat(provider.getDailyCloses("BADSYM", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))).isEmpty();
    }

    @Test
    void returnsEmptyListWhenApiCallFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/v8/finance/chart/BADSYM?period1=1767225600&period2=1767398400&interval=1d"))
                .andRespond(withServerError());

        YahooCandleProvider provider = new YahooCandleProvider(builder, BASE);

        assertThat(provider.getDailyCloses("BADSYM", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))).isEmpty();
    }
}
