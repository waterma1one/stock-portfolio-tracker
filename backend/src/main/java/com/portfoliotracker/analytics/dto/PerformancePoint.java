package com.portfoliotracker.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PerformancePoint(LocalDate date, BigDecimal portfolioChangePercent, BigDecimal spyChangePercent) {
}
