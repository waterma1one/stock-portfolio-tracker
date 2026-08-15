package com.portfoliotracker.price;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubQuoteResponse(BigDecimal c) {
}
