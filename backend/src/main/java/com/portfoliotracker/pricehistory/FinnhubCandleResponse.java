package com.portfoliotracker.pricehistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubCandleResponse(List<BigDecimal> c, List<Long> t, String s) {
}
