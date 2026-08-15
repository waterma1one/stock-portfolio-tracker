package com.portfoliotracker.pricehistory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
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
                LocalDate date = Instant.ofEpochSecond(response.t().get(i)).atZone(ZoneOffset.UTC).toLocalDate();
                closes.add(new DailyClose(date, response.c().get(i)));
            }
            return closes;
        } catch (Exception e) {
            return List.of();
        }
    }
}
