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
