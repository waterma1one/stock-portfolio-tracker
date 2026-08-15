package com.portfoliotracker.symbolprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubProfileResponse(String name, @JsonProperty("finnhubIndustry") String finnhubIndustry) {
}
