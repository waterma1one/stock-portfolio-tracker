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
