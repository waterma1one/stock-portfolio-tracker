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
