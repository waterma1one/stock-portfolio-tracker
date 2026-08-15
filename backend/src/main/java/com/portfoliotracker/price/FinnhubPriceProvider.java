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
