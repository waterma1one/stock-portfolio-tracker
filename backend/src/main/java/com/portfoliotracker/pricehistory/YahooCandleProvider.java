package com.portfoliotracker.pricehistory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily closes from Yahoo Finance's chart endpoint. Finnhub's /stock/candle is not available on
 * free API keys, and this endpoint needs no key. It is unofficial, so it can change without notice.
 */
@Component
public class YahooCandleProvider implements CandleProvider {

    private final RestClient restClient;

    public YahooCandleProvider(RestClient.Builder restClientBuilder,
                               @Value("${yahoo.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeader("User-Agent", "Mozilla/5.0").build();
    }

    @Override
    public List<DailyClose> getDailyCloses(String symbol, LocalDate from, LocalDate to) {
        try {
            long fromEpoch = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            // period2 is exclusive, so add a day to include `to`
            long toEpoch = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

            YahooChartResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v8/finance/chart/{symbol}")
                            .queryParam("period1", fromEpoch)
                            .queryParam("period2", toEpoch)
                            .queryParam("interval", "1d")
                            .build(symbol))
                    .retrieve()
                    .body(YahooChartResponse.class);

            if (response == null || response.chart() == null || response.chart().result() == null
                    || response.chart().result().isEmpty()) {
                return List.of();
            }
            YahooChartResponse.Result result = response.chart().result().get(0);
            if (result.timestamp() == null || result.indicators() == null
                    || result.indicators().quote() == null || result.indicators().quote().isEmpty()) {
                return List.of();
            }
            List<BigDecimal> closes = result.indicators().quote().get(0).close();
            if (closes == null) {
                return List.of();
            }

            List<DailyClose> daily = new ArrayList<>();
            for (int i = 0; i < closes.size() && i < result.timestamp().size(); i++) {
                if (closes.get(i) == null) {
                    continue; // Yahoo returns null for days without a close
                }
                LocalDate date = Instant.ofEpochSecond(result.timestamp().get(i)).atZone(ZoneOffset.UTC).toLocalDate();
                daily.add(new DailyClose(date, closes.get(i)));
            }
            return daily;
        } catch (Exception e) {
            return List.of();
        }
    }
}
